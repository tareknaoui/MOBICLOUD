# Détails d'Implémentation Exacts
**Scan exhaustif — Date: 13 mai 2026**

## Extraits de code clés (source de vérité)

Cette section contient les **faits d'implémentation EXACTS** extraits ligne par ligne du code source. Chaque détail est localisable via chemins fichiers fournis.

---

## Module 01 : Authentification

### Nodeplate Format

**Fichier**: `RelayMsg.kt` + `relay-server/server.js`

```
nodeId: 16 hexadecimal ASCII characters
Format: [0-9a-fA-F]{16}
Example: "abcdef0123456789"
```

**Validation serveur** (relay-server/server.js ligne 92):
```javascript
const UUID_V4_RE = /^[0-9a-fA-F]{16}$/;
if (typeof nodeId !== 'string' || !UUID_V4_RE.test(nodeId)) {
  return { ok: false, reason: 'nodeId invalide (attendu 16 chars hex ASCII)' };
}
```

### EC P-256 Signature

**Payload signé** (relay-server/server.js ligne 105):
```javascript
const signedData = Buffer.from(
  `MobiCloud-HA-AUTH:${nodeId}:${timestamp}`, 
  'utf8'
);
```

**Schéma complet**:
- Texte: `MobiCloud-HA-AUTH:` + 16 hex nodeId + `:` + Unix ms timestamp
- Chiffré: EC P-256 (prime256v1) ECDSA SHA-256
- Encoded: Base64 pour transport JSON

**Exemple**:
```
Payload JSON sent:
{
  "nodeId": "abcdef0123456789",
  "pubKeySpkiDer": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AM...",  // Base64
  "timestamp": 1715601234567,
  "signature": "MEQCIHx+wO1kR2Z7..."  // Base64 EC signature
}

Server verifies:
  plaintext = "MobiCloud-HA-AUTH:abcdef0123456789:1715601234567"
  verify(SHA256(plaintext), pubKey, signature) == true
```

### Fenêtre Anti-replay

**Intervalle**: ±30 000 ms (AUTH_WINDOW_MS)

**Code** (relay-server/server.js lignes 96-102):
```javascript
const AUTH_WINDOW_MS = 30_000;
const ts = Number(timestamp);
if (!Number.isFinite(ts) || ts <= 0) {
  return { ok: false, reason: 'AUTH timestamp invalide' };
}
const skew = Math.abs(Date.now() - ts);
if (skew > AUTH_WINDOW_MS) {
  return { ok: false, reason: `AUTH timestamp hors fenêtre (écart ${skew}ms)` };
}
```

**Implication**: Requête AUTH plus vieille que 30s = rejeté (protection replay attack)

---

## Module 03-04 : Gossip & Heartbeat

### Heartbeat Interval (Fixe)

**Valeur**: 30 000 ms (30 secondes)

**Fichier**: `ClusterConstants.kt` ligne 12
```kotlin
const val HEARTBEAT_INTERVAL_MS = 30_000L
```

**Usage** (MemberHeartbeatUseCase.kt) :
- Super-Pair envoie heartbeat à chaque member
- Member reçoit et met à jour `lastSeen = now()`

### Timeout Super-Pair

**Valeur**: 90 000 ms (90 secondes)

**Fichier**: `ClusterConstants.kt` ligne 16
```kotlin
const val SP_TIMEOUT_MS = 90_000L
```

**Signification**: 3 manqués = mort Super-Pair
- Heartbeat 30s × 3 = 90s window
- Détection panne : max 2 minutes (3×30s + latence)

**Scan éviction** (MonitorMemberLivenessUseCase.kt) :
```kotlin
const val LIVENESS_CHECK_INTERVAL_MS = 15_000L  // Scan toutes 15s
val stale = memberRegistry.listStale(now - SP_TIMEOUT_MS)
// Évict si now - lastSeen > 90s
```

### Gossip Fan-out

**Valeur**: FAN_OUT = 2

**Fichier**: `GossipSyncUseCase.kt` ligne 36
```kotlin
companion object {
    private const val FAN_OUT = 2
}
```

**Comportement** (GossipSyncUseCase.kt lignes 66-71):
```kotlin
val neighbors = selectRandomNeighbors(activePeers, FAN_OUT)
for (peer in neighbors) {
  gossipOutboundPort.sendBloomGossip(nodeId, gossipMsg)
}
```

**Impacte**: Chaque nœud envoie Gossip à 2 voisins aléatoires par cycle
- ❌ Pas de 3-voisins documenté
- ❌ Pas de cycle court/long adaptatif

---

## Module 08-09 : Erasure Coding

### Paramètres k/n (Fixe)

**Fichier**: `ErasureParameters.kt` lignes 14-18
```kotlin
data class ErasureParameters(
    val k: Int = 4,          // Data blocks
    val n: Int = 2,          // Parity blocks
    val blockSize: Int = 1 * 1024 * 1024,
)
```

**Implémentation**: Reed-Solomon GF(256)
- 4 blocs données + 2 blocs parité = 6 fragments total
- Tolérance: jusqu'à 2 pertes (2 manquants acceptés)
- ❌ **NON adaptatif** : pas de K+2-K+8 selon fiabilité

**Validation** (EncodeErasureFragmentsUseCase.kt lignes 27-29):
```kotlin
require(params.k >= 1 && params.n >= 1 && params.k + params.n <= 255) {
    "GF(256) constraint: k >= 1, n >= 1, k + n <= 255 (got k=${params.k}, n=${params.n})"
}
```

### Fragment Size Calculation

**Code** (EncodeErasureFragmentsUseCase.kt lignes 36-41):
```kotlin
val fragmentSize = (bytes.size + params.k - 1) / params.k  // ceil
require(fragmentSize.toLong() * params.k <= Int.MAX_VALUE) {
    "Padded size overflows Int (fragmentSize=$fragmentSize, k=${params.k})"
}
val paddedSize = fragmentSize * params.k
val padded = bytes.copyOf(paddedSize)  // Zero-pad to multiple of k
```

**Exemple**:
```
File: 10 MB (10 485 760 bytes)
k = 4
fragmentSize = ceil(10485760 / 4) = 2 621 440 bytes
paddedSize = 2 621 440 × 4 = 10 485 760 (déjà multiple)
Result: 4 data fragments of 2.6 MB + 2 parity fragments of 2.6 MB
```

---

## Module 10 : Élection Bully

### Comparaison Score

**Code** (RunBullyElectionUseCase.kt lignes 140-149):
```kotlin
private fun isHigherPriority(
    otherScore: Float,
    otherId: String,
    localScore: Float,
    localId: String
): Boolean {
    if (otherScore > localScore) return true
    if (otherScore < localScore) return false
    return otherId > localId  // Lexicographic tie-break
}
```

**Algorithme**:
1. Si `otherScore > localScore` → other a priorité (higher reliability)
2. Si `otherScore < localScore` → self a priorité
3. Sinon (égalité) → `otherId > localId` lexicographique (déterministe)

**Exemple**:
```
Self: score=0.75, id="aaa..."
Other: score=0.8, id="bbb..."
→ otherScore (0.8) > localScore (0.75) → OTHER WINS
→ Self envoie ALIVE, abandonne

Self: score=0.75, id="zzz..."
Other: score=0.75, id="aaa..."
→ otherScore == localScore
→ "zzz" > "aaa" → SELF WINS (lexicographique)
```

### Monitoring Window

**Valeur**: 20 000 ms

**Fichier**: `RunBullyElectionUseCase.kt` lignes 46-48
```kotlin
companion object {
    const val MONITORING_WINDOW_MS = 20_000L
}
```

**Code** (lignes 62-75):
```kotlin
peerRepository.peers
    .map { peers ->
        val noSuperPeer = peers.none { it.isActive && it.isSuperPair }
        val hasOtherKnownPeer = peers.any { it.identity.nodeId != localIdentity.nodeId }
        noSuperPeer && hasOtherKnownPeer
    }
    .distinctUntilChanged()
    .transformLatest { shouldElect ->
        if (shouldElect) {
            delay(MONITORING_WINDOW_MS)  // Attendre 20s
            emit(Unit)
        }
    }
    .firstOrNull()
```

**Signification**:
- Nœud détecte pas de Super-Pair actif
- **Attend 20 secondes** avant de déclencher Bully
- Si Super-Pair réapparaît pendant 20s → annuler élection

---

## Module 11 : Cluster & JOIN

### Constantes Cluster

**Fichier**: `ClusterConstants.kt`

```kotlin
const val MAX_CLUSTER_SIZE = 50

const val JOIN_PROTOCOL_VERSION = 2

const val HEARTBEAT_INTERVAL_MS = 30_000L

const val SP_TIMEOUT_MS = 90_000L

const val JOIN_REQUEST_TIMEOUT_MS = 5_000L

const val ISOLATION_BACKOFF_MS = 20_000L

const val LIVENESS_CHECK_INTERVAL_MS = 15_000L

const val BULLY_TIMESTAMP_WINDOW_MS = 30_000L
```

### JOIN_REQUEST Signature

**Payload signé** (ProcessJoinRequestUseCase.kt lignes 47-53):
```kotlin
val signedBytes = joinRequestSignedBytes(
    senderNodeId = request.senderNodeId,
    candidatePublicKey = request.candidatePublicKey,
    freeBytes = request.freeBytes,
    reliabilityScore = request.reliabilityScore,
    timestampMs = request.timestampMs
)
```

**Schéma** : `SHA256(senderNodeId || pubkey || freeBytes || score || timestamp)`
- Champs: 5 (pas de GPS contrairement doc)
- Protocol version: 2 (Story 12.1)

### Check-and-Add Atomique

**Code critique** (ProcessJoinRequestUseCase.kt lignes 74-92):
```kotlin
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
    val alts = getAlternativeSuperPeers(selfNodeIdBytes)
    networkEventRepository.pushEvent(
        "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: CLUSTER_FULL (${memberRegistry.size()}/$MAX_CLUSTER_SIZE)"
    )
    return signedRedirect(JoinRedirectReason.CLUSTER_FULL, alts, selfNodeIdBytes)
}
```

**Garantie**:
- `addIfBelowCapacity` doit être **atomic** en Room DB
- Sinon : 2 JOIN concurrents → cluster > MAX_CLUSTER_SIZE

**Implementation Room** (RoomMemberRegistry.kt) :
```kotlin
@Transaction  // DB transaction atomic
suspend fun addIfBelowCapacity(member: MemberInfo, max: Int): Boolean {
    val count = memberDao.count()
    if (count >= max) return false
    memberDao.insert(member)
    return true
}
```

---

## Protocole Relai — Détails Binaires

### Frame Format

**Structure**:
```
Byte 0      : Type opcode (0x01-0xFF)
Bytes 1-4   : Length (little-endian uint32)
Bytes 5+    : Payload (Length bytes)
```

**Exemple** (AUTH 0x01):
```
Hex:  01 | 2E 01 00 00 | {"nodeId":"abcdef0123456789",...}
      ↑    ↑
      Type Length (302 bytes in LE)

Binary representation:
  buildFrame(MSG.AUTH, Buffer.from(JSON.stringify(authPayload), 'utf8'))
```

### Validation Frame

**Code** (relay-server/server.js lignes 58-65):
```javascript
function parseFrame(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 5) return null;
  const type = buf.readUInt8(0);
  const length = buf.readUInt32LE(1);
  if (buf.length !== 5 + length) return null;
  if (length > MAX_BLOCK_SIZE + 128) return null; // +128 pour header JSON UPLOAD
  return { type, payload: buf.slice(5) };
}
```

**Rejets** :
- `< 5 bytes` → null (malformé)
- `length != buf.length - 5` → null (mismatch)
- `length > 1.1 MB + 128` → null (oversize)

---

## Relay Buffer (UPLOAD/FORWARD)

### Déduplication & TTL

**Code** (relay-server/server.js lignes 410-433):
```javascript
const existing = relayBuffer.get(blockId) || [];

// Déduplication : si (blockId, destNodeId) existe déjà, remplacer l'entrée
const dupIdx = existing.findIndex(e => e.destNodeId === destNodeId);
if (dupIdx !== -1) {
  clearTimeout(existing[dupIdx].ttlTimer);
  existing.splice(dupIdx, 1);
}

// TTL 60s
const newEntry = { fromNodeId, destNodeId, data, ttlTimer: null };
const ttlTimer = setTimeout(() => {
  const arr = relayBuffer.get(blockId);
  if (arr) {
    const filtered = arr.filter(e => e !== newEntry);
    if (filtered.length === 0) relayBuffer.delete(blockId);
    else relayBuffer.set(blockId, filtered);
  }
}, TTL_MS);  // 60_000 ms
newEntry.ttlTimer = ttlTimer;

existing.push(newEntry);
relayBuffer.set(blockId, existing);
```

**Garantie**:
- Chaque `(blockId, destNodeId)` n'existe qu'une fois
- Si nouveau UPLOAD arrive pour même paire → remplacer (reset TTL)
- Auto-purge après 60s inactivité

---

## Annuaire Signalisation

### REGISTER_PEER Entry

**Structure DB** (relay-server/server.js lignes 224-234):
```javascript
signalingRegistry.set(nodeId, {
  ip, port,
  reliabilityScore: reliabilityScore ?? 0.5,
  electedAt: electedAt ?? Date.now(),
  clusterId: clusterIdStr,
  freeBytes: freeBytesNum,
  currentMemberCount: currentMemberCountNum,
  lastSeen: Date.now(),
  ttlTimer,
  isSuperPair: true   // REGISTER_PEER = revendication formelle
});
```

**Validation UUID clusterId** (relay-server/server.js lignes 171-180):
```javascript
const UUID_V4_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
let clusterIdStr = '';
if (clusterId === undefined || clusterId === null || clusterId === '') {
  clusterIdStr = '';
} else if (typeof clusterId === 'string' && UUID_V4_RE.test(clusterId)) {
  clusterIdStr = clusterId;
} else {
  console.warn(`[SIGNALING] clusterId invalide rejeté (coerce en "")`);
  clusterIdStr = '';
}
```

**Coercion** (non-rejet si UUID invalide) :
- `clusterId` invalide → remplacer par `""` + warn
- `freeBytes` invalide → remplacer par `0` + warn
- `currentMemberCount` invalide → remplacer par `0` + warn

---

## Health Endpoint

### GET /health

**Code** (relay-server/server.js lignes 527-546):
```javascript
if (req.method === 'GET' && req.url === '/health') {
  let superPeerCount = 0;
  for (const entry of signalingRegistry.values()) {
    if (entry.isSuperPair) superPeerCount++;
  }
  const body = JSON.stringify({
    status: 'ok',
    sessions: sessions.size,
    pendingBlocks: relayBuffer.size,
    participants: signalingRegistry.size,
    registeredSuperPeers: superPeerCount
  });
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(body);
}
```

**Sortie**:
```json
{
  "status": "ok",
  "sessions": 12,
  "pendingBlocks": 45,
  "participants": 50,
  "registeredSuperPeers": 8
}
```

---

## Member Registry (Room DB)

### Schéma

**Entité MemberInfo**:
```kotlin
@Entity(tableName = "cluster_members")
data class MemberInfo(
  @PrimaryKey
  val nodeId: String,              // 16 hex chars
  val clusterId: String,           // UUID
  val publicKey: ByteArray,        // EC P-256 SPKI-DER
  val ipAddress: String = "",
  val port: Int = 0,
  val freeBytes: Long = 0,
  val role: MemberRole = MEMBER,   // MEMBER | SUPER_PAIR
  val joinedAt: Long,
  val lastHeartbeat: Long
)

enum class MemberRole {
  MEMBER,
  SUPER_PAIR
}
```

### Atomic Insert

**Opération critique** (ProcessJoinRequestUseCase.kt):
```kotlin
@Transaction
suspend fun addIfBelowCapacity(member: MemberInfo, max: Int): Boolean {
  val currentCount = memberDao.count()
  if (currentCount >= max) return false
  memberDao.insert(member)  // 1 insert = 1 transaction
  return true
}
```

**Garantie Room** : Transaction wraps count + insert = atomic (no race)

---

## Hardening Gossip

### Anti-Amplification

**Code** (GossipSyncUseCase.kt lignes 104-111):
```kotlin
val isKnownPeer = peerRepository.peers.value
    .any { it.identity.nodeId == msg.senderNodeId && it.isActive }
if (!isKnownPeer) {
    networkEventRepository.pushEvent(
        "[GOSSIP] Bloom rejete : sender ${msg.senderNodeId.take(8)} non-pair"
    )
    return@withContext Result.success(Unit)
}
```

**Logique** : Rejeter Bloom si sender n'est pas pair connu
- ❌ Sinon : attaquant non-pair spam Bloom → victime query DHT + emit DeltaRequest
- ✅ Avec : Bloom rejeté silencieusement

### Anti-DHT Poisoning

**Code** (GossipSyncUseCase.kt lignes 192-201):
```kotlin
val isKnownPeer = peerRepository.peers.value
    .any { it.identity.nodeId == response.responderNodeId && it.isActive }
if (!isKnownPeer) {
    networkEventRepository.pushEvent(
        "[GOSSIP] DeltaResponse rejete : responder ${response.responderNodeId.take(8)} non-pair"
    )
    return@withContext Result.failure(
        SecurityException("DeltaResponse from unknown peer ${response.responderNodeId}")
    )
}
```

**Logique** : Rejeter DeltaResponse si responder pas pair connu
- ❌ Sinon : attaquant injecte fausses routes DHT (blockId X chez IP-malveillant)
- ✅ Avec : Routes rejetées

---

## Anti-Loop Protections

### UPLOAD Anti-loop

**Code** (relay-server/server.js lignes 388-392):
```javascript
if (destNodeId.toLowerCase() === String(fromNodeId).toLowerCase()) {
  sendError(senderWs, 'UPLOAD destNodeId == fromNodeId interdit');
  return;
}
```

**Logique** : Nœud ne peut pas s'uploader un bloc à lui-même

### REQUEST_BLOCK Anti-loop

**Code** (relay-server/server.js lignes 464-470):
```javascript
if (destNodeId.toLowerCase() === String(fromNodeId).toLowerCase()) {
  sendError(senderWs, 'REQUEST_BLOCK destNodeId == fromNodeId interdit');
  return;
}
```

**Logique** : Requester ne peut pas demander à lui-même

---

## Performance Heartbeat Relai

**Protocole-level ping** (relay-server/server.js lignes 692-703):
```javascript
const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();  // Socket zombie
      continue;
    }
    ws.isAlive = false;
    try { ws.ping(); } catch { /* ws en cours de fermeture */ }
  }
}, HEARTBEAT_INTERVAL_MS);  // 30_000 ms

ws.on('pong', () => { ws.isAlive = true; });
```

**Garantie**:
- Toutes 30s : relai ping TOUTES les ws
- Si pas pong dans les 30s suivants → ws.terminate()
- Détecte sockets "zombies" (réseau coupé sans TCP close)

---

## Résumé Implémentation Clés

| Aspect | Valeur | Source |
|--------|--------|--------|
| **Auth signature** | EC P-256 SHA-256 | RelayAuthSigner.kt, relay-server |
| **Auth payload** | `MobiCloud-HA-AUTH:nodeId:timestamp` | relay-server ligne 105 |
| **Anti-replay** | ±30 000 ms | AUTH_WINDOW_MS, relay-server |
| **Heartbeat** | 30 000 ms fixe | ClusterConstants.kt |
| **Timeout SP** | 90 000 ms | SP_TIMEOUT_MS |
| **Erasure k/n** | 4/2 fixe | ErasureParameters.kt |
| **Max cluster** | 50 nœuds | MAX_CLUSTER_SIZE |
| **Ellection tie-break** | Lexicographique nodeId | RunBullyElectionUseCase ligne 148 |
| **Monitoring window** | 20 000 ms | RunBullyElectionUseCase |
| **Relay buffer TTL** | 60 000 ms | relay-server TTL_MS |
| **Relay max buffer entries** | 500 blocs | relay-server MAX_RELAY_BUFFER_ENTRIES |
| **GET_PEERS PKI** | TOFU (clés au relai) | relay-server ligne 310 |

---

**Document généré**: 13 mai 2026 — Scan exhaustif code source  
**Validité**: Snapshot production à date  
**Tous les chemins fichiers**: Vérifiables dans repo MobiCloud
