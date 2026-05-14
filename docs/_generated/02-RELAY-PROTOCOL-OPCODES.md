# Protocole Relai WebSocket — Référence Opcodes
**Scan exhaustif — Date: 13 mai 2026**

## Résumé

Le relai WebSocket Node.js (/relay-server/server.js ~740 lignes) gère tous les messages inter-nœuds via un **protocole binaire custom** (1 byte type + 4 bytes length LE + payload).

**18 opcodes** implémentés couvrant : authentification, signalisation (annuaire), relai blocs, élection Bully, et Gossip DHT.

---

## Framing binaire

```c
struct Frame {
  uint8_t   type;           // Opcode (0x01-0xFF)
  uint32_t  length;         // Longueur payload (little-endian)
  uint8_t   payload[length]; // Données (type-dépendant)
};
```

**Validation relai** :
- Frame < 5 bytes → rejeté (malformé)
- Frame length ≠ payload size → rejeté
- Payload > MAX_BLOCK_SIZE + 128 → rejeté

---

## Authentification

### 0x01 AUTH (Client → Relai)

**Payload**: JSON UTF-8
```json
{
  "nodeId": "abcdef0123456789",        // 16 hex ASCII exactement
  "pubKeySpkiDer": "MIIBIjANBg...",    // Base64 EC P-256 SPKI-DER key
  "timestamp": 1715601234567,          // Unix ms (Number)
  "signature": "MEQCIHx+wO1..."        // Base64 EC P-256 ECDSA signature
}
```

**Vérification relai** (server.js lines 82-136) :
```
1. Parse JSON → extract nodeId, pubKeySpkiDer, timestamp, signature
2. Valider nodeId : /^[0-9a-fA-F]{16}$/ (exactement 16 hex ASCII)
3. Fenêtre anti-replay : |now - timestamp| ≤ 30 000 ms (AUTH_WINDOW_MS)
4. Créer clé publique depuis pubKeySpkiDer (DER base64)
5. Vérifier clé = EC (prime256v1 REQUIRED)
6. Payload signé : Buffer.from(`MobiCloud-HA-AUTH:${nodeId}:${timestamp}`, 'utf8')
7. Signature verify : crypto.verify('SHA256', signedData, publicKey, signature)
```

**Erreurs possibles**:
- `AUTH JSON invalide` → AUTH field missing/unparseable
- `nodeId invalide (attendu 16 chars hex ASCII)` → wrong format
- `AUTH timestamp hors fenêtre (écart ${skew}ms)` → replay detected
- `Clé publique invalide` → DER decode failed
- `Clé doit être de type EC` → non-EC key
- `Courbe EC invalide : attendu prime256v1, reçu ${curve}` → wrong curve
- `Signature EC P-256 invalide` → verify failed

**Timeout** : Si AUTH non reçu dans 10 000 ms → ws.close(1008, 'AUTH timeout')

**Réponse** : 0x02 AUTH_OK (empty payload) → session authentifiée

---

### 0x02 AUTH_OK (Relai → Client)

**Payload**: Empty

Signale succès authentification. Client peut maintenant envoyer autres messages.

**Comportement**:
- Une seule AUTH_OK par session (re-AUTH reçoit ERROR)
- Fermeture ancienne session si même nodeId se reconnecte

---

## Signalisation (Annuaire peer discovery)

### 0x03 REGISTER_PEER (Client → Relai)

**Payload**: JSON UTF-8
```json
{
  "ip": "192.168.1.10",
  "port": 5000,
  "reliabilityScore": 0.85,           // Optional, default 0.5
  "electedAt": 1715601234567,        // Optional, default now()
  "clusterId": "550e8400-e29b-...",   // Optional UUID v4, default ""
  "freeBytes": 1073741824,            // Optional, default 0 (Story 9.2)
  "currentMemberCount": 12            // Optional, default 0 (Story 12.1)
}
```

**Validation relai** (server.js lines 152-238) :
```
1. Valider ip : /^[\d.:a-fA-F]{2,45}$/ (IPv4 ou IPv6)
2. Valider port : 0 ≤ port ≤ 65535 (Number)
3. Valider clusterId : UUID v4 stricte /^[0-9a-f]{8}-4[0-9a-f]{3}-...$/
   - Si invalide → coerce en "" + warn (pas rejeté)
4. Valider freeBytes : Number, isFinite, ≥0, ≤ MAX_SAFE_INTEGER
   - Si invalide → coerce en 0 + warn
5. Valider currentMemberCount : idem freeBytes, max MAX_CLUSTER_SIZE_SERVER (50)
6. Cap serveur : si registre plein (≥100) et nodeId pas déjà enregistré → rejeté
```

**Enregistrement DB** :
```javascript
signalingRegistry.set(nodeId, {
  ip, port,
  reliabilityScore,
  electedAt,
  clusterId,
  freeBytes,
  currentMemberCount,
  lastSeen: now(),
  ttlTimer: setTimeout(..., 60000),  // Auto-purge 60s
  isSuperPair: true  // Marque Super-Pair élu
});
```

**Réponse** : ACK 0x08 ou ERROR 0xFF

---

### 0x0B JOIN (Client → Relai)

**Payload**: JSON UTF-8
```json
{
  "ip": "192.168.1.11",               // Optional
  "port": 5001,                       // Optional
  "reliabilityScore": 0.75            // Optional, default 0.5
}
```

**Validation relai** (server.js lines 243-297) :
```
1. IP/port optionnels — si présents, valider strictement
2. Idem REGISTER_PEER mais ip/port pas requis
3. Importante : si nodeId DÉJÀ Super-Pair (isSuperPair=true)
   → préserver statut Super-Pair (re-JOIN ne dégrade pas)
4. Si isSuperPair=true → préserver clusterId, freeBytes, currentMemberCount existants
```

**Enregistrement DB** :
```javascript
signalingRegistry.set(nodeId, {
  ip: ip ?? '0.0.0.0',    // Default loopback si absent
  port: port ?? 0,
  reliabilityScore,
  electedAt: existing?.electedAt ?? null,  // Garder existant
  clusterId: existing?.clusterId ?? '',    // Garder existant
  freeBytes: existing?.freeBytes ?? 0,     // Garder existant
  currentMemberCount: existing?.currentMemberCount ?? 0,
  lastSeen: now(),
  ttlTimer,
  isSuperPair: wasSuperPair  // Garder existant (pas de dégradation)
});
```

**Objectif**: Présence simple (polling heartbeat) vs. REGISTER_PEER (élection formelle)

---

### 0x04 GET_PEERS (Client → Relai)

**Payload**: Empty

Client demande snapshot annuaire.

**Réponse**: 0x05 PEERS

---

### 0x05 PEERS (Relai → Client)

**Payload**: JSON UTF-8
```json
[
  {
    "nodeId": "abcdef0123456789",
    "ip": "192.168.1.10",
    "port": 5000,
    "reliabilityScore": 0.85,
    "lastSeen": 1715601234567,
    "isSuperPair": true,
    "clusterId": "550e8400-e29b-...",
    "freeBytes": 1073741824,
    "pubKeySpkiDerB64": "MIIBIjANBg...",        // Story 10.1 : clé publique (TOFU PKI)
    "currentMemberCount": 12
  },
  ...
]
```

**Détails** (server.js lines 299-334) :
- `pubKeySpkiDerB64` : récupérée depuis `sessions[nodeId].publicKey.export()`
  - Si export échoue → chaîne vide
  - Role de PKI TOFU : le relai joue le rôle d'autorité de certificat de confiance
- Snapshot complet à l'appel GET_PEERS

---

## Relai Store-and-Forward (Blocs)

### 0x06 UPLOAD (Client → Relai)

**Payload**: Binaire
```
Offset  Taille  Contenu
0       16      destNodeId (UTF-8, NUL-padé)
16      64      blockId (UTF-8 hex, NUL-padé)
80      N       data (bloc chiffré AES-256-GCM)
```

**Validation relai** (server.js lines 366-439) :
```
1. Payload < 80 bytes → ERROR "payload trop court"
2. Strip padding NUL trailing uniquement (pas tous NUL)
   - destNodeId = payload[0:16].toString('utf8').replace(/\0+$/, '').trim()
   - blockId = payload[16:80].toString('utf8').replace(/\0+$/, '').trim()
3. Valider destNodeId : exactement 16 chars après strip
4. Valider blockId : /^[0-9a-fA-F]{64}$/ (64 hex chars)
5. Anti-loop : destNodeId != fromNodeId (case-insensitive)
```

**Livraison** :
- Si destinataire **connecté** → FORWARD 0x07 immédiat (fire-and-forget)
- Sinon → **BUFFERED** en RAM avec TTL 60s
  - Déduplication : si (blockId, destNodeId) existe → remplacer
  - Cap : MAX_RELAY_BUFFER_ENTRIES = 500 blocs
- À reconnexion dest → `flushPendingBlocks()` → vidage buffer

**Réponse** : 0x08 ACK ou 0xFF ERROR

**ACK payload** :
```json
{ "blockId": "..." }
```

---

### 0x07 FORWARD (Relai → Client)

**Payload**: Binaire (structure identique UPLOAD)
```
Offset  Taille  Contenu
0       16      fromNodeId (source UPLOAD)
16      64      blockId
80      N       data (bloc chiffré)
```

Bloc transité via relai (immédiat ou depuis buffer).

---

### 0x0C REQUEST_BLOCK (Client → Relai) — Pull inter-cluster

**Payload**: Binaire (80 bytes exact)
```
Offset  Taille  Contenu
0       16      destNodeId (Super-Pair distant)
16      64      blockId (demandé)
```

**Validation relai** (server.js lines 446-486) :
```
1. Payload != 80 bytes → ERROR
2. Strip padding NUL destNodeId/blockId (idem UPLOAD)
3. Valider destNodeId : 16 chars après strip
4. Valider blockId : /^[0-9a-fA-F]{64}$/
5. Anti-loop : destNodeId != fromNodeId (case-insensitive)
6. Destinataire **DOIT être connecté** → sinon ERROR "destinataire injoignable"
   ⚠️ **Pas de buffering** : requester time-out de toute façon
```

**Relai** : Pivot 80 bytes vers destinataire via 0x0D REQUEST_BLOCK_FORWARDED

---

### 0x0D REQUEST_BLOCK_FORWARDED (Relai → Client)

**Payload**: Binaire (80 bytes)
```
Offset  Taille  Contenu
0       16      fromNodeId (requester)
16      64      blockId
```

Destinataire reçoit demande bloc et répond directement via TCP ou 0x06 UPLOAD.

---

## Gossip DHT

### 0x0E SIGNAL (Client → Relai)

**Payload**: Binaire
```
Offset  Taille  Contenu
0       16      destNodeId (UTF-8, NUL-padé)
16      N       data (arbitraire)
```

**Validation relai** (server.js lines 491-504) :
```
1. Payload < 16 bytes → droppé silencieusement
2. destNodeId = payload[0:16].toString('utf8').replace(/\0+$/, '').trim()
3. Si destNodeId < 4 chars → droppé
4. Si destinataire connecté → forward 0x0F SIGNAL_RECEIVED
5. Sinon → **droppé silencieusement** (pas de buffering)
```

Utilisé pour Gossip DHT (delta-sync, bloom filter, etc.)

---

### 0x0F SIGNAL_RECEIVED (Relai → Client)

**Payload**: Binaire
```
Offset  Taille  Contenu
0       16      fromNodeId (sender)
16      N       data
```

Gossip reçu via relai unicast.

---

## Élection Bully

### 0x10 ELECTION_BROADCAST (Client → Relai)

**Payload**: JSON UTF-8
```json
{
  "senderNodeId": "abcdef0123456789",
  "type": "ELECTION" | "ALIVE" | "COORDINATOR",
  "reliabilityScore": 0.85,
  "clusterId": "550e8400-e29b-...",
  "timestampMs": 1715601234567,
  "signatureBytes": "MEQCIHx+wO1..."
}
```

**Validation relai** : Pas strict (best-effort), simple forward.

**Relai** (server.js lines 510-523) :
```
1. Forward payload à TOUS connectés SAUF émetteur
2. Fire-and-forget (pas de buffering)
3. Log type de message pour diagnostique
```

---

### 0x11 ELECTION_RECEIVED (Relai → Client)

**Payload**: JSON UTF-8 (identique ELECTION_BROADCAST)

Reçu par tous nœuds sauf émetteur.

**Traitement client** (RunBullyElectionUseCase.kt) :
- Type "ELECTION" → Si score > mien → ignore (envoie ALIVE)
- Type "ALIVE" → J'ai perdu (score > mien) → abandon
- Type "COORDINATOR" → J'ai gagné, deviens Super-Pair

---

## Health Check

### 0x09 PING (Client → Relai)

**Payload**: Empty

Heartbeat protocol-level WebSocket.

**Réponse**: 0x0A PONG

---

### 0x0A PONG (Relai → Client)

**Payload**: Empty

Relai confirme liveness.

**Serveur heartbeat** (server.js lines 692-703) :
```javascript
setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();  // Socket zombie
      continue;
    }
    ws.isAlive = false;
    ws.ping();  // Envoie 0x09
  }
}, 30_000);  // Toutes 30s

ws.on('pong', () => { ws.isAlive = true; });
```

Si pas PONG dans 30s → socket fermée (zombie network).

---

## Erreur

### 0xFF ERROR (Relai → Client)

**Payload**: UTF-8 text (message erreur)

Exemples:
- `Frames texte non supportées — utiliser frames binaires`
- `Frame malformée`
- `Premier message doit être AUTH (0x01)`
- `AUTH échouée : AUTH timestamp hors fenêtre (écart Xms)`
- `UPLOAD payload trop court (min 80 bytes)`
- `REQUEST_BLOCK destinataire injoignable`
- `Relay buffer plein — réessayer plus tard`

---

## Statistiques serveur

### GET /health (HTTP)

**Réponse**: JSON
```json
{
  "status": "ok",
  "sessions": 12,             // Connexions authentifiées actives
  "pendingBlocks": 45,        // Blocs en attente dans buffer
  "participants": 50,         // Annuaire total (JOIN + REGISTER)
  "registeredSuperPeers": 8   // Super-Pairs (isSuperPair=true)
}
```

---

## Limits & Constants

| Constant | Valeur | Détails |
|----------|--------|---------|
| **MAX_BLOCK_SIZE** | 1 100 000 | 1.1 MB max payload UPLOAD/FORWARD |
| **MAX_RELAY_BUFFER_ENTRIES** | 500 | Blocs en attente max |
| **MAX_SIGNALING_PEERS** | 100 | Super-Pairs enregistrés max |
| **MAX_CLUSTER_SIZE_SERVER** | 50 | Mirror app MAX_CLUSTER_SIZE |
| **TTL_MS** | 60 000 | Buffer blocs + annuaire auto-purge |
| **AUTH_WINDOW_MS** | 30 000 | Fenêtre anti-replay ±30s |
| **AUTH_TIMEOUT_MS** | 10 000 | Ferme non-auth après 10s |
| **HEARTBEAT_INTERVAL_MS** | 30 000 | Ping protocol-level 30s |

---

## État du relai

### Mémoire (RAM only)

**sessions** Map<nodeId, { ws, publicKey }>
- Une entrée par connexion authentifiée

**signalingRegistry** Map<nodeId, { ip, port, reliabilityScore, electedAt, clusterId, freeBytes, currentMemberCount, lastSeen, ttlTimer, isSuperPair }>
- Annuaire peer discovery
- Auto-purge après 60s inactivité

**relayBuffer** Map<blockId, [{ fromNodeId, destNodeId, data, ttlTimer }]>
- Blocs en attente (destinataire absent)
- Auto-purge après 60s inactivité

**Pas de persistance disque** — redémarrage perd tout (acceptable pour MVP).

---

## Graceful Shutdown

**SIGTERM handler** (server.js lines 708-722) :
```
1. Log "fermeture gracieuse"
2. Fermer toutes les ws avec code 1001 "Server shutting down"
3. httpServer.close()
4. Timeout filet de sécurité 5s → process.exit(0)
```

---

**Document généré**: 13 mai 2026 — Scan exhaustif relay-server/server.js
**Validité**: Snapshot production à date
