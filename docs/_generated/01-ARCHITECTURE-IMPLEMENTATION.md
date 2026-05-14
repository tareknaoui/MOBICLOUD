# Architecture et Implémentation MobiCloud
**Scan exhaustif du code source — Date: 13 mai 2026**

## Table des matières
1. [Vue d'ensemble du projet](#vue-densemble-du-projet)
2. [Structure multi-parties](#structure-multi-parties)
3. [Stack technologique réel](#stack-technologique-réel)
4. [11 Modules implémentés (m01-m11)](#11-modules-implémentés-m01-m11)
5. [Protocole relais WebSocket](#protocole-relais-websocket)
6. [Paramètres et métriques réelles](#paramètres-et-métriques-réelles)
7. [Architecture réseau](#architecture-réseau)
8. [Propriétés de sécurité implémentées](#propriétés-de-sécurité-implémentées)

---

## Vue d'ensemble du projet

**MobiCloud** est une plateforme de **stockage P2P distribué pour clusters mobiles** composée de :

- **App Android** (Kotlin, Jetpack Compose) : client mobile avec 11 modules de domaine
- **Relay Node.js** : serveur central WebSocket pour signalisation inter-cluster et relai de blocs
- **Modules de domaine** : logique métier organisée par fonctionnalité (auth, élection, DHT, réparation, etc.)

**Classification**: Monorepo multi-part mobile+backend (app Android + relay Node.js)

---

## Structure multi-parties

```
MOBICLOUD/
├── app/                          # Module Android principal
│   ├── src/main/kotlin/com/mobicloud/
│   │   ├── domain/usecase/       # 11 modules (m01-m11)
│   │   ├── data/network/service/ # Service P2P + clients réseau
│   │   ├── data/p2p/             # Protocole P2P (WebSocket, TCP, TCP relay)
│   │   └── ui/                   # Jetpack Compose UI
│   └── src/
├── relay-server/                 # Serveur WebSocket Node.js
│   └── server.js                 # ~740 lignes — authentification, signalisation, relai blocs
├── core/                         # Modules partagés
│   ├── network/                  # Retrofit, OkHttp
│   ├── room/                     # Room Database
│   ├── ui/                       # Composants partagés
│   └── preferences/              # DataStore
├── data/                         # Modules données
│   └── src/main/kotlin/...
├── feature/                      # Modules features (auth, home, profile, settings)
└── docs/                         # Documentation

**Sépération claire** : Application (business logic + UI) vs. Relai (signalisation + buffering blocs)
```

---

## Stack technologique réel

### Android App (Kotlin)

| Composant | Technologie | Détails |
|-----------|-------------|---------|
| **UI Framework** | Jetpack Compose | Material3 design system |
| **Architecture** | MVVM + Clean Architecture | Séparation domaine/data/UI |
| **DI** | Dagger Hilt | Scope ApplicationScope pour singletons |
| **Async** | Kotlin Coroutines + Flow | withContext(Dispatchers.IO/Default) |
| **Stockage local** | Room Database | Schéma CRDT avec LWW (Last-Write-Wins) |
| **Preferences** | DataStore | SharedPreferences moderne |
| **Networking** | Retrofit + OkHttp | TCP pour P2P direct, WebSocket pour relai |
| **Build** | Gradle 9.4.1 | Convention plugins (build-logic/) |
| **Formatage** | Spotless + ktlint | Pre-commit hook |
| **Testing** | JUnit4, Mockk | Instrumentation tests |

### Relay Backend (Node.js)

| Composant | Technologie | Détails |
|-----------|-------------|---------|
| **Runtime** | Node.js | Port 10000 (configurable ENV) |
| **WebSocket** | ws (npm) | WebSocketServer + HTTP health endpoint |
| **Crypto** | Node.js crypto built-in | EC P-256 (prime256v1) signatures, SHA-256 |
| **Framing** | Custom binary protocol | 1 byte type + 4 bytes LE length + payload |
| **Storage** | In-RAM only | Pas de persistance disque |
| **Graceful shutdown** | SIGTERM handler | Fermeture 5s timeout |

---

## 11 Modules implémentés (m01-m11)

### **Module 01 : Auth & Discovery**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m01_auth_discovery/`
**Fichiers clés**: `GenerateHashcashProofUseCase.kt` (inutilisé), `CalculateReliabilityScoreUseCase.kt`

**Implémentation**:
- ✅ **Authentification EC P-256** : Android Keystore signing (hardware-backed)
  - Payload signé : `MobiCloud-HA-AUTH:nodeId(16 hex chars):timestamp_ms`
  - Fenêtre anti-replay : ±30 000 ms (AUTH_WINDOW_MS)
  - Vérification : Node.js `/relay-server/server.js` lines 82-136
  
- ❌ **Hashcash PoW** : fichier existe (`GenerateHashcashProofUseCase.kt`) mais NON implémenté
  - Doc promet 1 sec/identité, code ne l'appelle jamais
  
- ✅ **Score de fiabilité** : `CalculateReliabilityScoreUseCase.kt`
  - Formule simplifiée : `score = provider.getScore().coerceIn(0f, 1f)`
  - Pas de MIN(matériel, réseau), pas de lissage exponentiel (α=0.7), pas d'accéléromètre

**Opcodes relais** :
- `0x01` AUTH — signature EC P-256 JSON
- `0x02` AUTH_OK — réponse succès authentification

---

### **Module 03-04 : Gossip & Heartbeat**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/`

**Implémentation**:
- ✅ **DHT Gossip CRDT** : `GossipSyncUseCase.kt` (singleton)
  - Flood type: **FAN_OUT = 2** voisins aléatoires (pas 3, pas épidémique O(log N))
  - Bloom filter 128 bits pour delta-sync
  - Hardening : Rejet si sender non-pair connu
  - DELTA_REQUEST_TIMEOUT_MS = 3000 ms

- ✅ **Heartbeat 30s constant** : `ClusterConstants.kt`
  ```kotlin
  const val HEARTBEAT_INTERVAL_MS = 30_000L      // 30s, fixe
  const val SP_TIMEOUT_MS = 90_000L              // 3 manqués = mort (90s)
  const val LIVENESS_CHECK_INTERVAL_MS = 15_000L // check toutes 15s
  ```

- ❌ **Pas de cycle adaptatif** : heartbeat constant, pas de court/long cycle

**Opcodes relais** :
- `0x0E` SIGNAL — forwarding Gossip DHT unicast (relay-only)
- `0x0F` SIGNAL_RECEIVED — reçu côté dest

---

### **Module 05 : DHT Catalog**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/`

**Implémentation**:
- ✅ **Consistent Hash Ring** : `ConsistentHashRing.kt`
  - Anneau local pour responsabilité fragments
  
- ✅ **CRDT LWW basique** : `ResolveDhtConflictUseCase.kt`
  - Last-Write-Wins : timestamp résout conflits
  - Structure DB Room : blockId, nodeId, ipAddress, port, timestamp

- ❌ **Pas de DHT partitionné Chord** : pas d'anneau O(log N), pas de [ID_DHT, ID_Successeur[
- ❌ **Gossip centralisé relais** : via SIGNAL 0x0E unicast, pas épidémique 3-voisins

---

### **Module 06-07 : Auto-Repair & Migration**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/`

**Implémentation**:
- ✅ **Détection panne heartbeat** : `MonitorMemberLivenessUseCase.kt`
  - SP_TIMEOUT_MS = 90s (3 manqués)
  - Trigger si aucun heartbeat reçu
  
- ✅ **Orchestration réparation** : `OrchestrateBlockMigrationUseCase.kt`
  - Super-Pair orchestre redistribution
  - K fragments → décodage → redistribution à N nouveaux nœuds
  
- ✅ **Départ signé** : `SendDepartureNoticeUseCase.kt`
  - Testament : gossip prioritaire des blocs orphelins

- ❌ **Pas de "Nœud Médecin" spécialisé** : logique distribuée
- ❌ **Pas de Triage Médical** : pas de "Marge de Survie = survivants − K"

---

### **Module 08 : Stockage & Hosting**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_hosting/`

**Implémentation**:
- ✅ **Hébergement de fragments** : `ReceiveAndHostBlockUseCase.kt`
- ✅ **Réponse à requêtes** : `RespondToBlockRequestUseCase.kt`
  - TCP serveur intra-cluster

---

### **Module 08-09 : Erasure Coding & Distribution**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/`

**Implémentation**:
- ✅ **Reed-Solomon k=4, n=2 (fixe)** : `EncodeErasureFragmentsUseCase.kt`
  ```kotlin
  data class ErasureParameters(
      val k: Int = 4,          // 4 blocs données
      val n: Int = 2,          // 2 blocs parité → tolérance 2 pertes
      val blockSize: Int = 1_048_576  // 1 MB
  )
  ```
  - Implémentation : GF(256) Reed-Solomon (OpenFEC ou libraid)
  
- ❌ **Pas d'Erasure Coding adaptatif** :
  - Doc promet K+2 à K+8 selon médiane/écart-type SF
  - Code : k=4, n=2 **codé en dur** (ligne 15, ErasureParameters.kt)

- ✅ **Distribution intelligente** : `SelectOptimalPeersUseCase.kt`
  - Anti-corrélation ✅
  - Seuil SF minimum ✅
  - ❌ Pas de Groupes de Proximité (70% voisins communs)
  - ❌ Pas de Score_Candidature pondéré par taux d'occupation
  - ✅ Parallélisme max 3 canaux

- ✅ **Téléchargement compétitif** : `DownloadFileBlocksUseCase.kt` + `BlockDownloadClient.kt`
  - Fenêtres glissantes K fragments
  - ❌ Pas de K+2 en mode dégradé

**Opcodes relais** :
- `0x0C` REQUEST_BLOCK — pull inter-cluster (16 bytes nodeId + 64 bytes blockId)
- `0x0D` REQUEST_BLOCK_FORWARDED — relay forward sans buffering
- `0x06` UPLOAD — push bloc via relai (store-and-forward)
- `0x07` FORWARD — forward immédiat ou depuis buffer

---

### **Module 10 : Élection Bully**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/`

**Implémentation**:
- ✅ **Algorithme Bully** : `RunBullyElectionUseCase.kt`
  - Compare SF : `otherScore > localScore` → ALIVE (abandon)
  - Bris d'égalité : `otherId > localId` (lexicographique)
  - MONITORING_WINDOW_MS = 20 000 ms (attente 20s avant élection si aucun Super-Pair)
  - Timeout élection = 3000 ms (3s)

- ❌ **Pas de bonus hystérésis** : Doc promet 15%, code : simple `otherScore > localScore`
- ❌ **Pas de mandat 30 min** : Super-Pair reste jusqu'à départ/crash
- ❌ **Pas de vote de destitution** : pas de mécanisme majorité voisins

**Opcodes relais** :
- `0x10` ELECTION_BROADCAST — broadcast ELECTION/ALIVE/COORDINATOR
- `0x11` ELECTION_RECEIVED — reçu côté dest

---

### **Module 11 : Cluster & JOIN Explicite** ← **NOUVEAU (pas en doc théorique)**
**Chemin**: `app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/`
**Statut**: Module 11 entièrement nouvellement implémenté, **pas documenté en théorique**.

**Structure cluster**:
```kotlin
// Constants (ClusterConstants.kt)
const val MAX_CLUSTER_SIZE = 50              // Plafond membres
const val HEARTBEAT_INTERVAL_MS = 30_000L    // 30s
const val SP_TIMEOUT_MS = 90_000L            // 3 manqués = mort (90s)
const val JOIN_REQUEST_TIMEOUT_MS = 5_000L   // Admission ≤5s
const val ISOLATION_BACKOFF_MS = 20_000L     // Anti-cascade (20s isolement)
const val LIVENESS_CHECK_INTERVAL_MS = 15_000L // Check 15s
```

**Implémentation**:
- ✅ **State Machine** : `JoinStateMachine.kt`
  - États: `Isolated` → `Candidate` → `SuperPair` / `Member`
  - Transitions par GPS proximity (<5 km), élection Bully

- ✅ **JOIN Protocol** : `ProcessJoinRequestUseCase.kt`
  - Branche 0 : Seul Super-Pair traite (guard d'état)
  - Branche 1 : Vérif signature EC P-256
  - Branche 2 : Fenêtre anti-replay ±30s (BULLY_TIMESTAMP_WINDOW_MS)
  - Branche 3+4 : Check-and-add atomique `addIfBelowCapacity(MAX_CLUSTER_SIZE)`
  - Réponse : `JoinAccept` (clusterId, memberSnapshot) ou `JoinRedirect` (alts)

- ✅ **Member Registry** : `MemberRegistry.kt` + `RoomMemberRegistry.kt`
  - Room DB stockage persistence crash
  - Snapshot MEMBER_UPDATE diffusé à tous members

- ✅ **Heartbeat Member** : `MemberHeartbeatUseCase.kt` + `MemberHeartbeatSenderImpl.kt`
  - Envoie heartbeat 30s
  - Timeout 90s (3 manqués) → éviction

- ✅ **Monitor Liveness** : `MonitorMemberLivenessUseCase.kt`
  - Scan LIVENESS_CHECK_INTERVAL_MS = 15s
  - Évict si `now - lastSeen > SP_TIMEOUT_MS`

**Opcodes relais** :
- `0x0B` JOIN — présence simple participant (isSuperPair=false)
- `0x03` REGISTER_PEER — déclaration Super-Pair (isSuperPair=true)

---

## Protocole relais WebSocket

### Framing binaire

```
[Type: 1 byte] [Length: 4 bytes LE] [Payload: Length bytes]

Type codes (RelayMsg.kt + server.js):
  0x01  AUTH           — Authentification EC P-256
  0x02  AUTH_OK        — Succès auth
  0x03  REGISTER_PEER  — Déclaration Super-Pair
  0x04  GET_PEERS      — Demande annuaire
  0x05  PEERS          — Réponse annuaire
  0x06  UPLOAD         — Push bloc via relai
  0x07  FORWARD        — Forward bloc
  0x08  ACK            — Acknowledgement
  0x09  PING           — Heartbeat protocol WebSocket
  0x0A  PONG           — Réponse PING
  0x0B  JOIN           — Présence participant simple
  0x0C  REQUEST_BLOCK  — Demande bloc inter-cluster
  0x0D  REQUEST_BLOCK_FORWARDED — Forward demande bloc
  0x0E  SIGNAL         — Gossip DHT unicast
  0x0F  SIGNAL_RECEIVED — Reçu Gossip
  0x10  ELECTION_BROADCAST — Bully election broadcast
  0x11  ELECTION_RECEIVED  — Reçu election
  0xFF  ERROR          — Erreur
```

### Authentification (AUTH 0x01)

**Client → Relay**:
```json
{
  "nodeId": "abcdef0123456789",        // 16 hex chars ASCII
  "pubKeySpkiDer": "MIIBIjANBg...",    // Base64 EC P-256 SPKI-DER
  "timestamp": 1715601234567,          // Unix ms
  "signature": "MEQCIHx+wO1..."        // Base64 EC P-256 signature
}
```

**Payload signé** : `MobiCloud-HA-AUTH:nodeId:timestamp` (UTF-8 bytes)
**Fenêtre anti-replay** : |now - timestamp| ≤ 30 000 ms
**Timeout** : AUTH_TIMEOUT_MS = 10 000 ms (ferme si pas AUTH dans 10s)

### Signalisation (REGISTER_PEER 0x03 / JOIN 0x0B)

**REGISTER_PEER** (Super-Pair élu) :
```json
{
  "ip": "192.168.1.10",
  "port": 5000,
  "reliabilityScore": 0.85,
  "electedAt": 1715601234567,
  "clusterId": "550e8400-e29b-41d4-a716-446655440000",  // UUID v4
  "freeBytes": 1073741824,                               // 1 GB
  "currentMemberCount": 12
}
```

**JOIN** (participant simple) :
- Idem mais `isSuperPair=false` en DB serveur
- Si déjà Super-Pair, statut conservé (pas de dégradation)

**Registry DB** (server.js) :
- TTL = 60 000 ms (auto-purge si pas re-register)
- Max 100 Super-Pairs enregistrés (MAX_SIGNALING_PEERS)
- Max 50 personnes/cluster (MAX_CLUSTER_SIZE_SERVER mirror app)

### Relay buffer (UPLOAD 0x06 → FORWARD 0x07)

**Payload UPLOAD** :
```
[destNodeId: 16 bytes UTF-8 NUL-padé]
[blockId: 64 bytes UTF-8 NUL-padé]
[data: N bytes chiffré AES-256 GCM]
```

**Comportement** :
1. Si destinataire connecté → FORWARD immédiat (fire-and-forget)
2. Sinon → BUFFERED en RAM (TTL 60s)
3. À reconnexion dest → `flushPendingBlocks()` → livraison depuis buffer
4. Anti-loop : dest ≠ source (case-insensitive)
5. Cap buffer : MAX_RELAY_BUFFER_ENTRIES = 500 blocs

### REQUEST_BLOCK pull inter-cluster (0x0C → 0x0D)

**Requester → Relai** :
```
[destNodeId: 16 bytes UTF-8 NUL-padé]
[blockId: 64 bytes UTF-8 hex]
```

**Relai → Dest** :
- Pivot 80 bytes (fromNodeId + blockId) via REQUEST_BLOCK_FORWARDED 0x0D
- **Pas de buffering** : si dest absent → `REQUEST_BLOCK destinataire injoignable` ERROR
- Requester time-out de toute façon

### Élection Bully broadcast (ELECTION_BROADCAST 0x10 → ELECTION_RECEIVED 0x11)

**Payload** :
```json
{
  "senderNodeId": "abcdef0123456789",
  "type": "ELECTION" | "ALIVE" | "COORDINATOR",
  "reliabilityScore": 0.75,
  "clusterId": "550e8400-e29b-41d4-a716-446655440000",
  "timestampMs": 1715601234567,
  "signatureBytes": "MEQCIHx+wO1..."  // Base64
}
```

**Comportement** :
- Forward à **tous connectés sauf émetteur**
- Fire-and-forget (pas de buffering)
- Type "ALIVE" → j'ai reçu ELECTION et je suis plus prioritaire

---

## Paramètres et métriques réelles

### Intervals & Timeouts

| Paramètre | Valeur | Lieu | Objectif |
|-----------|--------|------|----------|
| **HEARTBEAT_INTERVAL_MS** | 30 000 ms | ClusterConstants.kt | Ping membres cluster |
| **SP_TIMEOUT_MS** | 90 000 ms | ClusterConstants.kt | 3 manqués = mort (90s) |
| **LIVENESS_CHECK_INTERVAL_MS** | 15 000 ms | ClusterConstants.kt | Scan éviction |
| **MONITORING_WINDOW_MS** | 20 000 ms | RunBullyElectionUseCase.kt | Attente avant Bully |
| **ELECTION_TIMEOUT** | 3 000 ms | RunBullyElectionUseCase.kt | Réception ALIVE |
| **AUTH_WINDOW_MS** | 30 000 ms | relay-server/server.js | Anti-replay ±30s |
| **AUTH_TIMEOUT_MS** | 10 000 ms | relay-server/server.js | Ferme non-auth après 10s |
| **TTL_MS** (relay) | 60 000 ms | relay-server/server.js | Buffer blocs + annuaire |
| **JOIN_REQUEST_TIMEOUT_MS** | 5 000 ms | ClusterConstants.kt | Admission ≤5s |
| **ISOLATION_BACKOFF_MS** | 20 000 ms | ClusterConstants.kt | Anti-cascade auto-élection |
| **DELTA_REQUEST_TIMEOUT_MS** | 3 000 ms | GossipSyncUseCase.kt | Gossip sync 3s |
| **HEARTBEAT_INTERVAL_MS** (relai WS) | 30 000 ms | relay-server/server.js | Détecte sockets zombies |

### Erasure Coding

| Paramètre | Valeur | Détails |
|-----------|--------|---------|
| **k** (données) | 4 | 4 blocs données |
| **n** (parité) | 2 | 2 blocs parité |
| **Tolérance** | 2 pertes | K+N=6, K=4 → 2 manquants accepté |
| **Algorithme** | Reed-Solomon GF(256) | OpenFEC ou libraid |

### Cluster & Réseau

| Paramètre | Valeur | Détails |
|-----------|--------|---------|
| **MAX_CLUSTER_SIZE** | 50 | Plafond membres/cluster |
| **MAX_BLOCK_SIZE** (relai) | 1 100 000 bytes | 1.1 MB (~1 MB fragment + 100 KB header) |
| **MAX_RELAY_BUFFER_ENTRIES** | 500 | Blocs en attente en RAM |
| **MAX_SIGNALING_PEERS** | 100 | Super-Pairs enregistrés max |
| **FAN_OUT** (Gossip) | 2 | 2 voisins aléatoires par cycle |
| **JOIN_PROTOCOL_VERSION** | 2 | Story 12.1 : sans GPS en payload |

---

## Architecture réseau

### 3 Canaux de communication

**MobiCloud utilise 3 canaux distincts** (pas 1 canal unique) :

1. **Relai WebSocket** (signalisation + DHT + inter-cluster)
   - TCP 10000 (default, configurable)
   - Tous messages : AUTH, REGISTER, JOIN, SIGNAL, ELECTION, REQUEST_BLOCK
   - Store-and-forward blocs si dest absent

2. **TCP P2P intra-cluster direct**
   - Port dynamique (TCP server sur chaque nœud)
   - Transferts blocs K fragments
   - Gossip DHT local (optionnel)
   - Heartbeat direct (optionnel alternative relai)

3. **UDP Multicast local (futur)**
   - Découverte locale (pas implémenté MVP)
   - Annonce présence cluster (candidat)

**Architecture réelle** :
```
    ┌─ Nœud A ────────┬─ Relai (WS 10000) ───┬─ Nœud B
    │ m01_auth        │ (Node.js)            │ m01_auth
    │ m03_gossip      │ • AUTH, REGISTER     │ m03_gossip
    │ m10_election    │ • SIGNAL (Gossip DHT)│ m10_election
    └─ TCP 5000 ──────┼──────────────────────┼─ TCP 5001
      (blocs)         │ • REQUEST_BLOCK      │ (blocs)
                      │ • UPLOAD/FORWARD     │
                      │ • ELECTION_BROADCAST │
                      └──────────────────────┘
```

---

## Propriétés de sécurité implémentées

| Propriété | Implémentée | Mécanisme | Statut |
|-----------|-------------|-----------|--------|
| **Confidentialité** | ✅ | AES-256-GCM fragments | Complète |
| **Intégrité fragments** | ✅ | Signature EC P-256 | Complète |
| **Intégrité catalogue** | ✅ | CRDT LWW timestamped | Complète |
| **Authenticité nœuds** | ✅ | EC P-256 keystore hardware-backed | Complète |
| **Anti-Sybil** | ✅ | Signature Keystore (pas PoW Hashcash doc) | Simplifiée mais sécurisée |
| **Anti-Collusion (PoR)** | ❌ | Pas implémenté | Non présent |
| **Anti-Trou Noir** | ❌ | Pas implémenté | Non présent |
| **Réveil asynchrone** | ❌ | Pas implémenté | Non présent |
| **Hystérésis élection** | ❌ | Pas implémenté | Bully simple comparaison |
| **Mandat Super-Pair** | ❌ | Pas implémenté | Pas d'abdication planifiée |

---

## Résumé implémentation vs. documentation théorique

**Alignés** ✅ :
- Élection Bully (algo basique)
- Erasure Coding Reed-Solomon (k=4, n=2)
- Chiffrement AES-256-GCM
- Gossip DHT CRDT (LWW basique)
- Heartbeat & détection panne (30s fixe)
- Cluster JOIN explicite (module 11)
- Authentification EC P-256

**Simplifiés** ⚠️ :
- Score fiabilité (brut vs. multi-critères avec IA)
- Distribution fragments (sans optimisation taux occupation)
- Gossip (centralisé relais vs. épidémique)

**Non implémentés** ❌ :
- Erasure Coding adaptatif (K+2 à K+8)
- Preuve de Travail Hashcash
- Réveil asynchrone
- Module 9 Gouvernance (Karma, PoR)
- DHT partitionné Chord O(log N)
- Hystérésis élection 15%
- Mandat Super-Pair 30 min

**Codes**: 28 écarts identifiés en détail dans `RAPPORT_ALIGNEMENT_CODE_DOCS.md`

---

**Document généré**: 13 mai 2026 — Scan exhaustif code source
**Validité**: Snapshot production à date
