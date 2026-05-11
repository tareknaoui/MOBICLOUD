# Délimitation de Cluster : GPS + Multicast Hybride

## Le problème de base

Dans MobiCloud, un **cluster** est un groupe de nœuds mobiles coordonnés par un Super-Pair élu.
La question clé : **comment savoir quels appareils doivent appartenir au même cluster ?**

L'approche naïve (WiFi SSID = un cluster) est trop fragile :
- Deux appareils dans le même bâtiment mais sur des SSIDs différents → clusters séparés
- Un nœud 4G n'a pas de SSID → pas de frontière naturelle
- Deux nœuds 4G à 1000km peuvent se découvrir via Firebase et finir dans le même cluster

---

## L'approche retenue : GPS + Multicast Hybride

> **Principe :** la découverte utilise les mécanismes réseau existants (UDP Multicast + Firebase),
> mais l'**adhésion au cluster est filtrée par distance GPS**.

```
Découverte (réseau)      →   Election Bully   →   JOIN filtré par GPS
UDP Multicast + Firebase      Super-Pair élu        distance < MAX_RADIUS ?
                                                     ✅ JOIN_ACCEPT
                                                     ❌ JOIN_REDIRECT
```

---

## Les deux mécanismes de découverte

| Mécanisme | Portée réseau | Portée physique réelle |
|-----------|--------------|----------------------|
| UDP Multicast `224.0.0.1:50000` | Même sous-réseau (routeur) | ~10–100m (même bâtiment) |
| Firebase Realtime DB `active_nodes/` | Internet entier | **Illimitée** ← problème |

Le GPS sert à **corriger** Firebase : même si deux nœuds se découvrent via Firebase à 1000km, le filtre GPS les empêche de rejoindre le même cluster.

---

## Exemple concret : 5 appareils à Alger

### Positions géographiques

```
                    [Université USTHB]
                         Alice (WiFi)
                          36.706°N  3.175°E
                          Super-Pair élue

          [Café Didouche]           [Bab Ezzouar]
          Bob (WiFi)                Carol (4G)
          36.738°N  3.050°E         36.713°N  3.184°E
          ~10km d'Alice             ~0.8km d'Alice
          REJETÉ                    ACCEPTÉE


                    [Paris, France]
                    Dave (4G)
                    48.860°N  2.347°E
                    ~1350km d'Alice
                    REJETÉ
```

**MAX_RADIUS = 5km** (annoncé par Alice dans son message COORDINATOR)

---

## Déroulé étape par étape

### Étape 1 — Découverte (T=0s à T=20s)

Chaque appareil démarre son service P2P et annonce sa présence :

- **Alice, Bob** → UDP Multicast (même WiFi ou Firebase)
- **Carol, Dave** → Firebase `active_nodes/`

Après 20 secondes sans Super-Pair détecté, l'**élection Bully** se déclenche.

---

### Étape 2 — Élection Bully (T=20s à T=23s)

Les 4 nœuds comparent leurs scores de fiabilité :

```
Alice  score=0.91  ← le plus haut → gagne
Carol  score=0.82
Bob    score=0.75
Dave   score=0.60
```

Alice diffuse le message **COORDINATOR** — nouveau format avec GPS et rayon :

```json
{
  "type": "COORDINATOR",
  "senderId": "aa-111",
  "clusterId": "wifi-usthb-5GHz",
  "maxRadiusMeters": 5000,
  "gpsLocation": { "lat": 36.706, "lng": 3.175 },
  "timestamp": 23000,
  "signature": "..."
}
```

---

### Étape 3 — Chaque nœud envoie un JOIN_REQUEST (T=23s)

Après réception du COORDINATOR, chaque nœud envoie sa demande d'adhésion
avec **sa propre position GPS** :

**Carol (Bab Ezzouar, 4G) :**
```json
{
  "type": "JOIN_REQUEST",
  "senderId": "cc-333",
  "clusterId": "wifi-usthb-5GHz",
  "gpsLocation": { "lat": 36.713, "lng": 3.184 },
  "freeStorageBytes": 8000000000,
  "reliabilityScore": 0.82,
  "timestamp": 23150,
  "signature": "..."
}
```

**Bob (Café Didouche, WiFi) :**
```json
{
  "type": "JOIN_REQUEST",
  "senderId": "bb-222",
  "gpsLocation": { "lat": 36.738, "lng": 3.050 },
  "freeStorageBytes": 12000000000,
  "reliabilityScore": 0.75,
  "timestamp": 23100,
  "signature": "..."
}
```

**Dave (Paris, 4G) :**
```json
{
  "type": "JOIN_REQUEST",
  "senderId": "dd-444",
  "gpsLocation": { "lat": 48.860, "lng": 2.347 },
  "freeStorageBytes": 5000000000,
  "reliabilityScore": 0.60,
  "timestamp": 23200,
  "signature": "..."
}
```

---

### Étape 4 — Alice filtre les JOIN_REQUEST par distance GPS (T=23.5s)

Alice calcule la distance haversine entre elle et chaque demandeur :

```
Position Alice = (36.706°N, 3.175°E)
MAX_RADIUS     = 5000m

Carol → haversine(Alice, Carol) =   980m  ✅  < 5000m  → JOIN_ACCEPT
Bob   → haversine(Alice, Bob)   = 10200m  ❌  > 5000m  → JOIN_REDIRECT
Dave  → haversine(Alice, Dave)  = 1350km  ❌  > 5000m  → JOIN_REDIRECT
```

**Réponse à Carol :**
```json
{
  "type": "JOIN_ACCEPT",
  "clusterId": "wifi-usthb-5GHz",
  "memberSnapshot": [
    { "nodeId": "aa-111", "ip": "192.168.1.10", "port": 9001, "isSuperPair": true },
    { "nodeId": "cc-333", "ip": "10.0.2.5",     "port": 9001, "isSuperPair": false }
  ]
}
```

**Réponse à Bob :**
```json
{
  "type": "JOIN_REDIRECT",
  "reason": "OUT_OF_RADIUS",
  "distanceMeters": 10200,
  "maxRadiusMeters": 5000
}
```

---

### Étape 5 — Bob et Dave forment leurs propres clusters (T=44s)

Les nœuds rejetés attendent 20s, cherchent des pairs proches, et déclenchent
leurs propres élections :

```
Bob (Café Didouche) + Emna (même café) → élection → Cluster B
Dave (Paris)        + Eve  (Paris)     → élection → Cluster C
```

---

### Résultat final — 3 clusters géographiquement cohérents

```
┌──────────────────────────────────────────┐
│  Cluster A — Bab Ezzouar / USTHB         │
│  Super-Pair : Alice  (WiFi, 36.706°N)    │
│  Membre     : Carol  (4G,  36.713°N)     │
│  Rayon      : 5km                        │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│  Cluster B — Café Didouche               │
│  Super-Pair : Bob    (WiFi, 36.738°N)    │
│  Membre     : Emna   (WiFi, 36.740°N)    │
│  Rayon      : 5km                        │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│  Cluster C — Paris                       │
│  Super-Pair : Dave   (4G,  48.860°N)     │
│  Membre     : Eve    (4G,  48.852°N)     │
│  Rayon      : 5km                        │
└──────────────────────────────────────────┘
```

---

## Machine à états d'un nœud

```
UNDISCOVERED
     │
     ▼  reçoit COORDINATOR
JOINING ──── envoie JOIN_REQUEST ────────────────────┐
     │                                               │
     ├── JOIN_ACCEPT  → MEMBER (actif)               │
     │                                               │
     ├── JOIN_REDIRECT → cherche autre Super-Pair    │
     │        └── nouveau JOIN_REQUEST               │
     │                                               │
     └── timeout (5s) → ISOLATED                    │
              └── attend 20s → re-déclenche élection ┘

MEMBER
     │  envoie heartbeat toutes les 5s au Super-Pair
     │
     ├── Super-Pair absent >5s → REJOINING → re-élection
     └── reçoit ABDICATION    → REJOINING → re-élection
```

---

## Registre du Super-Pair (table Room `cluster_members`)

```
┌──────────┬───────────────┬───────┬──────────────────┬─────────┐
│ nodeId   │ ipAddress     │ port  │ lastHeartbeatMs  │ status  │
├──────────┼───────────────┼───────┼──────────────────┼─────────┤
│ cc-333   │ 10.0.2.5      │ 9001  │ 24000            │ ACTIVE  │
└──────────┴───────────────┴───────┴──────────────────┴─────────┘

Règles d'éviction :
- Membre absent > 15s → status = EVICTED
- MEMBER_UPDATE delta diffusé à tous les membres actifs
```

---

## Ce qui change dans le code

### Nouveaux messages

| Message | Émetteur | Destinataire | Contenu clé |
|---------|----------|-------------|-------------|
| `JOIN_REQUEST` | Pair ordinaire | Super-Pair | GPS, freeStorage, signature |
| `JOIN_ACCEPT` | Super-Pair | Pair | memberSnapshot |
| `JOIN_REDIRECT` | Super-Pair | Pair | reason, distanceMeters |
| `MEMBER_UPDATE` | Super-Pair | Tous les membres | deltaAdded, deltaRemoved |
| `LEAVE` | Pair | Super-Pair | départ gracieux |

### Nouveaux use cases

| Use Case | Rôle |
|----------|------|
| `SendJoinRequestUseCase` | Envoie JOIN après réception COORDINATOR, gère retry |
| `ProcessJoinRequestUseCase` | Valide signature + GPS, accepte ou redirige |
| `MemberHeartbeatUseCase` | Heartbeat 5s vers Super-Pair, détecte sa mort |

### Modifications existantes

| Fichier | Modification |
|---------|-------------|
| `RunBullyElectionUseCase` | Après victoire → déclenche `SendJoinRequestUseCase` |
| `ProcessIncomingElectionEventUseCase` | Après COORDINATOR → déclenche `SendJoinRequestUseCase` |
| `ElectionPayload` | Ajoute `maxRadiusMeters` et `gpsLocation` dans COORDINATOR |

---

## Calcul de distance (Haversine)

```kotlin
fun haversineMeters(a: GpsCoordinate, b: GpsCoordinate): Double {
    val R = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) *
            cos(Math.toRadians(b.lat)) *
            sin(dLng / 2).pow(2)
    return 2 * R * asin(sqrt(h))
}
```

> **Note :** La permission `ACCESS_FINE_LOCATION` est **déjà déclarée** dans le Manifest
> (utilisée pour lire le SSID WiFi). Aucune permission supplémentaire n'est nécessaire.

---

## Valeurs de rayon recommandées

| Contexte | MAX_RADIUS | Justification |
|----------|-----------|---------------|
| Campus / bureau | 200m | Même bâtiment |
| Quartier | 1km | Zone de confiance sociale |
| Ville | 10km | Valeur par défaut |
| Configurable | libre | Annoncé dans le COORDINATOR |

---

## Avantages pour la soutenance

1. **La frontière du cluster est géographique, pas réseau** — indépendante du SSID, du FAI, de la topologie NAT.
2. **Membership explicite et signé** — un nœud est membre uniquement s'il a reçu un `JOIN_ACCEPT` cryptographiquement authentifié.
3. **Auto-régulant** — les nœuds rejetés forment organiquement de nouveaux clusters sans intervention centrale.
4. **Fonctionne en 4G et WiFi** — le filtre GPS unifie les deux cas.
5. **Réutilise une permission existante** — aucun nouveau risque de refus utilisateur.
