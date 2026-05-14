# Module 11 : Cluster & Protocol JOIN Explicite
**Scan exhaustif — Date: 13 mai 2026**

## Statut

⚠️ **Module 11 est NOUVELLEMENT IMPLÉMENTÉ et PAS DOCUMENTÉ en théorique**

Ce module représente **~40 pages de code Kotlin** ajoutées après la documentation théorique formelle. C'est le résultat de l'évolution MVP (minimum viable product) qui a simplifié l'élection distribuée en cluster explicite (lieu géographique + GPS proximity + heartbeat 30s).

**Découvert par** : Scan exhaustif du code source en mai 2026.

---

## Architecture cluster

### Concept

**Cluster** = groupe limité de nœuds (max 50) colocalisés géographiquement (GPS proximity <5 km) partageant :
- Un **clusterId** (UUID v4)
- Un **Super-Pair** élu (Bully algorithm)
- Un **Member registry** (Room DB)
- Un **Heartbeat 30s** intra-cluster

### Topologie

```
┌─ Cluster A (UUID-A) ─────────────┐
│  Super-Pair A (électeur)         │
│  ├─ Member 1 (nœud Android)      │
│  ├─ Member 2 (nœud Android)      │
│  └─ Member 3 (nœud Android)      │
│                                   │
│  Heartbeat: 30s (SP → members)    │
│  Timeout: 90s (3 manqués = mort)  │
└──────────────────────────────────┘

        Relai WebSocket (WS 10000)
        ↑↓ REGISTER_PEER + SIGNAL

┌─ Cluster B (UUID-B) ─────────────┐
│  Super-Pair B (électeur)         │
│  ├─ Member 4                     │
│  └─ Member 5                     │
└──────────────────────────────────┘
```

---

## Fichiers implémentation

| Fichier | Lignes | Responsabilité |
|---------|--------|-----------------|
| **ClusterConstants.kt** | 30 | Constantes (HEARTBEAT_INTERVAL_MS, MAX_CLUSTER_SIZE, etc.) |
| **JoinStateMMachine.kt** | TBD | FSM : Isolated → Candidate → SuperPair / Member |
| **MemberRegistry.kt** | TBD | Interface membre registry |
| **RoomMemberRegistry.kt** | TBD | Impl. Room (persistence crash) |
| **ProcessJoinRequestUseCase.kt** | 146 | Traiter JOIN_REQUEST (SP côté) |
| **SendJoinRequestUseCase.kt** | TBD | Envoyer JOIN_REQUEST (candidate côté) |
| **MemberHeartbeatUseCase.kt** | TBD | Envoie heartbeat 30s |
| **ProcessHeartbeatUseCase.kt** | TBD | Traite heartbeat reçu |
| **MonitorMemberLivenessUseCase.kt** | TBD | Scan liveness, éviction |
| **MemberSnapshotCacheUseCase.kt** | TBD | Snapshot DB pour survie crash |
| **ProcessMemberUpdateUseCase.kt** | TBD | Traite MEMBER_UPDATE gossip |
| **SendMemberUpdateUseCase.kt** | TBD | Diffuse MEMBER_UPDATE |
| **BullySoloElectionUseCase.kt** | TBD | Élection solo si isolé 20s |
| **MarkSelfAsSuperPairUseCase.kt** | TBD | Marque self Super-Pair |

---

## Constants

```kotlin
// app/src/main/kotlin/com/mobicloud/domain/models/m11_join/ClusterConstants.kt

// Plafond batterie côté SP + critère unique d'admission (Story 12.1)
const val MAX_CLUSTER_SIZE = 50

// Bump de version protocole JOIN suite au retrait GPS du payload signé (Story 12.1).
const val JOIN_PROTOCOL_VERSION = 2

// Compromis batterie vs détection mort : 30 s = 2 cycles radio min, détection décès ≤ 2 min
// (3 heartbeats manqués = SP_TIMEOUT_MS = 90 s) sans surcharger la radio 4G en permanence.
const val HEARTBEAT_INTERVAL_MS = 30_000L

// 3 heartbeats manqués = mort réelle du membre. Anti-flap 4G↔WiFi : le handover
// peut couper 10-20 s sans que le nœud soit réellement mort — 90 s absorbe 2 handovers.
const val SP_TIMEOUT_MS = 90_000L

// NFR-08 : admission ≤ 5 s end-to-end via relai HA (RTT 4G ≈ 100 ms, traitement SP ≈ 10 ms,
// 2 allers-retours = 420 ms ; 5 s laisse 10× la marge pour les pics réseau transitoires).
const val JOIN_REQUEST_TIMEOUT_MS = 5_000L

// Anti-cascade auto-élection en flap réseau transitoire : 20 s d'isolement garantit
// qu'une coupure 4G passagère (reconnexion ≤ 10 s) ne déclenche pas une Bully solo
// et un nouveau cluster orphelin. Inférieur à SP_TIMEOUT_MS (90 s) pour converger vite.
const val ISOLATION_BACKOFF_MS = 20_000L

// 15s = 1/6 de SP_TIMEOUT_MS — granularité d'éviction acceptable (max 105s détection mort réelle),
// et 4× moins de scans que toutes les 5s.
const val LIVENESS_CHECK_INTERVAL_MS = 15_000L

// Versioning protocole
const val BULLY_TIMESTAMP_WINDOW_MS = 30_000L  // Anti-replay fenêtre ±30s
const val MAX_EXPECTED_MEMBERS = MAX_CLUSTER_SIZE + 10  // Threshold warn logs
```

---

## State Machine

### États

```
    ┌─ Undiscovered (démarrage)
    │  - Pas d'info cluster
    │  - Lance GPS discovery
    │
    ├─ Isolated (20s sans pair actif)
    │  - Pas de pair GPS-local détecté
    │  - ISOLATION_BACKOFF_MS = 20s avant auto-élection solo
    │
    ├─ Candidate (découverte ou après Bully)
    │  - Participe à élection Bully
    │  - Attend ELECTION_BROADCAST des pairs
    │
    ├─ SuperPair (élu Bully)
    │  - Accepte JOIN_REQUEST
    │  - Orchestre heartbeat, liveness check
    │  - Gère member registry
    │
    └─ Member (accepté par SuperPair)
       - Envoie heartbeat SP 30s
       - Écoute MEMBER_UPDATE gossip
       - Prêt à contribuer stockage (m08-m09)
```

### Transitions

| Depuis | Vers | Déclencheur |
|--------|------|-------------|
| **Undiscovered** | **Candidate** | GPS discovery réussie (pair local détecté) |
| **Undiscovered** | **Isolated** | Timeout GPS (→ adoption Isolated) |
| **Isolated** | **SuperPair** | ISOLATION_BACKOFF_MS écoulé → BullySolo |
| **Isolated** | **Candidate** | GPS découverte tard (pair rejoint) |
| **Candidate** | **SuperPair** | Gagner Bully election |
| **Candidate** | **Member** | Recevoir JoinAccept |
| **SuperPair** | **Candidate** | SP_TIMEOUT_MS (crash Super-Pair) → Bully relancée |
| **Member** | **Candidate** | Recevoir JoinRedirect → cherche autre SP |

---

## Protocole JOIN (Client-side : Candidate)

### 1️⃣ Découverte Super-Pair

**Trigger** : Candidate détecte clusters voisins via GET_PEERS relai

```kotlin
// Fetch annuaire du relai
val peers = signalingRepository.fetchActiveSuperPeerHints()
  .filter { it.isSuperPair }
  .filter { it.currentMemberCount < MAX_CLUSTER_SIZE }  // Pas saturé
  .sortedBy { it.currentMemberCount }  // Charge la plus faible d'abord
  .take(3)  // Top 3 moins chargés
```

### 2️⃣ JOIN_REQUEST

**Envoi** : `SendJoinRequestUseCase.kt`

```kotlin
// Créer demande JOIN
val joinRequest = JoinRequest(
  senderNodeId: String,           // 16 hex chars (nodeId)
  candidatePublicKey: ByteArray,  // EC P-256 public key
  freeBytes: Long,                // Espace disque libre
  reliabilityScore: Float,        // Score fiabilité 0-1
  timestampMs: Long,              // Unix ms (anti-replay)
  signatureBytes: ByteArray       // EC P-256 signature
)

// Payload signé : joinRequestSignedBytes(senderNodeId, pubkey, freeBytes, score, timestamp)
```

**Timeout** : JOIN_REQUEST_TIMEOUT_MS = 5 000 ms

---

### 3️⃣ JOIN_ACCEPT (Serveur-side : Super-Pair)

**Traitement** : `ProcessJoinRequestUseCase.kt` (146 lignes)

**Branche 0 : Guard d'état**
```kotlin
if (currentState !is NodeJoinState.SuperPair) {
  return signedRedirect(JoinRedirectReason.INVALID_STATE, alts)
}
```

**Branche 1 : Vérification signature EC P-256**
```kotlin
val signedBytes = joinRequestSignedBytes(
  senderNodeId, candidatePublicKey, freeBytes, reliabilityScore, timestampMs
)
val sigValid = securityRepository.verifySignature(
  data = signedBytes,
  signature = request.signatureBytes,
  publicKey = request.candidatePublicKey
)
if (!sigValid) return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, emptyList())
```

**Branche 2 : Fenêtre anti-replay**
```kotlin
val skewMs = abs(now - request.timestampMs)
if (skewMs > BULLY_TIMESTAMP_WINDOW_MS) {  // ±30s
  return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, emptyList())
}
```

**Branche 3+4 : Check-and-add atomique**
```kotlin
// ⚠️ CRITIQUE : without atomicity, 2 JOINs concurrents → cluster > MAX
val newMember = MemberInfo(
  nodeId = request.senderNodeId,
  publicKey = request.candidatePublicKey,
  ipAddress = "",
  port = 0,
  freeBytes = request.freeBytes,
  role = MemberRole.MEMBER
)
val admitted = memberRegistry.addIfBelowCapacity(newMember, MAX_CLUSTER_SIZE)
if (!admitted) {
  return signedRedirect(JoinRedirectReason.CLUSTER_FULL, alts)
}
```

**Réponse JoinAccept** :
```kotlin
// Snapshot member registry signé
val acceptTimestampMs = System.currentTimeMillis()
val snapshot = memberRegistry.list()
val acceptSignedBytes = joinAcceptSignedBytes(clusterId, selfNodeId, acceptTimestampMs, snapshot)
val acceptSignature = securityRepository.signData(acceptSignedBytes)

return JoinResponse.JoinAccept(
  clusterId,
  superPairNodeId: ByteArray,
  memberSnapshot: List<MemberInfo>,
  timestampMs,
  signatureBytes
)
```

### 4️⃣ JOIN_REDIRECT (Refus)

**Raisons refus** :
```kotlin
enum class JoinRedirectReason {
  INVALID_STATE,       // Super-Pair pas actif
  INVALID_SIGNATURE,   // Signature EC P-256 invalide ou replay
  CLUSTER_FULL         // 50 membres atteint
}
```

**Payload** :
```kotlin
data class JoinResponse.JoinRedirect(
  val reason: JoinRedirectReason,
  val alternativeSuperPeers: List<SuperPeerHint>,  // Top 3 moins chargés
  val timestampMs: Long,
  val signatureBytes: ByteArray
)
```

**Alternatives** : Filtre super-peers
```kotlin
signalingRepository.fetchActiveSuperPeerHints()
  .filter { it.currentMemberCount < MAX_CLUSTER_SIZE }  // Pas saturés
  .sortedWith(compareBy<SuperPeerHint> 
    { it.currentMemberCount }  // Charge min d'abord
    .thenBy { it.nodeId.toHexShort() })  // Bris d'égalité déterministe
  .take(3)  // Top 3
```

---

## Member Registry (Room Database)

### Schéma

```sql
CREATE TABLE cluster_members (
  nodeId TEXT PRIMARY KEY,
  clusterId TEXT NOT NULL,
  publicKey BLOB NOT NULL,
  ipAddress TEXT DEFAULT '',
  port INTEGER DEFAULT 0,
  freeBytes INTEGER DEFAULT 0,
  role TEXT DEFAULT 'MEMBER',  -- MEMBER | SUPER_PAIR
  joinedAt INTEGER NOT NULL,
  lastHeartbeat INTEGER NOT NULL,
  FOREIGN KEY (clusterId) REFERENCES clusters(clusterId)
);

CREATE TABLE clusters (
  clusterId TEXT PRIMARY KEY,
  superPairNodeId TEXT NOT NULL,
  createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL
);
```

### Interface

```kotlin
interface MemberRegistry {
  suspend fun add(member: MemberInfo): Boolean
  suspend fun addIfBelowCapacity(member: MemberInfo, max: Int): Boolean
  suspend fun remove(nodeId: String, clusterId: String): Boolean
  suspend fun updateHeartbeat(nodeId: String): Boolean
  suspend fun list(): List<MemberInfo>
  suspend fun listStale(beforeTimestamp: Long): List<MemberInfo>
  fun size(): Int
}
```

### Snapshot (survie crash)

**MEMBER_UPDATE message** (gossip) :
```kotlin
// À chaque modification registry (add/remove/heartbeat)
// Super-Pair envoie snapshot complet signé

data class MemberUpdatePayload(
  val clusterId: String,
  val timestamp: Long,
  val memberList: List<MemberInfo>,
  val superPairNodeId: ByteArray,
  val signature: ByteArray
)
```

**Survivance crash** :
```kotlin
// Nœud redémarre → charge snapshot Room
// Snapshot signé garantit intégrité (pas de corruption file system)
val snapshot = memberRegistry.restoreFromDb()
// Continue cluster membership même après crash
```

---

## Heartbeat Intra-cluster (30s fixe)

### Super-Pair Heartbeat (Outbound)

**Trigger** : `MemberHeartbeatUseCase.kt` — timer 30s récurrent

```kotlin
class MemberHeartbeatUseCase {
  suspend fun sendHeartbeat(): Result<Unit> {
    // À chaque HEARTBEAT_INTERVAL_MS = 30s
    val members = memberRegistry.list()
    for (member in members) {
      // Envoyer heartbeat direct TCP ou via relai
      networkClient.sendHeartbeat(member.nodeId, timestamp = now())
    }
  }
}
```

### Member Heartbeat (Inbound)

**Trigger** : `ProcessHeartbeatUseCase.kt` — reçoit heartbeat SP

```kotlin
suspend fun onHeartbeatReceived(sp: SuperPairNodeId, timestamp: Long) {
  memberRegistry.updateHeartbeat(sp)
  lastSuperPairSeen = timestamp
}
```

### Timeout (90s = 3 manqués)

```kotlin
// Si pas de heartbeat reçu depuis SP_TIMEOUT_MS = 90s
// → Considérer Super-Pair mort
// → Lancer élection Bully (RunBullyElectionUseCase)

class MonitorMemberLivenessUseCase {
  suspend fun checkLiveness() {
    val now = System.currentTimeMillis()
    val stale = memberRegistry.listStale(now - SP_TIMEOUT_MS)
    for (member in stale) {
      memberRegistry.remove(member.nodeId, clusterId)
      // Push event notification
    }
  }
}
```

---

## Anti-cascade : ISOLATION_BACKOFF_MS = 20s

### Problème

Si réseau 4G bascule vers WiFi (ou vice-versa) :
- 5-10s de latence/drop
- Nœud se croit isolé
- Déclenche BullySolo → nouveau cluster orphelin
- Cascade : 3 nœuds = 3 mini-clusters au lieu de 1

### Solution

**Attendre 20s d'isolement vrai avant BullySolo** :

```kotlin
class JoinStateMachine {
  var isolationStart: Long? = null
  
  fun onIsolated() {
    if (isolationStart == null) {
      isolationStart = now()
    }
  }
  
  suspend fun checkIsolationBackoff() {
    if (isolationStart != null && now() - isolationStart >= ISOLATION_BACKOFF_MS) {
      // Vraiment isolé 20s → BullySolo
      bullySoloElectionUseCase()
      isolationStart = null
    }
  }
}
```

ISOLATION_BACKOFF_MS = 20s < SP_TIMEOUT_MS = 90s
→ Élection solo converge vite mais tolère handover transitoire

---

## Story 12.1 : GPS retiré du payload

### Changement protocole

**Join Protocol Version 1** (ancien) :
```
Payload signé : joinRequestSignedBytes(
  senderNodeId, 
  candidatePublicKey, 
  freeBytes, 
  reliabilityScore, 
  gpsLatitude,      // ← Retiré
  gpsLongitude,     // ← Retiré
  timestamp
)
```

**Join Protocol Version 2** (actuel, Story 12.1) :
```
Payload signé : joinRequestSignedBytes(
  senderNodeId, 
  candidatePublicKey, 
  freeBytes, 
  reliabilityScore, 
  timestamp         // ← Seuls 5 champs
)
```

### Raison

GPS pas fiable/disponible MVP → complexité sans gain (proximit détectée via relai cluster clustering)

### Impact

- Signature différente → bumped JOIN_PROTOCOL_VERSION = 2
- ClusterId attribué par JoinAccept ou BullySolo (pas dérivé SSID)
- Nœud génère UUID aléatoire si première élection

---

## Summary

| Aspect | Détail |
|--------|--------|
| **Scope** | Cluster local ≤50 nœuds, <5 km GPS |
| **Électeur** | Bully algorithm (SF compare) |
| **Heartbeat** | 30s fixe, timeout 90s (3 manqués) |
| **Persistence** | Room DB snapshot MEMBER_UPDATE |
| **Isolation** | 20s backoff avant BullySolo (anti-cascade) |
| **Admission** | JOIN_REQUEST→JoinAccept (signé EC P-256) |
| **MaxSize** | 50 membres + cap serveur 50 super-peers |
| **Protocol** | v2 (sans GPS), signature stricte ±30s |

---

**Document généré**: 13 mai 2026 — Scan exhaustif m11_join/
**Validité**: Module production dans app mvp
