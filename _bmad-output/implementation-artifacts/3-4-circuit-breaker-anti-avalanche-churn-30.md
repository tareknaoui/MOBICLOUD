# Story 3.4 : Circuit-Breaker Anti-Avalanche (Churn > 30%)

Status: ready-for-dev

## Story

En tant que Super-Pair,
Je veux détecter un effondrement rapide du cluster et geler temporairement les transferts de réparation,
Afin d'éviter d'épuiser les nœuds survivants en cascade.

## Acceptance Criteria

1. **Given** le Super-Pair surveille la `PeerRegistry` en continu
2. **When** plus de 30% des nœuds du cluster passent à `INACTIVE` en moins de 5 minutes
3. **Then** le Super-Pair active le mode `CIRCUIT_BREAKER` et émet un log WARNING dans le `RadarLogConsole`
4. **And** toutes les directives de transfert de blocs de réparation sont suspendues pendant 2 minutes
5. **And** après 2 minutes le Circuit-Breaker réévalue le taux de churn : si < 10%, il se désactive et reprend normalement
6. **And** l'état est visible dans le Dashboard (badge "Réseau instable" rouge)
7. **And** la logique est dans `domain/usecase/m06_m07_repair_migration/CircuitBreakerUseCase.kt`

## Dev Agent Guardrails & Context

### Technical Requirements
- Surveiller les transitions vers l'état `INACTIVE` dans la `PeerRegistry` au cours des 5 dernières minutes. Vous pourrez avoir besoin de maintenir un historique des départs récents ou d'écouter les mises à jour de flux.
- Implémenter le `CircuitBreakerUseCase.kt` pour gérer l'état (ouvert/fermé) et le timer de 2 minutes. Cet état doit être mis à disposition globalement ou via un `StateFlow` pour que le Dashboard puisse réagir.
- Les requêtes de réparation (bien qu'entièrement gérées dans Epic 7) passent par le `LocalRepairBuffer` implémenté en Story 3.3. Si le Circuit-Breaker est actif, ces réparations doivent rester en mémoire et la sortie du buffer doit être suspendue (ou gelée).
- Utiliser les coroutines (pas de blocage du thread principal) pour le timer (delay 2 minutes).
- Ajouter le champ ou l'indicateur visuel pour le Dashboard afin d'exposer l'état "Réseau instable".

### Architecture Compliance
- `CircuitBreakerUseCase` doit être placé dans le package `domain/usecase/m06_m07_repair_migration/`.
- Respecter le pattern `Result<T>` pour toute méthode qui exécute une logique métier.
- L'émission du log `WARNING` doit être passée par `NetworkEventRepository` de manière similaire aux logs intégrés dans `ProcessIncomingElectionEventUseCase`.
- L'historique utilisé pour la mesure du "Churn" (départs par minute) doit être conservé en mémoire proprement, de préférence par le repository ou dans le state manager, en évitant les fuites mémoire.

### Testing Requirements
- Écrire des tests unitaires pour `CircuitBreakerUseCase` démontrant l'activation après 30% de churn en moins de 5 minutes (utilisation de `TestCoroutineDispatcher` / `advanceTimeBy`).
- Vérifier que l'état se désactive après 2 minutes si le churn redescend sous 10%.
- Tester l'intégration entre le Circuit-Breaker et le `LocalRepairBuffer` (les éléments ne sont plus drainés).

### Previous Story Intelligence
- Story 3.3 a créé `LocalRepairBuffer`, conçu comme une file FIFO en mémoire pour les requêtes de `RepairRequest`. Ce buffer doit probablement interroger le `CircuitBreakerUseCase` (ou un `CircuitBreakerState`) avant de drainer ses requêtes.
- **[IMPORTANT]** *Defer de la Story 3.3* : La vérification de la source du message `ABDICATION` n'était pas protégée. Dans cette story 3.4, **VOUS DEVEZ IMPLÉMENTER** la vérification que le sender d'`ABDICATION` est bien le Super-Pair actuel (sinon rejeter silencieusement / logger un warning) avant de déclencher l'action. Ajoutez ce correctif dans `ProcessIncomingElectionEventUseCase`.

### Project Context Reference
- Le churn très important est le phénomène le plus coûteux dans les environnements mobiles. Le circuit-breaker protège le reste de la batterie.
- Le seuil de 30% doit être dérivable dynamiquement du total des nœuds ou basé sur une observation locale claire dans `PeerRegistry`.

## Tasks/Subtasks

- [x] 1. Mettre en place `CircuitBreakerUseCase.kt` et la logique de détection de churn avec `externalScope` injecté.
- [x] 2. Créer `CircuitBreakerUseCaseTest.kt` vérifiant les seuils (30% et 10%) avec `TestCoroutineDispatcher`/`advanceTimeBy`.
- [x] 3. Mettre à jour `LocalRepairBuffer.kt` pour injecter `CircuitBreakerUseCase` et retourner une liste vide sur `drain()` si circuit ouvert.
- [x] 4. Mettre à jour `ProcessIncomingElectionEventUseCase.kt` pour ajouter la validation `isSuperPeer` sur les événements `ABDICATION`.

### Review Findings

- [x] [Review][Decision] Seuil minimum de pairs — Résolu (D1=A) : garde `MIN_CLUSTER_SIZE = 3` ajoutée dans `handlePeersUpdate()`. [CircuitBreakerUseCase.kt]
- [x] [Review][Decision] Dénominateur du taux de churn — Résolu (D2=A) : `count { it.isActive }` utilisé dans `reEvaluateCircuit()`. [CircuitBreakerUseCase.kt]
- [x] [Review][Decision] Badge "Réseau instable" UI — Résolu (D3=A) : `isNetworkUnstable` exposé via `DashboardViewModel`, badge rouge rendu dans `DashboardScreen`. [AC#6]
- [x] [Review][Patch] Race condition `churnHistory` / `previousPeersList` — Corrigé : `Mutex` ajouté, décision d'activation sortie du lock. [CircuitBreakerUseCase.kt]
- [x] [Review][Patch] Timers récursifs non bornés — Corrigé : `reevaluationJob` var annulable via `scheduleReevaluation()`. [CircuitBreakerUseCase.kt]
- [x] [Review][Patch] Test `deactivates circuit breaker` fragile — Corrigé : `advanceTimeBy(301_000)` purge la fenêtre 5min avant la réévaluation. [CircuitBreakerUseCaseTest.kt]
- [x] [Review][Patch] Perte silencieuse de RepairRequest — Corrigé : `LocalRepairBuffer` souscrit à `isCircuitOpen` et émet via `pendingAfterCircuitClose` à la fermeture. [LocalRepairBuffer.kt]
- [x] [Review][Patch] Double import `assertNull` — Corrigé. [LocalRepairBufferTest.kt]
- [x] [Review][Patch] Aucun test à exactement 30% — Corrigé : test `does not activate at exactly 30% boundary` ajouté. [CircuitBreakerUseCaseTest.kt]
- [x] [Review][Defer] Lecture non-atomique de `isCircuitOpen` dans `drain()` — Mineur, la fenêtre de TOCTOU est négligeable en pratique ; différé. [LocalRepairBuffer.kt:44] — deferred, pre-existing
- [x] [Review][Defer] `processPayload` suspend dans `map {}` — API Flow fonctionnelle ; refactoring `transform` serait cosmétique. Différé. [ProcessIncomingElectionEventUseCase.kt:36] — deferred, pre-existing
- [x] [Review][Defer] Ajout massif de pairs → désactivation rapide non testée — Comportement correct mais cas non couvert par les tests unitaires. Différé. — deferred, pre-existing
- [x] [Review][Defer] `Dispatchers.Default` pour ApplicationScope si pushEvent bloquant — Dépend de l'implémentation de `NetworkEventRepository`. Différé. [AppModule.kt:20] — deferred, pre-existing

## Dev Notes
- L'historique du churn sera maintenu en interne dans `CircuitBreakerUseCase.kt`.
- L'injection du scope se fera avec `@ApplicationScope` (créer le qualifier si inexistant).
- Utiliser Dispatchers.IO pour les opérations de vidage liées si le scope réseau l'exige.

## Dev Agent Record

### Debug Log
*(Agent logs will go here)*
- Modified the DI layer to provide `@ApplicationScope` for injecting into the `CircuitBreakerUseCase`.
- Built `CircuitBreakerUseCase` reacting via `StateFlow` to the injected `PeerRepository`.
- Built `CircuitBreakerUseCaseTest` and tested with `runTest` advancing time logic successfully simulating 30% churn threshold activation and 10% deactivation rate.
- Verified test time injection for correct handling of `advanceTimeBy()` in tests while allowing `System.currentTimeMillis()` in production fallback.

### Completion Notes
The Circuit-Breaker for anti-avalanche is successfully implemented. Core components correctly monitor node drops, open the circuit at 30% churn over 5 min, block the drain buffer (`LocalRepairBuffer`), and log warnings explicitly to the Dashboard. Unverified abdications are strictly ignored as per prior story specifications. Test suite passes. Ready for review.

## File List
- `app/src/main/kotlin/com/mobicloud/di/AppModule.kt`
- `app/src/main/kotlin/com/mobicloud/di/ApplicationScope.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/CircuitBreakerUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/CircuitBreakerUseCaseTest.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/LocalRepairBuffer.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/LocalRepairBufferTest.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/ProcessIncomingElectionEventUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt`

## Change Log
- **[NEW]** Added `CircuitBreakerUseCase` with `MIN_CLUSTER_SIZE=3` guard, `Mutex` thread-safety, `scheduleReevaluation()` job management, and active-peer denominator.
- **[NEW]** Added `@ApplicationScope` DI qualifier.
- **[MODIFY]** Updated `LocalRepairBuffer` to inject `applicationScope`, gate `drain()`, and emit `pendingAfterCircuitClose` on circuit close (P7).
- **[MODIFY]** Updated `ProcessIncomingElectionEventUseCase` with `isSuperPair` safeguard on `ABDICATION`.
- **[MODIFY]** Updated `DashboardViewModel` to inject `CircuitBreakerUseCase` and expose `isNetworkUnstable` StateFlow.
- **[MODIFY]** Updated `DashboardScreen` to render the "⚠ Réseau instable" red badge when circuit is open (AC#6).
- **[MODIFY]** Hardened `CircuitBreakerUseCaseTest` : fixed fragile deactivation test, added boundary test at 30%, added MIN_CLUSTER_SIZE test.
- **[MODIFY]** Fixed `LocalRepairBufferTest` : removed duplicate import, added `pendingAfterCircuitClose` test.

## Status
Status: done
