# Comparaison des approches de délimitation de cluster — MobiCloud

> **Contexte :** MobiCloud est une application Android de stockage P2P.
> Les téléphones s'organisent en **clusters** coordonnés par un Super-Pair élu.
> La question est : **comment décider quels téléphones appartiennent au même cluster ?**

---

## Approche 1 — XOR prefix + split/merge (inspiré BitTorrent/Kademlia)

### Principe

Chaque téléphone a un identifiant unique (NodeID) généré à l'installation.
Le cluster est déterminé par les **premiers bits** de ce NodeID.

```
NodeID d'Alice = 0010...
NodeID de Bob  = 0110...
NodeID de Dave = 1011...

Avec profondeur = 1 bit :
  Alice et Bob → cluster "0"   (premier bit = 0)
  Dave         → cluster "1"   (premier bit = 1)
```

### Split : quand un cluster déborde

Si un cluster dépasse `MAX` membres, on le coupe en deux selon le bit suivant.

```
Cluster "0" = {Alice 0010, Bob 0110, Charlie 0101, Grace 0001}  → trop grand

Après split sur le 2ème bit :
  Cluster "00" = {Alice, Grace}
  Cluster "01" = {Bob, Charlie}
```

### Merge : quand un cluster se vide

Si un cluster tombe sous `MIN` membres, on le fusionne avec son jumeau (même préfixe, dernier bit inversé).

```
Cluster "00" = {Alice}  → trop petit
Cluster "01" = {Bob, Charlie}

Après merge :
  Cluster "0" = {Alice, Bob, Charlie}
```

### Qui fait quoi

| Décision | Qui décide |
|----------|-----------|
| Calculer son cluster_id | Téléphone (deterministe : `nodeId.take(depth)`) |
| Déclencher un split | **Relais Render** (seul à voir tous les membres) |
| Déclencher un merge | **Relais Render** |
| Notifier les téléphones du changement | **Relais Render** (`CLUSTER_REASSIGN`) |
| Élire le Super-Pair dans le cluster | Téléphones (algorithme Bully) |

**Le relais maintient une table globale** `clusterMembership : Map<prefix, Set<nodeId>>` et synchronise cette table entre ses instances via Yjs CRDT.

### Pseudo-code

```javascript
// Côté téléphone (Kotlin)
val myClusterId = nodeId.take(currentDepth)

// Côté relais (Node.js)
if (cluster.size > MAX) splitCluster(prefix)
if (cluster.size < MIN) mergeWithTwin(prefix)
```

### Avantages

- Calcul du cluster instantané côté téléphone (1 ligne de code)
- Taille de cluster mathématiquement bornée (`MIN ≤ taille ≤ MAX`)
- Scalable : fonctionne avec 10 ou 10 millions de nœuds
- Anti-Sybil : le NodeID est coûteux à générer (hashcash)
- Compatible HA : les 2 relais Render synchronisent via Yjs CRDT

### Inconvénients

- **Le relais est l'autorité centrale** : sans lui, pas de split/merge possible
- Pas de cohérence géographique : deux nœuds à 2000km peuvent avoir le même préfixe
- Le relais doit maintenir un état global en RAM (complexité côté serveur)
- Justification métier faible : pourquoi stocker avec un nœud qui a le même préfixe ?

---

## Approche 2 — JOIN explicite + GPS optionnel

### Principe

La frontière du cluster est définie par **deux règles combinées** :
1. Taille maximale de cluster (`MAX_CLUSTER_SIZE`)
2. Distance GPS optionnelle (`MAX_RADIUS_METERS`) — activée seulement si GPS disponible

Un pair ne rejoint un cluster qu'après avoir reçu un **`JOIN_ACCEPT` signé** du Super-Pair.

### Déroulé

```
1. Découverte : UDP Multicast (WiFi local) + Relais HA (4G/inter-réseau)

2. Élection Bully : le nœud avec le meilleur score de fiabilité
   devient Super-Pair et diffuse COORDINATOR

3. Chaque nœud envoie JOIN_REQUEST au Super-Pair :
   {
     nodeId, signature, gpsLocation (optionnel),
     freeStorageBytes, reliabilityScore
   }

4. Super-Pair filtre :
   ┌─ GPS des deux nœuds disponible ?
   │     OUI → distance < MAX_RADIUS ? continuer : JOIN_REDIRECT
   │     NON → ignorer le filtre GPS, continuer
   │
   └─ nb membres < MAX_CLUSTER_SIZE ?
         OUI → JOIN_ACCEPT  (+ memberSnapshot)
         NON → JOIN_REDIRECT
```

### Exemple concret

5 téléphones, MAX_RADIUS = 5km, MAX_CLUSTER_SIZE = 10 :

| Nœud | Réseau | Distance d'Alice | Résultat |
|------|--------|-----------------|---------|
| Carol | 4G | 800m | ✅ JOIN_ACCEPT |
| Bob | WiFi | 10km | ❌ JOIN_REDIRECT |
| Dave | 4G | 1350km (Paris) | ❌ JOIN_REDIRECT |

Bob et Dave, refusés, cherchent d'autres nœuds proches et forment leurs propres clusters.

### Gestion du GPS indisponible

```
GPS null (indoor, cold start, permission refusée)
  → filtre GPS ignoré
  → seule règle active : MAX_CLUSTER_SIZE
  → comportement gracieux, pas de blocage
```

### Qui fait quoi

| Décision | Qui décide |
|----------|-----------|
| Découverte des pairs | Téléphone + Relais (signaling) |
| Élection du Super-Pair | Téléphones (algorithme Bully) |
| Accepter ou refuser un membre | **Super-Pair** (pair élu, pas le relais) |
| Maintenir le registre des membres | Super-Pair (Room DB locale) |
| Notifier un départ/arrivée | Super-Pair (`MEMBER_UPDATE`) |
| Re-élection si Super-Pair tombe | Téléphones du cluster (Bully) |

**Le relais reste léger** : il fait du signaling et du transport de blocs. Il ne connaît pas la composition des clusters.

### Avantages

- **Décentralisé** : le Super-Pair élu gère son cluster, pas le relais
- Cohérence géographique quand GPS disponible
- Relais simple (pas d'état global à maintenir)
- Membership explicite et signé cryptographiquement
- Fonctionne si le relais tombe (les clusters existants survivent)
- GPS gracefully dégradé : ne bloque pas si indisponible

### Inconvénients

- GPS imprécis en intérieur (50–200m d'erreur)
- Ajoute des messages dans le protocole (JOIN_REQUEST, JOIN_ACCEPT, MEMBER_UPDATE)
- MAX_RADIUS est un paramètre arbitraire à justifier
- Super-Pair = point de défaillance transitoire (mitigé par la re-élection)

---

## Tableau comparatif

| Critère | XOR prefix | JOIN explicite + GPS |
|---------|-----------|---------------------|
| Autorité de décision | Relais (centralisé) | Super-Pair élu (pair) |
| Cohérence géographique | ❌ Non garantie | ✅ Oui (si GPS dispo) |
| Relais si tombé | Clusters figés | Clusters survivent |
| Complexité côté relais | Élevée (split/merge, Yjs) | Faible (signaling seul) |
| Complexité côté téléphone | Faible (1 ligne) | Moyenne (protocole JOIN) |
| Taille bornée | ✅ Garanti mathématiquement | ✅ Via MAX_CLUSTER_SIZE |
| Scalabilité | ✅ Très haute | ✅ Bonne |
| Principe décentralisation | ⚠️ Relais trop central | ✅ Respecté |
| Justification métier | ⚠️ Faible (préfixe = hasard) | ✅ Forte (proximité réelle) |
| Robustesse sans GPS | ✅ N/A | ✅ Dégradation gracieuse |

---

## Question ouverte pour l'avis tiers

Les deux approches résolvent le problème de la taille bornée de cluster.
Elles divergent sur **qui prend la décision** et **selon quel critère**.

- **XOR prefix** : décision centralisée (relais), critère algorithmique (bits du NodeID)
- **JOIN explicite** : décision distribuée (Super-Pair), critère physique (distance GPS + capacité)

Le choix dépend de la priorité donnée à :
1. La **décentralisation** (favorise JOIN explicite)
2. La **simplicité d'implémentation** (favorise XOR prefix côté téléphone)
3. La **cohérence géographique** (favorise JOIN explicite)
4. La **scalabilité extrême** (favorise XOR prefix)

---

*Document rédigé le 2026-05-11 pour MobiCloud (PFE Naoui).*
