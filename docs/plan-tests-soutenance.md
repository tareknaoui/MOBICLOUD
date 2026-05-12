# Plan de tests — Soutenance PFE MobiCloud

> **Objectif :** définir un dossier de tests complet et défendable pour la soutenance, couvrant les comportements nominaux, les pannes, la mobilité et le passage à l'échelle.

---

## Vue d'ensemble

| # | Test | Type | Effort | Priorité |
|---|------|------|--------|----------|
| 1 | Intra-cluster nominal | Démo IRL | 1 j | 🔴 Obligatoire |
| 2 | Inter-cluster | Démo IRL | 2 j | 🔴 Obligatoire |
| 3 | Mort du Super-Pair (résilience) | Démo IRL | 1 j | 🔴 Obligatoire |
| 4 | Migration de nœud entre clusters | Démo IRL | 3-4 j | 🟡 Bonus |
| 5 | Simulation 1 000 appareils | Simulation logicielle | 4-5 j | 🔴 Différenciant |

**Effort total estimé : 11-13 jours-développeur.**

### Constantes utilisées

| Paramètre | Valeur |
|-----------|--------|
| `MAX_CLUSTER_SIZE` | 50 |
| `MAX_RADIUS` | 5 km |
| `HEARTBEAT_INTERVAL` | 30 s |
| `SP_TIMEOUT` | 90 s |

---

## Test 1 — Intra-cluster nominal

### Objectif

Démontrer le cycle de vie complet d'un cluster unique : découverte, JOIN, stockage P2P, récupération.

### Setup

- **4 téléphones** : Alice, Bob, Carol, Dave
- Même réseau Wi-Fi (ou mix Wi-Fi + 4G)
- Tous géographiquement proches (< 5 km)

### Déroulé

| Étape | Action | Vérification |
|-------|--------|--------------|
| 1 | Alice lance MobiCloud (seule) | Logs : `Bully solo`, devient SP, `clusterId` généré |
| 2 | Bob lance MobiCloud | Logs : `JOIN_REQUEST → JOIN_ACCEPT`, member registry mis à jour |
| 3 | Carol, Dave lancent | Cluster = {Alice, Bob, Carol, Dave} |
| 4 | Alice upload un fichier 5 MB | Blocs répliqués sur Bob/Carol/Dave |
| 5 | Alice supprime le fichier local | Le fichier reste accessible (récupéré depuis les pairs) |
| 6 | Bob retrouve un bloc spécifique | Communication 100 % intra-cluster |

### Métriques à capturer

- Temps de JOIN par téléphone — **target < 2 s en Wi-Fi**
- Temps d'upload et de download des blocs
- Trafic réseau passé par le relai (target : **0 octet en Wi-Fi LAN pur**)
- Logs `adb logcat | grep MobiCloud` archivés comme preuves

---

## Test 2 — Inter-cluster

### Objectif

Démontrer la redondance géographique et la survie des données quand un cluster entier disparaît.

### Setup

- **6 téléphones** répartis en 2 groupes
- **Groupe A** : 3 téléphones sur Wi-Fi `network-A` (Alice SP, Bob, Carol)
- **Groupe B** : 3 téléphones sur Wi-Fi `network-B` ou en 4G (Dave SP, Eve, Frank)
- Les 2 clusters ont des `clusterId` distincts

**Astuce démo** : si pas de 2 vrais réseaux distants disponibles, forcer des GPS différents via override `MockLocationProvider` (mode dev).

### Déroulé

| Étape | Action | Vérification |
|-------|--------|--------------|
| 1 | Vérifier l'état initial : 2 clusters distincts | `GET /super-peers` retourne {Alice, Dave} |
| 2 | Alice upload un fichier avec redondance géographique activée | Trigger `RequestInterClusterHostingUseCase` |
| 3 | Alice trouve Dave comme candidat distant | Logs : `inter-cluster candidate selected: dave-pk-hash` |
| 4 | Bloc envoyé via relai HA (4G ↔ Wi-Fi) | Logs côté relai : transfer X MB |
| 5 | Killer le groupe A complet | Cluster A disparaît |
| 6 | Bob réinstalle l'app sur un device tiers, demande le fichier d'Alice | Récupère le bloc depuis le cluster B |

### Métriques à capturer

- Latence inter-cluster vs intra-cluster (le relai en pivot)
- Débit du relai HA pendant le transfert (MB/s)
- Survie du fichier après mort complète du cluster A : **100 % attendu**

> **C'est le test le plus important pour défendre la redondance géographique.**

---

## Test 3 — Mort du Super-Pair (résilience)

### Objectif

Prouver la **promotabilité du Super-Pair** : un cluster doit survivre à la mort de son SP sans perte de données.

### Setup

- Cluster de 4 téléphones (Alice SP, Bob, Carol, Dave)
- Cluster stable depuis au moins 2 minutes (heartbeats en cours)

### Déroulé

| Étape | Action | Vérification |
|-------|--------|--------------|
| 1 | Cluster stable, heartbeats en cours | Logs : `HEARTBEAT received from bob/carol/dave` |
| 2 | **Force-stop l'app d'Alice** : `adb shell am force-stop com.mobicloud` | Alice disparaît du réseau |
| 3 | Démarrer chronomètre | T0 |
| 4 | Bob/Carol/Dave détectent le silence d'Alice | Après 90 s (`SP_TIMEOUT`) : logs `SP unreachable` |
| 5 | Bully déclenché entre {Bob, Carol, Dave} | Échanges ELECTION/ALIVE |
| 6 | Bob (meilleur score) devient SP | Logs : `COORDINATOR(clusterId, bob-pk-hash)` |
| 7 | Vérifier la continuité du `memberSnapshot` | Carol et Dave restent membres sans re-JOIN |
| 8 | Bob s'annonce au tracker | Tracker mis à jour |
| 9 | Tenter de récupérer un fichier stocké initialement chez Alice | Récupéré depuis la réplication Bob/Carol/Dave |

### Métriques à capturer

- **Temps total de récupération** (T0 → nouveau SP fonctionnel) — **target < 100 s**
  - 90 s timeout + 5 s Bully + 5 s annonce tracker
- Nombre de messages Bully échangés
- Aucune perte de données (vérifier hash des blocs)

> **C'est le test qui prouve la promotabilité du Super-Pair, principe sacré de la thèse.**

---

## Test 4 — Migration de nœud entre clusters

### ⚠️ Pré-requis

Le design actuel **ne couvre pas explicitement** ce cas. Il faut ajouter une mécanique côté membre régulier :

```kotlin
// EvaluateClusterFitUseCase.kt
// Déclenché toutes les 5 min OU sur GPS update significatif

class EvaluateClusterFitUseCase {
    fun execute() {
        val myGps = locationProvider.current() ?: return
        val spGps = memberRegistry.getSuperPairGps()
        val distance = Haversine(myGps, spGps)
        if (distance > MAX_RADIUS) {
            sendLeave()                          // LEAVE → SP actuel
            searchAndJoinCloserCluster()         // discovery + JOIN_REQUEST → SP plus proche
        }
    }
}
```

### Setup

- **2 clusters** :
  - Cluster A (Alger) : Alice SP + Carol (membre)
  - Cluster B (Oran) : Dave SP
- Mode dev : override GPS via `MockLocationProvider` pour simuler le déplacement de Carol

### Déroulé

| Étape | Action | Vérification |
|-------|--------|--------------|
| 1 | État initial : Carol ∈ cluster A | `memberRegistry.contains(carol) == true` chez Alice |
| 2 | Mock le GPS de Carol : Alger → Oran (saut de 400 km) | `EvaluateClusterFitUseCase` se déclenche |
| 3 | Carol détecte `distance > MAX_RADIUS` | Logs : `cluster fit lost, distance=398km` |
| 4 | Carol envoie `LEAVE` à Alice | Alice diffuse `MEMBER_UPDATE { event: LEFT }` |
| 5 | Carol interroge le tracker, trouve Dave (Oran) | Sélection par proximité GPS |
| 6 | Carol envoie `JOIN_REQUEST` à Dave | Distance OK, capacité OK → `JOIN_ACCEPT` |
| 7 | Carol est maintenant ∈ cluster B | Continuité vérifiée |
| 8 | Vérifier le sort des blocs stockés par Carol pour A | Re-réplication ou orphelins ? |

### Métriques à capturer

- Temps total de migration (T0 détection → T1 nouveau `JOIN_ACCEPT`)
- Comportement des blocs : abandonnés ou re-répliqués automatiquement ?

### Point honnête à documenter

Que se passe-t-il pour les blocs stockés chez Carol pour le cluster A ?

**Réponse défendable** : Alice déclenche une re-réplication vers un autre membre de A dès la réception du `MEMBER_UPDATE { LEFT }`. Cette logique nécessite un ajout (`ReplicateBlocksOnMemberLeaveUseCase`), à intégrer comme **perspective court terme** si le temps manque pour la V4.

---

## Test 5 — Simulation à 1 000 appareils

### Objectif

Produire des **chiffres de scalabilité** défendables face au jury, au-delà de ce que la démo IRL peut prouver.

### Architecture de la simulation

#### Choix recommandé : Node.js + simulation in-memory

- **1 process Node.js** orchestrateur
- **1 000 "device actors"** : objets JS avec `EventEmitter`, encapsulant la logique d'un téléphone (state, GPS, score Bully, member registry)
- **Bus de messages central** in-memory simulant le réseau (avec latence configurable selon Wi-Fi/4G)
- **Horloge simulée** (`virtualTime.advance(30s)`) — évite d'attendre 30 s réels par tick
- Réutilise une partie du code de `relay-server/server.js` pour les messages

#### Alternative : Kotlin headless

- Extraire la logique métier (use-cases) en module Kotlin pur, sans dépendances Android
- Lancer 1 000 instances dans une JVM
- Avantage : code 100 % aligné avec l'app
- Inconvénient : plus complexe à mettre en place

→ **Choix recommandé pour PFE : Node.js.** Plus rapide à écrire, suffit à produire les courbes attendues.

### Distribution géographique simulée (Algérie)

```javascript
const cities = [
  { name: "Alger",        coords: [36.75, 3.04],   users: 400 },
  { name: "Oran",         coords: [35.70, -0.63],  users: 150 },
  { name: "Constantine",  coords: [36.36, 6.61],   users: 100 },
  { name: "Annaba",       coords: [36.90, 7.77],   users: 80 },
  { name: "Sétif",        coords: [36.19, 5.41],   users: 70 },
  { name: "Tlemcen",      coords: [34.88, -1.31],  users: 60 },
  { name: "Batna",        coords: [35.55, 6.17],   users: 60 },
  { name: "Béjaïa",       coords: [36.75, 5.08],   users: 50 },
  { name: "Blida",        coords: [36.47, 2.83],   users: 30 },
  { name: "Rural",        coords: "random",        users: 80 }
]
// Chaque user reçoit un GPS = centre ville + jitter gaussien de 3 km
```

### Scénarios à exécuter

| # | Scénario | Question | Métriques clés |
|---|----------|----------|----------------|
| **S1** | Convergence initiale | Combien de temps pour stabiliser 1 000 nœuds en clusters ? | Time-to-converge, distribution des tailles |
| **S2** | Churn permanent | 10 % des nœuds disparaissent toutes les minutes | Stabilité du `cluster count`, élections Bully/min |
| **S3** | Vague d'arrivées | 500 nœuds arrivent simultanément (cold start massif) | Latence JOIN moyenne, taux de REDIRECT |
| **S4** | Failure cascade | 50 Super-Pairs meurent en même temps | Temps de récupération, % blocs orphelins |
| **S5** | Mobilité | 5 % des nœuds bougent de > 50 km/heure simulée | Taux de migration, charge tracker |

### Métriques à capturer pour le rapport

```
[METRICS_PER_SCENARIO]
- Nombre final de clusters
- Distribution des tailles (histogramme) → graphe matplotlib
- Distribution géographique → carte avec markers
- Trafic moyen par nœud (msg/s)
- Trafic au tracker (req/s)
- Trafic au relai (msg/s, MB/s)
- Temps de convergence post-perturbation
- % de JOIN_REQUEST échoués / redirigés
- Élections Bully déclenchées / minute
```

### Livrables pour la soutenance

1. **3-4 graphes matplotlib** :
   - Distribution des tailles de cluster (histogramme)
   - Convergence : time series de "clusters stables" sur 1 heure simulée
   - Latence JOIN en fonction de la densité géographique
   - Trafic relai en fonction du nombre de users
2. **Une carte** des clusters générés (folium en Python ou Leaflet en JS)
3. **Un tableau** comparant les 5 scénarios

### Pseudo-architecture du simulateur

```
simulator/
├── package.json
├── src/
│   ├── DeviceActor.js          # Un téléphone simulé (state machine)
│   ├── MessageBus.js           # Bus pub/sub central
│   ├── VirtualClock.js         # Horloge simulée
│   ├── TrackerActor.js         # Simulation du tracker
│   ├── RelayActor.js           # Simulation du relai HA
│   ├── scenarios/
│   │   ├── s1_convergence.js
│   │   ├── s2_churn.js
│   │   ├── s3_wave_join.js
│   │   ├── s4_failure_cascade.js
│   │   └── s5_mobility.js
│   └── metrics/
│       ├── Collector.js        # Capture des métriques live
│       └── Plotter.js          # Export CSV pour matplotlib
├── results/
│   ├── s1_metrics.csv
│   ├── s1_cluster_sizes.png
│   └── ...
└── README.md
```

---

## Stratégie de priorisation

Si tu manques de temps :

### Priorité 1 (obligatoire pour la soutenance)

- Test 1 — Intra-cluster
- Test 2 — Inter-cluster
- Test 3 — Mort du Super-Pair

→ Sans ces 3 tests, la démo IRL ne tient pas debout.

### Priorité 2 (différenciant fort)

- Test 5 — Simulation 1 000

→ Sans la simulation, le mémoire reste sur du "5 téléphones, ça marche". Avec, tu deviens crédible jusqu'à 1 000+.

### Priorité 3 (bonus impressionnant)

- Test 4 — Migration de nœud

→ Si temps disponible après les 4 autres.

---

## Conseil stratégique pour la soutenance

Le jury va surtout regarder **2 choses** :

1. **Que la démo IRL fonctionne** sans planter → Tests 1, 2, 3 sont **obligatoires**, 4 est optionnel.
2. **Que tu aies des chiffres** sur le passage à l'échelle → Test 5 est **le facteur différenciant**.

> **Recommandation finale** : prioriser **1, 2, 3, 5** dans cet ordre. Le 4 est un bonus qui impressionne, mais le 5 est ce qui fait passer ton PFE de "projet d'étudiant" à "preuve de concept industrielle".

---

*Document rédigé le 2026-05-11 pour MobiCloud (PFE Naoui).*
