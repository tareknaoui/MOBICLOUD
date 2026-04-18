# Story 3.3 : Abdication Automatique & Buffer d'Urgence Électoral

Status: done

## Story

En tant que Super-Pair,
Je veux abdiquer automatiquement après 30 minutes et protéger les requêtes en transit lors de la transition,
Afin d'éviter l'épuisement de ma batterie et de garantir la continuité du cluster.

## Acceptance Criteria

1. **Given** un nœud est Super-Pair depuis exactement 30 minutes
2. **When** le timer d'abdication expire
3. **Then** le Super-Pair envoie un message `ABDICATION` signé à tous les pairs, déclenchant une nouvelle élection
4. **And** le nœud abdiquant s'exclut automatiquement de la prochaine élection pendant 5 minutes (`cooldownUntil` en mémoire)
5. **And** pendant la transition, les requêtes d'auto-réparation reçues sont enfilées dans un `LocalRepairBuffer` in-memory (max 50 entrées)
6. **And** lorsque le nouveau Super-Pair se déclare, les entrées du buffer sont retransmises dans l'ordre FIFO
7. **And** si le buffer dépasse 50 entrées, les plus anciennes sont droppées avec un log WARNING dans le `RadarLogConsole`

## Dev Agent Guardrails & Context

### Technical Requirements
- Ajouter `ABDICATION` à `ElectionMessageType` (`domain/models/ElectionMessageType.kt`).
- Créer un `LocalRepairBuffer` (`domain/models/LocalRepairBuffer.kt` ou `domain/usecase/m06_m07_repair_migration/LocalRepairBuffer.kt`) capable de stocker une file d'attente FIFO (max 50 entrées). Puisque Epic 7 (Auto-Réparation) n'est pas encore implémenté, utiliser un modèle `data class RepairRequest(val blockId: String, val destinationIp: String, val port: Int)` factice ou minimal.
- Créer `AbdicateSuperPeerUseCase` (dans `m10_election`) : ce use case doit envoyer le message `ABDICATION` via `IElectionNetworkClient`, définir la période de `cooldownUntil`, et retourner un résultat de succès.
- Modifier `ProcessElectionMessageUseCase` (créé en Story 3.1) pour traiter la réception d'un payload `ABDICATION` en retirant au pair sortant le statut de Super-Pair, provoquant ainsi par le monitoring de `RunBullyElectionUseCase` le déclenchement d'une nouvelle élection dans les 5 secondes.
- Intégrer la logique d'attente de 30 minutes dans `MobicloudP2PService`. Après succès d'une élection, déclencher un trigger `delay(30 * 60 * 1000L)` qui appelera `AbdicateSuperPeerUseCase` et ensuite `abdicate()` (la méthode exposée en Story 3.2 pour annuler `superPeerJob`).

### Architecture Compliance
- Ne **JAMAIS** bloquer de thread principal. Tous les delays ou timeouts (`delay(30_000L)`) doivent utiliser les `Coroutines`.
- Les classes de payload comme `RepairRequest` doivent être propres à la Clean Architecture (couche `Domain`).
- Conserver le pattern `Result<T>` pour les Use Cases. Pas d'exception levée brutalement.

### Testing Requirements
- Ajouter des cas de tests unitaires pour `AbdicateSuperPeerUseCase` (vérification de la signature du payload ABDICATION, cooldown de 5 minutes).
- Ajouter des tests pour `LocalRepairBuffer` (overflow à 51 éléments -> element le plus ancien drop, drain sur nouveau Super-Pair).
- Mocker `IElectionNetworkClient` via `StubElectionNetworkClient` modifié pour observer le broadcast d'ABDICATION dans les tests.

### Previous Story Intelligence
- Story 3.2 a exposé `abdicate()` sur `MobicloudP2PService` — on y fera appel pour killer le job `RegisterSuperPeerUseCase` (Firebase Keepalive) lors du déclenchement du timer de l'abdication.
- La signature et la vérification cryptographique via `KeystoreSecurityRepository` doivent être impliquées dans le payload d'ABDICATION (similaire à ELECTION dans Story 3.1).
- Le délai de 30 minutes doit pouvoir être redéfini / paramétré facilement pour pouvoir simuler son expiration par les tests unitaires (`advanceTimeBy`).

### Project Context Reference
- Le timer d'abdication réside fonctionnellement sur la couche limite Network/Domain via le `MobicloudP2PService`.
- Aucune interaction avec la couche Data/Firebase au moment de l'abdication autre que l'annulation du job keepalive qui procèdera elle-même à la suppression via le bloc de catch de `RegisterSuperPeerUseCase`.

## Story Completion Status
Ultimate context engine analysis completed - comprehensive developer guide created.

## Tasks/Subtasks
- [x] Implement Core Data Models (`ElectionMessageType.kt`, `ElectionEvent.kt`, `RepairRequest.kt`)
- [x] Implement In-Memory State & Buffers (`ElectionStateManager.kt`, `LocalRepairBuffer.kt`)
- [x] Repositories Updates (`PeerDao.kt`, `PeerRepository.kt`, `PeerRepositoryImpl.kt`)
- [x] Use Cases Modification (`AbdicateSuperPeerUseCase.kt`, `ProcessIncomingElectionEventUseCase.kt`, `RunBullyElectionUseCase.kt`)
- [x] MobicloudP2PService Updates (`MobicloudP2PService.kt`)
- [x] Tests and Validation (`AbdicateSuperPeerUseCaseTest.kt`, `LocalRepairBufferTest.kt`)

## Dev Agent Record
### Implementation Notes
- **Cooldown Logic:** Created an `ElectionStateManager` (singleton) to track local node cooldown period (5 minutes) after abdication. This prevents participation or initiation of any election flow during that window.
- **Abdication Database Update:** Added `clearSuperPairStatus` in `PeerDao` effectively overriding `is_super_pair` to `0`, to elegantly drop Super-Pair status upon receiving `ABDICATION` and let the reactive monitoring trigger a new election.
- **Repair Buffer Flow:** Used a `LinkedList` constrained at capacity `50` to FIFO drop incoming requests when a Super Pair is missing, and drained upon reception of the new `COORDINATOR` declaration.
- **MobicloudP2PService execution:** Inside loop 7 (`runBullyElectionUseCase().collect`), created a child coroutine bound to `superPeerJob`. It suspends for 30 minutes, fires `abdicateSuperPeerUseCase()` to invoke the broadcast+cooldown logic, and naturally terminates by dropping its own job context.

## File List
- `app/src/main/kotlin/com/mobicloud/data/local/dao/PeerDao.kt`
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/PeerRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/ElectionEvent.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/ElectionMessageType.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/RepairRequest.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/PeerRepository.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/LocalRepairBuffer.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/AbdicateSuperPeerUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/ElectionStateManager.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/ProcessIncomingElectionEventUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RunBullyElectionUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/LocalRepairBufferTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/AbdicateSuperPeerUseCaseTest.kt`

## Change Log
- Ajout fonction abdication automatique après 30 minutes
- Ajout de filter cooldown sur `ProcessIncomingElectionEventUseCase` et `RunBullyElectionUseCase`
- Création buffer circulaire `LocalRepairBuffer` pour `RepairRequest`
- Test exhaustif des use-cases d'abdication et du buffer

### Review Findings

- [x] [Review][Patch] F-01 : Cooldown appliqué avant broadcast — node paralysé si broadcast échoue [`AbdicateSuperPeerUseCase.kt:35`] — fixé
- [x] [Review][Patch] F-02 : `LocalRepairBuffer` viole Clean Architecture (`android.util.Log` + `NetworkEventRepository` dans Domain) [`LocalRepairBuffer.kt:3,5,12`] — fixé
- [x] [Review][Patch] F-03 : `AliveReceived` retourné lors d'un message ELECTION en cooldown sans ALIVE réel envoyé (fausse sémantique) [`ProcessIncomingElectionEventUseCase.kt:58`] — fixé (ajout `object Ignored : ElectionEvent()`)
- [x] [Review][Patch] F-04 : `ElectionStateManager` et timer d'abdication non testables (`System.currentTimeMillis()` hardcodé + `delay` sans paramètre) [`ElectionStateManager.kt:12`, `MobicloudP2PService.kt:303`] — fixé
- [x] [Review][Patch] F-05 : Aucune garde si `abdicateSuperPeerUseCase()` échoue avant `abdicate()` — abdication partielle silencieuse [`MobicloudP2PService.kt:305-307`] — fixé
- [x] [Review][Patch] **F-06 [CRITIQUE] : Aucun re-déclenchement d'élection après abdication — Loop 7 termine après la première émission et ne se relance pas** [`MobicloudP2PService.kt:288`] — fixé
- [x] [Review][Patch] F-08 : `@Synchronized` remplacé par `Mutex` kotlinx.coroutines pour éviter deadlock théorique sous coroutines [`LocalRepairBuffer.kt:17,27`] — fixé
- [x] [Review][Patch] F-12 : Requêtes drainées au COORDINATOR non loggées — AC#6 partiellement non conforme [`ProcessIncomingElectionEventUseCase.kt:149-151`] — fixé (`NetworkEventRepository` injecté, drain loggué vers RadarLogConsole)
- [x] [Review][Defer] F-07 : Buffer de réparation jamais drainé si COORDINATOR n'arrive pas (pas de TTL/drain de secours) [`LocalRepairBuffer.kt`] — deferred, pre-existing (Epic 7 non implémenté)
- [x] [Review][Defer] F-09 : Auto-réception du propre ABDICATION — race condition avec cancel de `superPeerJob` [`ProcessIncomingElectionEventUseCase.kt:81`] — deferred, edge case architectural
- [x] [Review][Defer] F-10 : Cooldown non persisté en cas de kill du process Android [`ElectionStateManager.kt:8`] — deferred, nécessite persistance (DataStore/SharedPrefs)
- [x] [Review][Defer] F-11 : ABDICATION sans vérification que le sender est bien le Super-Pair actuel dans la registry [`ProcessIncomingElectionEventUseCase.kt:85`] — deferred, sécurité defensive à adresser en Epic 3.4
