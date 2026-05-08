# Story 10-1 — Propagation de la clé publique dans GET_PEERS (résolution ByteArray(0) inter-cluster)

**Epic :** 10 — Hardening Sécurité & Dettes Techniques  
**Story ID :** 10-1  
**Status :** implemented  
**Date :** 2026-05-08  
**Auteur :** Antigravity (analyse conversation 7e21580e)

---

## User Story

> **En tant que** nœud MobiCloud qui distribue un fichier en mode inter-cluster (Niveau 3),  
> **je veux** pouvoir vérifier la signature de l'ACK reçu depuis un Super-Pair distant,  
> **afin de** garantir que le fragment a bien été reçu et stocké par ce nœud, et non forgé.

---

## Contexte et motivation

### Le problème (W4 — déferred depuis Story 3-2)

Quand `DistributeEncryptedBlocksUseCase` place un fragment sur un **Super-Pair inter-cluster** (Niveau 3), il construit un `Peer` avec :

```kotlin
// DistributeEncryptedBlocksUseCase.kt ligne 158
identity = NodeIdentity(remote.nodeId, ByteArray(0))
```

Ce `ByteArray(0)` vient de `SignalingRepositoryImpl.processPeerList()` (ligne 111), qui renseigne `NodeIdentity(peer.nodeId, ByteArray(0))` parce que le payload `GET_PEERS` du serveur relais **ne transporte pas la clé publique**.

Ensuite, `BlockTransferClient.sendBlock()` (ligne 85–93) tente de vérifier la signature de l'ACK :

```kotlin
val valid = securityRepository.verifySignature(
    data = signingPayload,
    signature = ack.signature,
    publicKey = peer.identity.publicKeyBytes  // ← ByteArray(0) → KeyInvalidException
).getOrDefault(false)
if (!valid) {
    return@withContext Result.failure(SecurityException("Signature ACK invalide"))
}
```

**Conséquence :** tout upload inter-cluster (Niveau 3) échoue avec `SecurityException("Signature ACK invalide")`, rendant le fallback Niveau 3 **inopérant en production**.

### Pourquoi la clé publique est déjà disponible côté serveur

Le serveur relais authentifie chaque nœud via `AUTH` avec `pubKeySpkiDer` (clé publique EC P-256 SPKI-DER encodée Base64). La clé est déjà stockée en session :

```javascript
// server.js ligne 484-485
authState = { nodeId: result.nodeId, publicKey: result.publicKey };
sessions.set(result.nodeId, { ws, publicKey: result.publicKey });
```

Il suffit donc de **sérialiser `publicKey` dans le payload `GET_PEERS`** pour que les clients Android la reçoivent.

---

## Périmètre de la story

### Ce qui change

#### 1. `relay-server/server.js` — `handleGetPeers`

Ajouter `pubKeySpkiDerB64` dans chaque entrée peer de la réponse `PEERS` :

```javascript
function handleGetPeers(ws) {
  const peers = [];
  for (const [nodeId, entry] of signalingRegistry.entries()) {
    // Récupérer la clé publique depuis la session active (si présente)
    const session = sessions.get(nodeId);
    const pubKeySpkiDerB64 = session?.publicKey
      ? session.publicKey.export({ format: 'der', type: 'spki' }).toString('base64')
      : '';

    peers.push({
      nodeId,
      ip: entry.ip,
      port: entry.port,
      reliabilityScore: entry.reliabilityScore,
      lastSeen: entry.lastSeen,
      isSuperPair: entry.isSuperPair === true,
      clusterId: entry.clusterId ?? '',
      freeBytes: entry.freeBytes ?? 0,
      pubKeySpkiDerB64   // ← nouveau champ
    });
  }
  safeSend(ws, buildFrame(MSG.PEERS, Buffer.from(JSON.stringify(peers), 'utf8')));
}
```

**Rétrocompatibilité :** champ absent = `''` côté client → pas d'impact sur les clients ne lisant pas ce champ.

**Note TTL :** La clé publique est liée à la session WebSocket active. Si un nœud se déconnecte et se reconnecte, il re-AUTH avec la même clé (AndroidKeyStore). La session `sessions.get(nodeId)` sera null si le nœud est dans `signalingRegistry` mais déconnecté — dans ce cas `pubKeySpkiDerB64 = ''`, ce qui est correct (le nœud n'est plus joignable directement).

#### 2. `relay-server/server.test.js` — Tests `handleGetPeers`

Ajouter les cas :
- `pubKeySpkiDerB64` présent dans la réponse si le nœud a une session active avec `publicKey`
- `pubKeySpkiDerB64 = ''` si le nœud est dans le registre mais sans session active

#### 3. `data/repository/SignalingRepositoryImpl.kt` — `processPeerList()`

Parser le nouveau champ et le propager dans `NodeIdentity` :

```kotlin
// Avant (ligne 111)
identity = NodeIdentity(peer.nodeId, ByteArray(0))

// Après
val pubKeyBytes = if (peer.pubKeySpkiDerB64.isNotBlank()) {
    runCatching { Base64.decode(peer.pubKeySpkiDerB64, Base64.NO_WRAP) }
        .getOrElse { 
            Log.w(TAG, "[processPeerList] pubKeySpkiDerB64 invalide pour ${peer.nodeId.take(8)}")
            ByteArray(0) 
        }
} else ByteArray(0)

identity = NodeIdentity(peer.nodeId, pubKeyBytes)
```

#### 4. `domain/models/RelayPeer.kt` — Ajouter `pubKeySpkiDerB64`

```kotlin
data class RelayPeer(
    val nodeId: String,
    val ip: String,
    val port: Int,
    val reliabilityScore: Float,
    val lastSeen: Long,
    val isSuperPair: Boolean,
    val clusterId: String,
    val freeBytes: Long,
    val pubKeySpkiDerB64: String = ""   // ← nouveau champ, défaut "" pour rétrocompatibilité
)
```

#### 5. `data/p2p/websocket/RelayWebSocketClient.kt` — `parsePeersPayload()`

Parser le nouveau champ dans la désérialisation JSON des `RelayPeer`. Chercher la méthode `parsePeersPayload` ou équivalente qui convertit la réponse JSON en `List<RelayPeer>` et ajouter l'extraction du champ `pubKeySpkiDerB64`.

#### 6. `DistributeEncryptedBlocksUseCase.kt` — Utiliser `remote.pubKeySpkiDerB64`

Une fois la clé propagée jusqu'à `RelayPeer`, le warning `[INTER-CLUSTER][R4] peer créé sans publicKeyBytes` devient obsolète pour les nœuds ayant une session active. Décoder la clé Base64 et l'injecter dans `NodeIdentity` :

```kotlin
val remotePubKeyBytes: ByteArray = if (remote.pubKeySpkiDerB64.isNotBlank()) {
    runCatching {
        android.util.Base64.decode(remote.pubKeySpkiDerB64, android.util.Base64.NO_WRAP)
    }.getOrElse {
        android.util.Log.w(
            "MobiCloud:Distribute",
            "[INTER-CLUSTER] pubKeySpkiDerB64 invalide pour ${remote.nodeId.take(8)} : ${it.message}"
        )
        ByteArray(0)
    }
} else ByteArray(0)

val remotePeer = Peer(
    identity = NodeIdentity(remote.nodeId, remotePubKeyBytes),
    // ...
)

if (remotePubKeyBytes.isEmpty()) {
    android.util.Log.w(
        "MobiCloud:Distribute",
        "[INTER-CLUSTER] peer ${remote.nodeId.take(8)} sans clé publique " +
        "— ACK non vérifiable (relay legacy ou session fermée côté serveur)"
    )
}
```

---

## Critères d'acceptation (BDD)

### AC1 — Clé publique dans GET_PEERS
```
GIVEN un Super-Pair authentifié (AUTH_OK) enregistré dans signalingRegistry
WHEN un client envoie GET_PEERS
THEN la réponse PEERS contient `pubKeySpkiDerB64` non vide pour ce Super-Pair
 AND la clé décodée en Base64 est une clé EC P-256 SPKI-DER valide
```

### AC2 — Champ absent pour nœud déconnecté
```
GIVEN un nœud dans signalingRegistry mais dont la session WebSocket est fermée
WHEN un client envoie GET_PEERS
THEN `pubKeySpkiDerB64` est une chaîne vide "" pour ce nœud
```

### AC3 — Parsing Android côté SignalingRepositoryImpl
```
GIVEN une réponse PEERS avec `pubKeySpkiDerB64` non vide
WHEN processPeerList() traite la liste
THEN le NodeIdentity créé a publicKeyBytes.isNotEmpty() = true
 AND publicKeyBytes est le décodage Base64 de pubKeySpkiDerB64
```

### AC4 — Rétrocompatibilité champ absent
```
GIVEN une réponse PEERS sans champ `pubKeySpkiDerB64` (ancien serveur)
WHEN processPeerList() traite la liste
THEN le NodeIdentity créé a publicKeyBytes = ByteArray(0) (pas de crash)
```

### AC5 — Vérification ACK inter-cluster fonctionnelle
```
GIVEN un fragment envoyé au Niveau 3 vers un Super-Pair distant
 AND ce Super-Pair a une clé publique valide dans son NodeIdentity
WHEN BlockTransferClient reçoit l'ACK signé
THEN verifySignature() retourne true (la clé publique permet de vérifier la signature)
 AND le fragment est comptabilisé comme confirmé dans le catalogEntry
```

### AC6 — Clé invalide en Base64 : dégradation silencieuse
```
GIVEN une réponse PEERS avec `pubKeySpkiDerB64` malformé (pas du Base64 valide)
WHEN processPeerList() traite la liste
THEN publicKeyBytes = ByteArray(0) (log warning, pas de crash)
 AND le nœud est tout de même inséré dans peerRepository
```

---

## Contraintes techniques

### CT1 — Pas de persistance de la clé publique
La clé publique d'un pair distant est un **snapshot volatile**, valide le temps d'une session WebSocket et d'un cycle TTL 60s. Elle ne doit PAS être persistée dans la table Room `peer_nodes` (aligné avec la décision Story 9.2 sur `clusterId`/`freeBytes`). Elle doit uniquement être disponible en mémoire via le `NodeIdentity` dans `latestPeers` du `SignalingRepository`.

### CT2 — Pas de migration Room
Aucune modification de schéma Room requise.

### CT3 — Taille de payload GET_PEERS
Chaque clé EC P-256 SPKI-DER fait 91 bytes → Base64 = ~124 chars. Pour 100 Super-Pairs max (`MAX_SIGNALING_PEERS`), l'augmentation du payload `PEERS` est au maximum **~12 400 bytes** supplémentaires. Le payload `PEERS` actuel est déjà sous `MAX_BLOCK_SIZE` (1.1 MB) → pas de dépassement.

### CT4 — Clé publique déjà vérifiée à l'AUTH
La clé exportée dans `GET_PEERS` est la même que celle vérifiée lors de l'AUTH EC P-256. Elle est déjà garantie valide et appartenant au nœud authentifié. Pas de vérification supplémentaire côté client requis — le serveur joue le rôle de PKI de confiance (modèle Trust-On-First-Use).

---

## Architecture — Flux complet après le fix

```
Super-Pair distant (B) se connecte au serveur relais
    │  AUTH { nodeId, pubKeySpkiDer, timestamp, signature }
    │  ← AUTH_OK
    │
    │  REGISTER_PEER { ip, port, clusterId, freeBytes }
    │
Nœud local (A) envoie GET_PEERS
    │  ← PEERS [ { nodeId=B, ip, port, ..., pubKeySpkiDerB64=<clé de B> } ]
    │
SignalingRepositoryImpl.processPeerList()
    │  _latestPeers.value = [ RelayPeer avec pubKeySpkiDerB64 renseigné ]
    │
DistributeEncryptedBlocksUseCase — Niveau 3
    │  remotePubKeyBytes = Base64.decode(remote.pubKeySpkiDerB64)
    │  remotePeer = Peer(identity = NodeIdentity(B, remotePubKeyBytes))
    │
BlockTransferClient.sendBlock(msg, remotePeer)
    │  → TCP direct ou relay UPLOAD
    │  ← ACK signé par B
    │
verifySignature(data, ack.signature, publicKeyBytes=<réel B>)
    │  ← true ✅
    │
DeliveryRecord confirmé → CatalogEntry → DHT
```

---

## Fichiers à modifier

| Fichier | Type de changement | État |
|---|---|---|
| `relay-server/server.js` | Modifier `handleGetPeers()` — ajouter `pubKeySpkiDerB64` | ✅ |
| `relay-server/server.test.js` | Ajouter tests AC1 et AC2 | ⏳ à écrire |
| `domain/models/RelayEvent.kt` (RelayPeer) | Ajouter champ `pubKeySpkiDerB64: String = ""` | ✅ |
| `data/p2p/websocket/RelayWebSocketClient.kt` | Parser `pubKeySpkiDerB64` dans `parsePeersPayload()` | ✅ |
| `data/repository/SignalingRepositoryImpl.kt` | Snapshot `_latestPeers` propage déjà la clé via RelayPeer | ✅ (snapshot) |
| `domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` | Décoder `remote.pubKeySpkiDerB64` → `NodeIdentity` | ✅ |

---

## Tests requis

### Côté serveur Node.js (`server.test.js`)

1. **`handleGetPeers inclut pubKeySpkiDerB64 pour nœud avec session active`**
   - Setup : injecter une entrée `sessions.set(nodeId, { ws: mockWs, publicKey: fakeCryptoKey })`
   - Assert : le JSON parsé de la réponse PEERS contient `pubKeySpkiDerB64` non vide et décodable

2. **`handleGetPeers retourne pubKeySpkiDerB64="" pour nœud sans session active`**
   - Setup : entrée dans `signalingRegistry` mais pas dans `sessions`
   - Assert : `pubKeySpkiDerB64 === ''`

### Côté Android (JVM) (`SignalingRepositoryImplTest.kt`)

3. **`processPeerList remplit publicKeyBytes depuis pubKeySpkiDerB64 valide`**
   - Given : `RelayPeer(pubKeySpkiDerB64 = validBase64Key)`
   - Then : `NodeIdentity.publicKeyBytes.isNotEmpty() == true`

4. **`processPeerList dégrade silencieusement un pubKeySpkiDerB64 invalide`**
   - Given : `RelayPeer(pubKeySpkiDerB64 = "not_base64!!!")`
   - Then : `NodeIdentity.publicKeyBytes == ByteArray(0)` + log warning émis

5. **`processPeerList gère pubKeySpkiDerB64 absent (rétrocompatibilité)`**
   - Given : `RelayPeer(pubKeySpkiDerB64 = "")` (défaut)
   - Then : `NodeIdentity.publicKeyBytes == ByteArray(0)` sans crash

---

## Dettes adressées par cette story

- **W4 (Story 3-2, deferred-work.md ligne 43)** — `ByteArray(0)` clé publique sans résolution démontrée
- **Commentaire `[Fix R4]` dans `DistributeEncryptedBlocksUseCase.kt` ligne 166-173** — dette documentée en code

---

## Dettes NON adressées par cette story (pour une future story)

- **W-9.3-3** : hot-spotting inter-cluster (`maxByOrNull { freeBytes }` envoie tous les fragments au même remote)
- **W-9.3-5** : race `latestPeers` muté pendant `distribute()`
- **`excludeNodeIds` non utilisé** dans l'appel `selectRemoteHosts()` de `DistributeEncryptedBlocksUseCase`

---

## Notes de développement

### Comment tester manuellement

1. Déployer le serveur mis à jour (ou lancer `node server.js` en local)
2. Lancer 2 émulateurs sur des clusters différents
3. Sur l'émulateur A : stocker un fichier (l'émulateur B doit être le seul pair disponible)
4. Vérifier dans les logs `[INTER-CLUSTER]` qu'il n'y a plus de `SecurityException("Signature ACK invalide")`
5. Vérifier que le `CatalogEntry` est correctement créé avec les 2+ fragments confirmés

### Point de vigilance sur `publicKey.export()`

En Node.js, `crypto.KeyObject.export({ format: 'der', type: 'spki' })` peut lever si la clé est d'un type non-exportable. Entourer d'un `try/catch` dans `handleGetPeers` :

```javascript
let pubKeySpkiDerB64 = '';
try {
  if (session?.publicKey) {
    pubKeySpkiDerB64 = session.publicKey
      .export({ format: 'der', type: 'spki' })
      .toString('base64');
  }
} catch (e) {
  console.warn(`[GET_PEERS] export clé publique échoué pour ${nodeId.slice(0,8)}: ${e.message}`);
}
```
