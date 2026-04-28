# MobiCloud — Décisions d'architecture : connectivité, clustering, super-peer

> **Statut** : décisions issues d'une session de design itérative.
> **Public** : équipe technique MobiCloud + agents IA contribuant au projet.
> **Objectif** : aligner tout le monde sur les choix d'architecture concernant la topologie réseau, le clustering, et le rôle du super-peer. Tous les choix sont **défendables en soutenance** et adossés à des analogies big data reconnues.

---

## 1. Principes directeurs

### 1.1. Le moins centralisé possible
Principe non négociable. Pour **chaque** composant proposé (index, ledger, vérification, gestion de cluster, transport), poser la question : *« est-ce que ça peut être plus distribué ? »*. Si oui, pousser dans cette direction.

**Exception déjà admise** : le super-peer est accepté comme partielle centralisation. Donc « le moins centralisé possible » = super-peer OK car déjà admis, mais **n'ajouter aucune autre centralisation par-dessus**. Le rôle du super-peer doit rester minimal (coordination, arbitrage), pas devenir un trou noir où tout transite.

### 1.2. Focus stockage, pas réseau
La contribution thèse de MobiCloud se situe au niveau **storage / placement / proof-of-storage / récompenses**, pas au niveau de la couche réseau. Quand un problème surgit côté réseau, on cherche une réponse côté stockage.

### 1.3. Simple à implémenter
Équipe big data, timeline PFE bornée. On préfère :
- les patterns big data reconnus (HDFS, Cassandra, Kafka) plutôt que des mécanismes novateurs côté plomberie,
- des structures de données simples (HashMap, listes triées) plutôt que des protocoles complexes (DHT, ICE, NAT traversal manuel).

---

## 2. Contrainte de connectivité réseau (constat empirique)

Tests réels en Algérie sur deux téléphones :

| Source → Destination | Résultat |
|---|---|
| 4G → 4G | ✅ fonctionne |
| 4G → WiFi | ✅ fonctionne (le pair 4G initie) |
| WiFi → WiFi | ❌ ne fonctionne pas |

**Cause** : les deux pairs WiFi sont derrière deux NAT résidentiels distincts ; aucune route directe sans STUN/TURN ou hole punching.

**Conséquence** : on ne peut **jamais compter sur un transfert P2P direct entre deux pairs WiFi** sur des réseaux différents.

Cette contrainte est **structurante** pour toute l'architecture qui suit.

---

## 3. Topologie : fédération de clusters

### 3.1. Multi-cluster, pas mono-cluster

MobiCloud n'est **pas** un système avec un super-peer central. C'est une **fédération de clusters indépendants**, chacun auto-géré par son propre super-peer.

| Propriété | Serveur unique | Multi-super-peer (notre choix) |
|---|---|---|
| SPOF | Si le serveur tombe, tout tombe | Si un super-peer tombe, **seul son cluster est affecté** |
| Coût d'infra | Quelqu'un paie le VPS/cloud | **Zéro coût d'infra** : téléphones d'utilisateurs |
| Promotion dynamique | Impossible | Un autre pair 4G du cluster prend la relève |
| Localité | Latence dépend du serveur | Super-peer **dans** le cluster, latence faible |
| Trust | Confiance en l'opérateur | Trust local : membres du cluster contrôlent leur super-peer |
| Scalabilité | Sharding/replication explicite | **Linéaire** : ajouter un cluster = ajouter un super-peer |
| Souveraineté | Souvent serveur à l'étranger | 100 % sur les téléphones algériens |

### 3.2. Cluster = unité d'intention partagée (modèle workspace)

Un cluster MobiCloud est l'équivalent d'un **workspace Slack** ou d'un **serveur Discord**, transposé au stockage :

- Une **famille** qui sauvegarde ses photos = 1 cluster
- Une **équipe** qui partage des fichiers de projet = 1 cluster
- Une **classe d'étudiants** qui mutualise des cours = 1 cluster
- Une **PME** qui se passe d'un NAS = 1 cluster

Les clusters se forment **par invitation** (QR code, lien signé). Il n'y a **pas de cluster global MobiCloud** — il y a N clusters indépendants qui coexistent.

### 3.3. Paramètres par cluster

| Paramètre | Valeur typique |
|---|---|
| Taille de bloc | 4 Mo |
| Facteur de réplication R | 3 |
| Taille max d'un cluster | 50–200 pairs (puis split) |
| Période de heartbeat | 30 s |

### 3.4. Inter-cluster

Pas nécessaire en v1. Si un jour utile, les super-peers peuvent communiquer entre eux pour mutualiser de la capacité (extension future, mention thèse).

### 3.5. Analogies big data défendables

- **HDFS Federation** — plusieurs NameNodes, chacun gère son namespace
- **Cassandra** — pas de coordinateur central ; les seed nodes sont l'équivalent des super-peers
- **Kafka** — pas un broker maître ; chaque broker est leader de ses partitions
- **BitTorrent** — pas de tracker unique ; chaque torrent a son tracker, plus DHT en fallback
- **Slack / Discord** — workspaces / serveurs comme unités d'intention bornées

---

## 4. Politique de placement : *connectivity-aware*

### 4.1. La règle

> **Au moins une réplique de chaque bloc doit être placée sur un pair joignable depuis l'extérieur (typiquement un pair 4G).**

Appliquée par le super-peer **au moment du placement**. Une fois placée, la donnée vit toute seule, le super-peer ne la touche plus.

### 4.2. Pourquoi cette règle suffit à résoudre le problème WiFi↔WiFi

On **ne tente pas** de transférer directement entre deux pairs WiFi. On déplace le problème vers la couche stockage : tant qu'au moins une réplique est sur un pair 4G, **n'importe quel pair WiFi peut récupérer le bloc** (matrice 4G→WiFi ✅).

### 4.3. Hiérarchie de fallback côté client (B veut un bloc)

1. B demande au super-peer (ou via cache) la liste des pairs détenteurs avec leur type de connexion.
2. **B trie : 4G d'abord, WiFi ensuite, offline en dernier.**
3. B tente le premier pair 4G. Si échec, suivant.
4. Si tous les pairs 4G d'un bloc sont offline (cas pathologique rare avec une politique de placement correcte) → super-peer intervient en relais ponctuel et restaure l'invariant en re-répliquant.

### 4.4. Critères pour être « pair 4G joignable »

- Connexion mobile détectée via `ConnectivityManager.getActiveNetwork()`
- Auto-déclaration au super-peer dans le heartbeat
- Sondage périodique du super-peer (TCP ping) pour confirmer la joignabilité réelle

### 4.5. Analogie big data

> **HDFS fait du *rack-aware placement* : au moins une réplique sur un autre rack pour survivre à la panne d'un rack.**
>
> **MobiCloud fait du *connectivity-aware placement* : au moins une réplique sur un pair joignable pour survivre à l'indisponibilité réseau d'un pair.**

C'est **exactement le même pattern** transposé du datacenter au mobile. Phrase clé pour la soutenance.

---

## 5. Super-peer : rôle et promotabilité

### 5.1. Rôle minimal, jamais bypassé

Le super-peer fait **uniquement** :
- maintenir l'index `bloc_id → liste de pairs détenteurs`
- maintenir la table des membres du cluster
- décider du placement des nouveaux blocs (en appliquant la règle 4G)
- arbitrer les conflits
- relayer en cas pathologique uniquement (pas le chemin nominal)

Le super-peer **arbitre, il ne fait pas**. Il est juge, pas exécuteur.

### 5.2. Critères d'éligibilité

- Connexion 4G (joignable)
- Batterie > seuil (ex. 50 %)
- Espace disponible suffisant
- Stabilité (heartbeats réguliers)

### 5.3. Promotabilité (invariant non négociable)

Le super-peer doit être **remplaçable**. Si le super-peer d'un cluster tombe, un autre pair éligible doit pouvoir prendre la relève automatiquement.

**Sans cette propriété** : chaque cluster a son propre SPOF, l'archi devient « N petits serveurs » au lieu d'un système distribué. L'argument multi-super-peer > serveur s'effondre.

### 5.4. Implications de la promotabilité

- L'**état du super-peer** (index, table membres, métadonnées) doit être **réplicable ou reconstructible** par les pairs du cluster.
- Mécanisme d'**élection** à prévoir (algorithme Bully simplifié) :
  - Heartbeat périodique des membres vers le super-peer
  - Après 3 heartbeats sans réponse → déclenchement de l'élection
  - Score = `(type 4G) > (WiFi) > (offline)`, puis batterie, puis espace, puis ID le plus bas en départage
- Quand l'ancien super-peer revient : il **rejoint comme membre normal**, pas re-promotion automatique. Évite le ping-pong.

---

## 6. Décisions écartées et justifications

### 6.1. WebRTC pour P2P direct WiFi↔WiFi

**Écarté.** Pourquoi :

- **Coût mobile** : libwebrtc ajoute ~10–15 Mo à l'APK, complexité ICE/SDP, problèmes avec le mode Doze d'Android.
- **Toujours besoin d'un serveur joignable** : signaling + STUN + (souvent) TURN. Le super-peer ferait l'affaire pour signaling, mais TURN consommerait sa bande passante → centralisation accrue.
- **CGNAT algérien souvent symétrique** → hole punching échoue fréquemment → fallback TURN nécessaire de toute façon.
- **Coût d'opportunité** : 2–3 semaines de dev pour une équipe sans expertise NAT, au détriment de la vraie contribution thèse (PoS, récompenses, logique mobile-native).
- **Risque démo** : échecs probabilistes, debugging difficile.

**Mention thèse** : citer comme alternative connue, justifier le choix de l'approche placement-aware comme évitement élégant du problème.

### 6.2. Hole punching / NAT traversal manuel

**Écarté pour les mêmes raisons.** Implémenter STUN + hole punching à la main = 3–4 semaines minimum, sans valeur ajoutée pour la thèse.

### 6.3. Serveur central unique

**Écarté.** Brise le principe de décentralisation. Un serveur unique :
- est un SPOF
- a un coût d'infra continu
- crée une dépendance à un opérateur
- empêche la souveraineté algérienne du déploiement
- n'a aucune des propriétés de la fédération multi-super-peer

### 6.4. Clustering géographique (LAN, GPS)

**Écarté.** En 4G, les pairs n'ont pas de notion de LAN partagé. Et : Sara au campus → Sara chez elle déclencherait un changement de cluster, donc perte d'accès à ses fichiers. Inutilisable.

### 6.5. Clustering aléatoire (DHT pure, hash modulo N)

**Écarté.** Brise la cohérence d'intention. Les pairs d'un cluster auraient peu de raisons d'être ensemble, et les transferts inter-clusters seraient constants.

---

## 7. Multi-cluster côté pair : un téléphone, plusieurs workspaces

### 7.1. Un pair appartient à N clusters simultanément

Sans cette propriété, l'app est inutilisable. Un même utilisateur a souvent besoin de :
- son cluster **familial** (photos, sauvegardes)
- son cluster **académique** (cours, TPs)
- son cluster **professionnel** (documents pro)

### 7.2. Isolation totale entre clusters

Chaque cluster est **indépendant** sur le téléphone. Pas de fuite, pas de découverte croisée.

```
Téléphone de Sara
└── /data/mobicloud/
    ├── clusters/
    │   ├── <uuid_famille_benali>/
    │   │   ├── members.json
    │   │   ├── blocks/
    │   │   └── manifests/
    │   ├── <uuid_promo_usthb>/
    │   └── <uuid_stage_sonatrach>/
    └── identity/
        └── keypair.pem
```

L'app fait tourner **N sessions de cluster en parallèle**.

### 7.3. Rôles distincts par cluster

Une même personne peut avoir des rôles différents dans chaque cluster :

| Cluster | Rôle de Sara |
|---|---|
| Famille Benali | Membre |
| Promo 2A Info | Super-peer (4G stable) |
| Stage Sonatrach | Membre |

### 7.4. Quotas par cluster

L'utilisateur définit un quota par cluster :

```
Espace alloué :
  Famille Benali     : 15 Go
  Promo 2A Info      :  5 Go
  Stage Sonatrach    : 10 Go
  ─────────────────────────
  Total              : 30 Go (sur 32 Go libres)
```

### 7.5. Identité

**Choix retenu** : même paire de clés pour tous les clusters (simple). Mention thèse : « clé par cluster » comme amélioration future pour la confidentialité (éviter la corrélation par les super-peers).

### 7.6. Mobilité : appartenance ≠ localisation

L'appartenance au cluster est **persistante** indépendamment de la position physique du pair.

Exemple : Sara reste membre de **Promo 2A Info** qu'elle soit au campus, dans le bus, chez elle, ou à l'étranger. Seul son **rôle effectif** peut varier (super-peer si 4G joignable, membre normal sinon).

**Ce qui change** = sa joignabilité. **Ce qui ne change pas** = son `cluster_id`, sa clé, ses droits, ses données.

> *"MobiCloud découple explicitement l'appartenance au cluster de la topologie réseau du moment. Un pair conserve son identité, son rôle, ses données et ses droits indépendamment de sa position physique ou de la nature de sa connexion à un instant donné."*

---

## 8. Cas d'usage de référence

### 8.1. Acteurs : cluster « Famille Benali »

| Membre | Rôle | Connexion habituelle | Capacités |
|---|---|---|---|
| Karim (père) | Super-peer initial | 4G Djezzy, joignable | 64 Go libres |
| Sara (fille) | Membre | WiFi maison (Idoom), parfois 4G | 32 Go libres |
| Amine (fils) | Membre | 4G Mobilis | 16 Go libres |
| Yasmine (mère) | Membre | WiFi maison | 8 Go libres |
| Téta (grand-mère) | Membre | WiFi maison uniquement | 4 Go libres |

### 8.2. S0 — Création du cluster

1. Karim ouvre MobiCloud, "Créer un cluster" → "Famille Benali".
2. L'app génère `cluster_id` (UUID v4) + paire de clés asymétriques.
3. Détection de connexion via `ConnectivityManager` → 4G, joignabilité confirmée par TCP ping vers Serveur Relais HA.
4. Karim devient super-peer initial automatiquement.
5. Service en background : index vide, table des membres avec lui seul, serveur HTTP local.

### 8.3. S1 — Invitations en cascade

1. Karim génère un QR contenant `cluster_id` + endpoint super-peer + token signé (1 usage, 24 h).
2. Sara scanne → contact Karim → soumet sa clé publique + profil (espace, type connexion).
3. Karim valide signature, ajoute Sara à la table, signe son adhésion, renvoie la **vue du cluster**.
4. Sara stocke localement et utilise pour les opérations futures.

### 8.4. S2 — Sara upload `photos_aïd.zip` (50 Mo)

1. **Découpage local** : 13 blocs (12 × 4 Mo + 1 × 2 Mo), hash SHA-256 par bloc.
2. **Manifeste** : nom, taille, liste ordonnée des hashs, métadonnées.
3. **Demande de placement** au super-peer Karim.
4. **Karim sélectionne 3 pairs par bloc** avec contrainte ≥ 1 4G. Évite Sara elle-même comme réplique.
   ```
   Bloc #1 candidats : Karim[4G] ✓ Amine[4G] ✓ Yasmine[WiFi] Téta[WiFi]
   Sélection R=3 avec ≥1 4G : [Karim, Amine, Yasmine]
   ```
5. Karim renvoie la table `bloc_id → [pair1, pair2, pair3]`.
6. **Upload parallèle** de Sara vers les pairs assignés (Sara WiFi → eux 4G/WiFi : matrice OK).
7. **Confirmation** : chaque récepteur vérifie le hash, confirme à Karim.
8. **Karim met à jour son index** avec la liste effective des détenteurs.

### 8.5. S3 — Yasmine récupère le fichier (cas favorable)

1. Yasmine → Karim : « manifeste de `photos_aïd.zip` ? »
2. Pour chaque bloc, Yasmine trie les détenteurs : 4G d'abord.
3. Téléchargement bloc par bloc depuis pairs 4G (matrice 4G→WiFi : OK).
4. Reconstitution.

### 8.6. S4 — Téta récupère un fichier (cas dégradé géré)

Bloc avec placement `[Sara_WiFi, Yasmine_WiFi, Amine_4G]`.

- Téta tente Amine 4G : ✓ (matrice 4G↔WiFi).
- **Si Amine offline** : Téta tente Sara WiFi → échec NAT. Yasmine WiFi → échec.
- Téta signale au super-peer.
- Karim relance : il joint Sara WiFi depuis sa 4G ✓, télécharge le bloc, sert Téta. **Restaure ensuite l'invariant** en plaçant une réplique sur un pair 4G dispo.

### 8.7. S5 — Karim part à l'étranger, sa 4G coupe

1. Heartbeats absents pendant 3 cycles → déclenchement de l'élection.
2. Score : Amine (4G) gagne face à Sara/Yasmine/Téta (WiFi).
3. **Amine devient le nouveau super-peer**.
4. Reconstitution de l'index : Amine demande aux pairs « envoyez-moi la liste de vos blocs » → reconstruit l'index.
5. Au retour de Karim : il rejoint comme membre normal. Pas de re-promotion auto.

### 8.8. S6 — La famille s'agrandit, dépassement du seuil

1. Cluster passe à 51 membres (max = 50).
2. Super-peer détecte le dépassement.
3. **Split** : partition des membres en 2 groupes (graphe d'invitations ou simple hash).
4. Groupe A garde le `cluster_id`, super-peer reste Amine.
5. Groupe B reçoit un nouveau `cluster_id`, élection d'un super-peer (par exemple Karim).
6. **Re-balancing** des données pour maintenir R = 3 dans chaque groupe.
7. Notification aux pairs.

Analogie : **split de partition Cassandra**, **re-balance de partitions Kafka**.

### 8.9. S7 — Sara perd son téléphone

1. Sara installe MobiCloud sur son nouveau téléphone, restaure son identité (clé sauvegardée hors ligne, ou re-validée par un membre via QR).
2. Contact super-peer, vérification d'identité (signature).
3. Vue du cluster + manifeste personnel récupérés.
4. Sara peut télécharger ses fichiers comme S3.
5. Les blocs qu'elle stockait (en tant que pair) sont **considérés perdus** → super-peer re-réplique sur d'autres pairs pour maintenir R = 3.

---

## 9. Trace détaillée d'un téléchargement (cas dégradé typique)

**Contexte** : Sara, 19h, WiFi Idoom (NAT), veut récupérer `tp_algorithmes.zip` (24 Mo, 6 blocs) du cluster **Promo 2A Info USTHB**. Super-peer = Mehdi (4G Djezzy).

### 9.1. Topologie au moment du téléchargement

```
Cluster Promo 2A Info — état à 19h12

  Mehdi    [4G Djezzy]    super-peer    online ✓
  Lina     [4G Mobilis]   membre        online ✓
  Nadia    [4G Ooredoo]   membre        online ✓
  Yacine   [WiFi maison]  membre        online (NAT)
  Karim    [WiFi maison]  membre        online (NAT)
  Walid    [—]            membre        offline (batterie morte)
  Sara     [WiFi Idoom]   membre        online (NAT)  ← elle
  ... 23 autres
```

### 9.2. Placement actuel des blocs

| Bloc | Détenteurs |
|---|---|
| #1 | Mehdi 4G ✓ — Lina 4G ✓ — Karim WiFi ✗ |
| #2 | Mehdi 4G ✓ — Yacine WiFi ✗ — Nadia 4G ✓ |
| #3 | Lina 4G ✓ — Yacine WiFi ✗ — Karim WiFi ✗ |
| #4 | Nadia 4G ✓ — Karim WiFi ✗ — Walid offline ✗ |
| #5 | Mehdi 4G ✓ — Lina 4G ✓ — Karim WiFi ✗ |
| #6 | Mehdi 4G ✓ — Nadia 4G ✓ — Yacine WiFi ✗ |

✓ joignable depuis Sara | ✗ pas joignable directement

### 9.3. Étapes

**Étape 1 — Sara ouvre l'app** → cluster Promo 2A Info → fichier `tp_algorithmes.zip` → Télécharger.

**Étape 2 — Récupération du manifeste** : Sara WiFi → Mehdi 4G : OK. Mehdi renvoie manifeste signé + table de placement à jour.

> Note : Sara a peut-être un manifeste caché localement. L'app utilise quand même celui du super-peer (un pair peut avoir perdu le bloc entre temps). Si Mehdi injoignable, fallback sur cache.

**Étape 3 — Téléchargement parallèle** (3-4 connexions simultanées max) :

| Bloc | Tentatives | Résultat | Source effective |
|---|---|---|---|
| #1 | Mehdi (4G) | ✓ direct | Mehdi |
| #2 | Nadia (4G) — load balancing | ✓ direct | Nadia |
| #3 | Lina busy → Yacine NAT timeout → Karim NAT timeout → Lina retry | ✓ après backoff | Lina |
| #4 | Nadia (4G) | ✓ direct | Nadia |
| #5 | Mehdi (4G) | ✓ direct | Mehdi |
| #6 | Mehdi (4G) | ✓ direct | Mehdi |

**Bilan** : 5 succès directs + 1 retry, 0 fallback super-peer. La règle de placement a payé son loyer.

**Étape 4 — Reconstitution** : 6 blocs concaténés selon l'ordre du manifeste, vérification du hash global, écriture dans `Téléchargements/`.

**Étape 5 — Sara ouvre le fichier**.

### 9.4. Cas pire : tous les pairs 4G d'un bloc indisponibles

1. App signale au super-peer.
2. Super-peer joint un pair WiFi (matrice 4G→WiFi OK), récupère le bloc, sert au demandeur.
3. Super-peer **re-réplique** le bloc sur un pair 4G pour restaurer l'invariant.

Le super-peer agit en **relais ponctuel** dans ce cas pathologique. C'est un fallback, pas le chemin nominal. La fréquence de ce fallback est un **indicateur de qualité du placement**, mesurable empiriquement dans la section évaluation de la thèse.

---

## 10. Ce que ces choix prouvent (mapping pour la soutenance)

| Aspect | Démontré par | Analogie big data |
|---|---|---|
| Multi-tenant, bootstrap par invitation | S0–S1 | Workspace Slack/Discord, déploiement HDFS |
| Placement contraint (rack-aware) | S2 | HDFS rack-aware placement |
| Tolérance à la non-joignabilité (NAT) via stockage | S3–S4, §9 | Stratégie de réplication HDFS |
| Pas de SPOF, super-peer promotable | S5 | NameNode HA, élection (Bully/Raft) |
| Scalabilité par split de cluster | S6 | Re-balance Cassandra, partitions Kafka |
| Tolérance à la perte d'un pair | S7 | Re-réplication HDFS |
| Multi-cluster côté pair | §7 | Client Hadoop multi-cluster, consumer Kafka |
| Découplage appartenance / localisation | §7.6 | (contribution mobile-native, pas couvert par big data classique) |

### 10.1. Phrases-clés pour la soutenance

> *"MobiCloud n'est pas une architecture client-super-peer. C'est une **fédération de clusters**, où chaque cluster est auto-géré par un super-peer **promotable**. Aucun composant unique n'est nécessaire au fonctionnement du système — c'est ce qui distingue MobiCloud d'un cloud centralisé hébergé sur mobile."*

> *"HDFS fait du *rack-aware placement* pour survivre à la panne d'un rack. MobiCloud fait du *connectivity-aware placement* pour survivre à l'indisponibilité réseau d'un pair. C'est exactement le même pattern, transposé du datacenter au mobile."*

> *"Un cluster MobiCloud est défini comme une **unité d'intention partagée** : un groupe de pairs ayant une raison commune (familiale, organisationnelle, sociale) de mutualiser leur stockage. Cette définition, inspirée du modèle workspace de Slack ou Discord et du déploiement borné de HDFS, garantit la stabilité, la cohérence sociale et la scalabilité par fédération de clusters indépendants."*

> *"L'établissement de connexions directes entre pairs derrière NAT est résoluble par des techniques standard (ICE/STUN/TURN, RFC 8445). Cependant, en contexte CGNAT algérien, la fiabilité de ces techniques est limitée. Nous avons opté pour une approche placement-aware qui contourne le problème au niveau de la couche stockage, garantissant qu'au moins une réplique soit toujours joignable. Cette approche évite la dépendance à un serveur TURN centralisé."*

> *"MobiCloud découple explicitement l'appartenance au cluster de la topologie réseau du moment. Un pair conserve son identité, son rôle, ses données et ses droits indépendamment de sa position physique ou de la nature de sa connexion à un instant donné."*

---

## 11. Questions ouvertes (à trancher avec l'encadrant)

| Question | Options | Notes |
|---|---|---|
| Comment **délimiter un cluster** quand on est en 4G pur (pas de LAN) ? | Workspace par invitation (recommandé) vs autres approches | Choix retenu : workspace |
| **Taille max du cluster** avant split ? | 50 / 100 / 200 ? | À calibrer empiriquement, paramètre tunable |
| **Identité** : clé unique vs clé par cluster ? | Unique (simple) vs par cluster (privé) | Recommandation PFE : unique, extension future = par cluster |
| **Bootstrap** : juste invitation, ou registre d'amorçage minimal ? | Invitation pure (purement P2P) vs registre optionnel | À discuter selon la cible utilisateur |
| **Comment quantifier "joignable"** au moment du placement ? | Auto-déclaration vs sondage périodique TCP | Hybride recommandé |
| **Mécanisme d'élection du super-peer** ? | Bully / Raft simplifié / score-based | Score-based recommandé pour simplicité |
| **Inter-cluster** (mutualisation entre clusters) ? | Pas en v1, mention extension future | Hors scope PFE |

---

## 12. Glossaire

| Terme | Définition |
|---|---|
| **Cluster** | Groupe borné de pairs partageant une intention (famille, équipe, classe). Unité de gouvernance et de stockage. |
| **Super-peer** | Pair d'un cluster avec rôle élargi : index, placement, arbitrage. Promotable. |
| **Pair** | Téléphone Android participant à un ou plusieurs clusters. |
| **Bloc** | Unité de stockage (4 Mo). Chaque fichier est découpé en blocs. |
| **Manifeste** | Description d'un fichier : liste ordonnée des hashs de blocs + métadonnées. |
| **R** | Facteur de réplication (3 par défaut). |
| **Placement connectivity-aware** | Règle de placement garantissant ≥ 1 réplique sur un pair joignable. |
| **Promotabilité** | Propriété d'un super-peer d'être remplaçable par un autre pair éligible. |
| **Matrice de connectivité** | 4G↔4G ✅, 4G↔WiFi ✅, WiFi↔WiFi ❌. Constat empirique structurant. |

---

## 13. Références suggérées pour la thèse

- HDFS Architecture, *The Hadoop Distributed File System*, Shvachko et al., 2010 (rack-awareness, replication policy)
- *Cassandra: A Decentralized Structured Storage System*, Lakshman & Malik, 2010 (multi-cluster, gossip, seed nodes)
- RFC 8445 — Interactive Connectivity Establishment (ICE) — citée comme alternative écartée
- RFC 5389 — Session Traversal Utilities for NAT (STUN)
- *Skype's Super-Nodes: Network Architecture*, Baset & Schulzrinne, 2006 (super-peer topologies)
- Kafka multi-cluster patterns (KIP-382 / Mirror Maker 2)
- BitTorrent : tracker + DHT fallback (BEP-5)
