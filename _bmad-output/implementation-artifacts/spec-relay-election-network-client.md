---
title: 'Relay Election Network Client — Bully via relay'
type: 'feature'
created: '2026-05-13'
status: 'done'
baseline_commit: '5bda519c7e299a140829fbeac5f470e8dff3702a'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `StubElectionNetworkClient` ne transmet jamais les messages ELECTION/ALIVE entre nœuds — les deux nœuds attendent 3s sans réponse, se couronnent tous les deux, ignorant le score de fiabilité. Le nœud au score le plus élevé doit gagner l'élection et le perdant doit rejoindre son cluster.

**Approach:** Ajouter un opcode `ELECTION_BROADCAST (0x10)` au relay qui forward le payload JSON à toutes les sessions connectées sauf l'émetteur. Créer `RelayElectionNetworkClient` qui implémente `IElectionNetworkClient` via ce canal. Démarrer `ProcessIncomingElectionEventUseCase` dans le service pour que le nœud à score supérieur réponde ALIVE et que le perdant reçoive COORDINATOR → FSM JoinEvent.CoordinatorReceived → rejoint le cluster gagnant.

## Boundaries & Constraints

**Always:**
- Conserver `StubElectionNetworkClient` (tests JVM en dépendent) — seul le binding DI change
- Utiliser `java.util.Base64` (pas `android.util.Base64`) pour la sérialisation JSON des `signatureBytes` — garde la couche data testable JVM
- Ne pas modifier `RunBullyElectionUseCase`, `ProcessIncomingElectionEventUseCase` ni `IElectionNetworkClient` — l'implémentation est purement dans la couche data
- Le relay forward ELECTION_BROADCAST à **tous** les nœuds connectés (pas seulement les SPs) — Bully implique tous les nœuds

**Ask First:**
- Si les tests existants de `RunBullyElectionUseCase` deviennent rouges après le rebinding, demander avant de modifier les tests

**Never:**
- Ne pas implémenter de buffering relay pour les messages ELECTION (fire-and-forget, comme SIGNAL)
- Ne pas modifier le protocole d'authentification relay
- Ne pas ajouter de nouveau opcode de réponse (ELECTION_RECEIVED = 0x11 est le seul ajout côté livraison)

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Nœud A score > B, A envoie ELECTION | A.score=0.9, B.score=0.5, relay connecté | B reçoit ELECTION_RECEIVED, ignore (score inférieur) ; A attend 3s sans ALIVE → gagne | — |
| Nœud B score > A, A envoie ELECTION | A.score=0.5, B.score=0.9, relay connecté | B reçoit ELECTION_RECEIVED → ProcessIncomingElection → envoie ALIVE → A reçoit ALIVE → A perd | — |
| Gagnant envoie COORDINATOR | Gagnant FSM=SuperPair, clusterId=UUID | Perdant reçoit COORDINATOR_RECEIVED → ProcessIncomingElection → JoinEvent.CoordinatorReceived → FSM → Joining | — |
| Relay déconnecté au moment de broadcastElectionMessage | `activeWebSocket == null` | `Result.failure(...)` → RunBullyElectionUseCase émet failure et repart en boucle | Loggé, Bully retry après ELECTION_RETRY_DELAY_MS |
| Destination absente (un nœud s'est déconnecté) | nœud non dans sessions | relay ne forward pas (fire-and-forget) | Silence côté relay, timeout Bully côté Android |

</frozen-after-approval>

## Code Map

- `relay-server/server.js` — ajouter MSG.ELECTION_BROADCAST (0x10) + MSG.ELECTION_RECEIVED (0x11) + handleElectionBroadcast()
- `app/.../data/p2p/websocket/RelayMsg.kt` — ajouter ELECTION_BROADCAST + ELECTION_RECEIVED
- `app/.../data/p2p/websocket/RelayWebSocketClient.kt` — ajouter incomingElectionMessages + sendElectionBroadcast + dispatch ELECTION_RECEIVED
- `app/.../data/election/RelayElectionNetworkClient.kt` — nouveau fichier, implémente IElectionNetworkClient
- `app/.../di/ElectionModule.kt` — rebinder IElectionNetworkClient → RelayElectionNetworkClient
- `app/.../data/network/service/MobicloudP2PService.kt` — injecter + démarrer ProcessIncomingElectionEventUseCase

## Tasks & Acceptance

**Execution:**
- [x] `relay-server/server.js` -- ajouter `ELECTION_BROADCAST: 0x10, ELECTION_RECEIVED: 0x11` au const MSG ; ajouter `handleElectionBroadcast(fromNodeId, payload)` qui forward `buildFrame(MSG.ELECTION_RECEIVED, payload)` à toutes les sessions sauf fromNodeId ; ajouter `case MSG.ELECTION_BROADCAST` dans le switch post-auth
- [x] `app/.../data/p2p/websocket/RelayMsg.kt` -- ajouter `const val ELECTION_BROADCAST: Byte = 0x10` et `const val ELECTION_RECEIVED: Byte = 0x11`
- [x] `app/.../data/p2p/websocket/RelayWebSocketClient.kt` -- ajouter `internal val incomingElectionMessages = MutableSharedFlow<ByteArray>(replay=0, extraBufferCapacity=64)` ; ajouter case `ELECTION_RECEIVED` dans onMessage → `flowScope.launch { incomingElectionMessages.emit(payload) }` ; ajouter `fun sendElectionBroadcast(jsonPayload: ByteArray): Boolean` (même pattern que sendSignal)
- [x] `app/.../data/election/RelayElectionNetworkClient.kt` -- créer classe `@Singleton` qui implémente `IElectionNetworkClient` ; `incomingMessages` = `relayClient.incomingElectionMessages.mapNotNull { electionPayloadFromJson(it) }.shareIn(scope, SharingStarted.Eagerly, replay=0)` ; `broadcastElectionMessage(payload)` sérialise en JSON et appelle `relayClient.sendElectionBroadcast(...)` ; helpers `electionPayloadToJson` / `electionPayloadFromJson` avec `java.util.Base64`
- [x] `app/.../di/ElectionModule.kt` -- remplacer `StubElectionNetworkClient` par `RelayElectionNetworkClient` dans le binding `@Binds IElectionNetworkClient`
- [x] `app/.../data/network/service/MobicloudP2PService.kt` -- ajouter `@Inject lateinit var processIncomingElectionEventUseCase: ProcessIncomingElectionEventUseCase` ; dans `startP2PNetworkLoops()` lancer `launch { processIncomingElectionEventUseCase().collect { result -> result.onFailure { Log.d(LOGTAG, "[ELECTION-IN] ${it.message}") } } }`

**Acceptance Criteria:**
- Given deux nœuds connectés au relay avec scores différents, when le Bully démarre (monitoring 20s), then seul le nœud au score le plus élevé envoie REGISTER_PEER (`registeredSuperPeers == 1` dans /health)
- Given nœud A gagne Bully et envoie COORDINATOR, when nœud B reçoit COORDINATOR, then B's FSM transite vers Joining → envoie JOIN_REQUEST à A (`[JOIN] CoordinatorReceived` visible dans logs)
- Given relay déconnecté, when broadcastElectionMessage() appelé, then Result.failure retourné sans crash

## Design Notes

**Sérialisation JSON ElectionPayload :**
```json
{
  "senderNodeId": "abcd1234efgh5678",
  "type": "ELECTION",
  "reliabilityScore": 0.75,
  "signatureBytes": "<base64>",
  "clusterId": "",
  "timestampMs": 1747143600000
}
```
`signatureBytes` encodé avec `java.util.Base64.getEncoder().encodeToString(bytes)` / `java.util.Base64.getDecoder().decode(str)`.

**Pourquoi ProcessIncomingElectionEventUseCase n'était pas démarré :** La classe existe depuis l'Epic 10 mais n'a jamais été collectée dans le service (oversight). Elle contient la logique ALIVE auto-reply + COORDINATOR FSM transition — exactement ce qu'il faut. Pas besoin de modifier la domain layer.

## Verification

**Commands:**
- `./gradlew :app:compileDebugKotlin` -- expected: BUILD SUCCESSFUL, 0 erreurs
- `./gradlew :app:testDebugUnitTest --tests "*Election*"` -- expected: tous les tests Election passent

**Manual checks (if no CLI):**
- Démarrer les deux téléphones → attendre 25s → vérifier `/health` → `registeredSuperPeers == 1`
- Logs Android : chercher `[ELECTION-IN]` sur le nœud perdant + `[JOIN] CoordinatorReceived` dans FSM
- Logs Render : un seul `[SIGNALING] REGISTER super-peer` suivi d'aucun second
