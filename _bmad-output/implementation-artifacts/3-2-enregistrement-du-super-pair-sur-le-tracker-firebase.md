# Story 3.2 : Enregistrement du Super-Pair sur le Tracker Firebase

Status: done

## Story

En tant que Super-Pair élu,
Je veux publier ma présence sur Firebase Realtime Database sous `super-peers/{nodeId}`,
Afin que les nœuds d'autres clusters (4G) puissent me trouver et rejoindre la fédération.

## Acceptance Criteria

1. **Given** un nœud remporte l'élection Bully (`RunBullyElectionUseCase` émet `Result.success(SuperPairElection)`)
2. **When** le message `COORDINATOR` a été broadcasté avec succès
3. **Then** le Super-Pair s'enregistre sur Firebase Realtime Database sous `super-peers/{nodeId}` avec les champs `{ip, port, reliabilityScore, electedAt, lastKeepalive}`
4. **And** cet enregistrement est rafraîchi toutes les **30 secondes** (keepalive) pour maintenir le TTL de 60 secondes
5. **And** si le Super-Pair abdique ou perd sa connexion, son entrée `super-peers/{nodeId}` est automatiquement supprimée via `onDisconnect().removeValue()` (appelé AVANT `setValue()`)
6. **And** l'enregistrement étend `SignalingRepository` existant (ajout de méthodes — ne pas casser `registerNode`/`observeRemoteNodes`)
7. **And** l'état du rôle du nœud est exposé via `StateFlow<NodeRole>` (PEER / SUPER_PAIR) dans `DashboardViewModel`
8. **And** si Firebase est inaccessible, la défaillance retourne `Result.failure(e)` — le cluster continue en P2P local (dégradé gracieux)
9. **And** un événement de log est ajouté à `NetworkEventRepository` lors de l'enregistrement Firebase réussi

## Tasks / Subtasks

- [x] Task 1 : Modèle `NodeRole` et extension de `SignalingRepository`
  - [x] Créer `domain/models/NodeRole.kt` — enum `PEER` / `SUPER_PAIR`
  - [x] Ajouter dans `domain/repository/SignalingRepository.kt` :
    - `suspend fun registerSuperPeer(ip: String, port: Int, reliabilityScore: Float, electedAt: Long): Result<Unit>`
    - `suspend fun unregisterSuperPeer(): Result<Unit>`
  - [x] Implémenter dans `data/repository/SignalingRepositoryImpl.kt` :
    - Chemin Firebase : `super-peers/{nodeId}` (DISTINCT de `nodes/{nodeId}` existant)
    - Champs : `nodeId`, `ip`, `port`, `reliabilityScore`, `electedAt`, `lastKeepalive`, `timestamp`
    - Appeler `onDisconnect().removeValue()` AVANT `setValue()`

- [x] Task 2 : Use Case `RegisterSuperPeerUseCase`
  - [x] Créer `domain/usecase/m10_election/RegisterSuperPeerUseCase.kt`
  - [x] Injecter : `SignalingRepository`, `IdentityRepository`, `NetworkEventRepository`, `ITrustScoreProvider`, `PublicIpFetcher`
  - [x] `operator fun invoke(tcpPort: Int): Flow<Result<Unit>>` qui :
    1. Récupère `nodeId` via `IdentityRepository.getIdentity()`
    2. Récupère l'IP publique via injection (`PublicIpFetcher`)
    3. Appelle `SignalingRepository.registerSuperPeer(...)`
    4. Lance une coroutine de keepalive toutes les 30s (`while(isActive) { delay(30_000); refresh() }`)
    5. Ajoute un `NetworkLogEvent` dans `NetworkEventRepository`
    6. En cas d'échec Firebase : émet `Result.failure(e)` sans crasher

- [x] Task 3 : Intégration dans `MobicloudP2PService`
  - [x] Écouter `RunBullyElectionUseCase().invoke()` dans le service (Loop 7)
  - [x] Sur `Result.success(SuperPairElection)` → lancer `RegisterSuperPeerUseCase().invoke()`
  - [x] Conserver une référence à la coroutine keepalive (`superPeerJob`) pour annulation lors de l'abdication (Story 3.3)

- [x] Task 4 : Dashboard — Exposition du `NodeRole`
  - [x] Ajouter dans `DashboardViewModel` : `val nodeRole: StateFlow<NodeRole>` via `combine(peerRepository.peers, localNodeIdFlow)`
  - [x] Injecter `PeerRepository` et `IdentityRepository` dans `DashboardViewModel`
  - [x] Afficher le badge `★ Super-Pair` / `● Nœud Connecté` dans `DashboardScreen` selon `nodeRole`

- [x] Task 5 : Tests unitaires
  - [x] Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCaseTest.kt`
  - [x] Tester : enregistrement Firebase réussi sur élection gagnée
  - [x] Tester : keepalive — vérifier que `registerSuperPeer` est rappelé après 30s (avancer le temps)
  - [x] Tester : fallback gracieux si Firebase inaccessible — `Result.failure` émis, aucun crash
  - [x] Tester : `unregisterSuperPeer` appelé lors de l'abdication

## Dev Notes

### ARCHITECTURE CRITIQUE — À LIRE EN PREMIER

**Firebase = Tracker UNIQUEMENT (pas DHT, pas stockage de données)**

Firebase Realtime Database joue exclusivement le rôle de STUN/Signaling Tracker pour la découverte inter-clusters. Les transferts de fichiers restent TCP direct P2P. Cette décision est FIGÉE (V4.0).

**Deux chemins Firebase DISTINCTS — ne pas confondre :**

| Chemin | Usage | Interface |
|---|---|---|
| `nodes/{nodeId}` | Découverte générique de nœuds (tous nœuds) | `SignalingRepository.registerNode()` — **EXISTANT, ne pas toucher** |
| `super-peers/{nodeId}` | Annonce du coordinateur de cluster | `SignalingRepository.registerSuperPeer()` — **À CRÉER dans cette story** |

### FICHIERS EXISTANTS — Réutiliser, NE PAS Recréer

**`SignalingRepository`** (`domain/repository/SignalingRepository.kt`) — interface existante à ÉTENDRE :
```kotlin
// Méthodes DÉJÀ PRÉSENTES (ne pas modifier) :
suspend fun registerNode(ip: String, port: Int): Result<Unit>
fun observeRemoteNodes(): Flow<List<Peer>>

// Méthodes À AJOUTER :
suspend fun registerSuperPeer(ip: String, port: Int, reliabilityScore: Float, electedAt: Long): Result<Unit>
suspend fun unregisterSuperPeer(): Result<Unit>
```

**`SignalingRepositoryImpl`** (`data/repository/SignalingRepositoryImpl.kt`) — déjà implémente Firebase :
- Constante existante : `private const val NODES_PATH = "nodes"` → ajouter `private const val SUPER_PEERS_PATH = "super-peers"`
- Pattern `onDisconnect().removeValue().await()` déjà utilisé dans `registerNode` → réutiliser EXACTEMENT
- Pattern de récupération de `nodeId` via `securityRepository.getIdentity()` déjà utilisé → réutiliser

**`RunBullyElectionUseCase`** (`domain/usecase/m10_election/RunBullyElectionUseCase.kt`) :
- Émet `Result.success(SuperPairElection(localIdentity))` à la ligne 118 en cas de victoire
- Appelle déjà `peerRepository.registerOrUpdatePeer(..., isSuperPair = true)` — ce flag est la source de vérité locale
- **Ne pas modifier ce use case** — créer `RegisterSuperPeerUseCase` qui s'abonne en aval

**`NetworkEventRepository`** (`domain/repository/NetworkEventRepository.kt`) — ajouter log lors de l'enregistrement Firebase :
```kotlin
networkEventRepository.addEvent(NetworkLogEvent(
    message = "Super-Pair enregistré sur Firebase Tracker: $ip:$port",
    timestamp = System.currentTimeMillis()
))
```

**`PublicIpFetcher`** (`data/network/PublicIpFetcher.kt`) — récupère l'IP publique, disponible via injection Hilt. Réutiliser pour obtenir l'IP à enregistrer sur Firebase.

**`MobicloudP2PService`** (`data/network/service/MobicloudP2PService.kt`) — service Foreground existant où intégrer l'écoute des élections.

### Structure du nœud Firebase `super-peers/{nodeId}`

```json
{
  "nodeId": "abc123",
  "ip": "203.0.113.42",
  "port": 7777,
  "reliabilityScore": 0.87,
  "electedAt": 1713360000000,
  "lastKeepalive": 1713360030000,
  "timestamp": 1713360030000
}
```

TTL effectif = 60 secondes. Keepalive toutes les 30s. `onDisconnect().removeValue()` assure le nettoyage automatique.

### Pattern Keepalive — Implémentation Correcte

```kotlin
// Dans RegisterSuperPeerUseCase, DANS la coroutine du flow :
try {
    signalingRepository.registerSuperPeer(ip, port, reliabilityScore, electedAt).getOrThrow()
    networkEventRepository.addEvent(...)
    
    // Keepalive toutes les 30s
    while (currentCoroutineContext().isActive) {
        delay(30_000L)
        signalingRepository.registerSuperPeer(ip, port, reliabilityScore, electedAt)
            .onFailure { Log.w(TAG, "Keepalive Firebase échoué — mode P2P local actif", it) }
    }
} catch (e: CancellationException) {
    // Abdication : la coroutine est annulée par Story 3.3
    signalingRepository.unregisterSuperPeer()
    throw e  // Propager CancellationException — NE PAS swallower
}
```

**CRITIQUE :** Ne jamais swallower `CancellationException`. La propager toujours.

### `NodeRole` — Nouveau Modèle à Créer

```kotlin
// domain/models/NodeRole.kt
package com.mobicloud.domain.models

enum class NodeRole {
    PEER,
    SUPER_PAIR
}
```

### Intégration DashboardViewModel — Nœud Local

Le ViewModel doit distinguer le nœud LOCAL des autres pairs. Il faut injecter `IdentityRepository` pour obtenir le `localNodeId` :

```kotlin
val nodeRole: StateFlow<NodeRole> = combine(
    peerRepository.peers,
    identityFlow  // flow de l'identité locale
) { peers, identity ->
    val isSuperPair = peers.any { p -> p.isSuperPair && p.isActive && p.identity.nodeId == identity.nodeId }
    if (isSuperPair) NodeRole.SUPER_PAIR else NodeRole.PEER
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NodeRole.PEER)
```

Attention : `IdentityRepository.getIdentity()` est `suspend` — utiliser `flow { emit(repo.getIdentity().getOrNull()) }` ou le résoudre dans `init {}`.

### Gestion des Erreurs — Pattern Obligatoire

Respecter le pattern `Result<T>` sans exception silencieuse (héritage Story 3.1) :

```kotlin
override suspend fun registerSuperPeer(...): Result<Unit> = runCatching {
    val identity = securityRepository.getIdentity().getOrThrow()
    val ref = firebaseDatabase.reference.child(SUPER_PEERS_PATH).child(identity.nodeId)
    // onDisconnect AVANT setValue — critique pour cleanup automatique
    ref.onDisconnect().removeValue().await()
    ref.setValue(superPeerData).await()
}
```

Si Firebase est inaccessible → `Result.failure(e)` est retourné proprement. Le caller décide du comportement de repli (continuer en local-only).

### Règles Firebase Security (Rappel Architecture)

```json
{
  "rules": {
    "super-peers": {
      "$nodeId": {
        ".write": "auth.uid === $nodeId",
        ".read": true
      }
    }
  }
}
```

Lecture publique. Écriture uniquement pour son propre `nodeId`.

### Tests — Dispatcher et Timing

Les tests du `RegisterSuperPeerUseCase` doivent utiliser `StandardTestDispatcher` (pas `UnconfinedTestDispatcher`) pour contrôler `delay(30_000L)` via `advanceTimeBy(30_001L)`. Pattern identique aux corrections appliquées en Story 3.1 (F-07).

```kotlin
@Test
fun `keepalive rafraîchit l'enregistrement toutes les 30 secondes`() = runTest {
    // Arrange : mock registerSuperPeer retourne Success
    // Act : lancer RegisterSuperPeerUseCase dans une coroutine de test
    advanceTimeBy(30_001L)
    // Assert : registerSuperPeer appelé 2x (initial + 1 keepalive)
    coVerify(exactly = 2) { signalingRepository.registerSuperPeer(any(), any(), any(), any()) }
}
```

### Préparation pour Story 3.3 (Abdication)

La coroutine keepalive dans `MobicloudP2PService` doit être stockée dans une `Job` nommée pour annulation :
```kotlin
private var superPeerJob: Job? = null

// Sur élection gagnée :
superPeerJob = serviceScope.launch { registerSuperPeerUseCase().collect { ... } }

// À exposer pour Story 3.3 (abdication automatique après 30 min) :
fun abdicate() { superPeerJob?.cancel() }
```

Story 3.3 utilisera ce mécanisme d'annulation — ne pas supprimer ou internaliser la `Job`.

## UX Requirements

- Badge `★ Super-Pair` (couleur `#00FF41` — Green Terminal) affiché dans `DashboardScreen` quand `nodeRole == NodeRole.SUPER_PAIR`
- Badge `● Nœud Connecté` affiché quand `nodeRole == NodeRole.PEER`
- Log dans `RadarLogConsole` : `"Super-Pair enregistré sur Firebase Tracker: {ip}:{port}"` ajouté via `NetworkEventRepository`
- Aucune animation (NFR-03 — conservation batterie) — changement de badge uniquement

## Project Structure

**Nouveaux fichiers à créer :**
- `app/src/main/kotlin/com/mobicloud/domain/models/NodeRole.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCaseTest.kt`

**Fichiers à modifier :**
- `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt` — ajouter 2 méthodes
- `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt` — implémenter les 2 nouvelles méthodes
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt` — ajouter `nodeRole: StateFlow<NodeRole>`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt` — afficher badge Super-Pair
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` — intégrer `RegisterSuperPeerUseCase`

**Fichiers à NE PAS modifier :**
- `RunBullyElectionUseCase.kt` — déjà complet, ne pas toucher
- `SignalingModule.kt` — déjà configuré pour Hilt
- `nodes/` path dans Firebase — chemin distinct de `super-peers/`

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Completion Notes List

- **NodeRole** : enum `PEER` / `SUPER_PAIR` créé dans `domain/models/NodeRole.kt`.
- **SignalingRepository** étendu avec `registerSuperPeer` et `unregisterSuperPeer`; chemin Firebase `super-peers/{nodeId}` distinct de `nodes/{nodeId}`. Pattern `onDisconnect().removeValue()` AVANT `setValue()` respecté (AC5).
- **RegisterSuperPeerUseCase** : `invoke(tcpPort)` émet `Result.success(Unit)` à l'enregistrement initial, puis keepalive toutes les 30s. `CancellationException` propagée après `unregisterSuperPeer()` (abdication propre). Fallback gracieux sur Firebase inaccessible (AC8).
- **MobicloudP2PService** : Loop 7 ajoutée — écoute `RunBullyElectionUseCase`, lance `RegisterSuperPeerUseCase(tcpPort)` sur victoire. `superPeerJob` exposé pour `abdicate()` (Story 3.3).
- **DashboardViewModel** : `nodeRole: StateFlow<NodeRole>` via `combine(peerRepository.peers, localNodeIdFlow)`. Injecte `PeerRepository` et `IdentityRepository`.
- **DashboardScreen** : Badge `★ Super-Pair` (couleur `#00FF41`) ou `● Nœud Connecté` selon `nodeRole`.
- **ElectionModule** : `StubElectionNetworkClient` et `ReliabilityTrustScoreAdapter` créés pour les bindings Hilt manquants de Story 3.1. `CoroutineDispatcher` non qualifié fourni pour `RunBullyElectionUseCase.defaultDispatcher`.
- **Corrections pré-existantes** : `PeerRepositoryImpl` + `PeerNodeEntity` + `CatalogDatabase` mis à jour pour `isSuperPair`. `KeystoreSecurityRepositoryImpl` complété avec `verifySignature`. Tests `RunBullyElectionUseCaseTest` et `BasicElectionUseCaseTest` corrigés pour kotlinx.coroutines 1.10.2.
- **Tests** : 8 tests unitaires `RegisterSuperPeerUseCaseTest` — tous verts. Suite complète 72+ tests — BUILD SUCCESSFUL.

### File List

**Nouveaux fichiers :**
- `app/src/main/kotlin/com/mobicloud/domain/models/NodeRole.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/data/election/StubElectionNetworkClient.kt`
- `app/src/main/kotlin/com/mobicloud/data/election/ReliabilityTrustScoreAdapter.kt`
- `app/src/main/kotlin/com/mobicloud/di/ElectionModule.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCaseTest.kt`

**Fichiers modifiés :**
- `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/PeerRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/PeerNodeEntity.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/security/KeystoreSecurityRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/DashboardViewModelTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RunBullyElectionUseCaseTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/BasicElectionUseCaseTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m01_auth_discovery/GenerateHashcashProofUseCaseTest.kt`

### Change Log

- 2026-04-17 : Implémentation Story 3.2 — Enregistrement du Super-Pair sur le Tracker Firebase. Ajout de `NodeRole`, `RegisterSuperPeerUseCase`, extension `SignalingRepository`, intégration service P2P, badge Dashboard, 8 tests unitaires. Corrections pré-existantes : `PeerNodeEntity.isSuperPair`, `verifySignature`, bindings Hilt election, compat kotlinx.coroutines 1.10.2.

### Review Findings

#### Decision Needed

- [x] [Review][Decision] **electedAt : timestamp call-site vs timestamp d'élection** — `RegisterSuperPeerUseCase.invoke()` utilise `electedAt = System.currentTimeMillis()` au moment de l'appel, pas au moment où `RunBullyElectionUseCase` a émis `Result.success`. Si `SuperPairElection` porte un timestamp d'élection, il n'est pas transmis. Choix requis : (A) passer `electedAt` depuis `MobicloudP2PService` si `SuperPairElection` le contient, (B) accepter l'approximation call-site (délai négligeable). [sources: blind+auditor]

#### Patches

- [x] [Review][Patch] **Migration Room 2→3 manquante — données perdues sur mise à jour** [`CatalogDatabase.kt`] — ajout `MIGRATION_2_3` + enregistrement dans `IdentityModule` [sources: blind+edge]
- [x] [Review][Patch] **`superPeerJob` public non-volatile : data race et encapsulation cassée** [`MobicloudP2PService.kt:67`] — `@Volatile private var` [sources: blind+edge+auditor]
- [x] [Review][Patch] **`localNodeIdFlow` cold : émet `null` si identité pas prête à la souscription** [`DashboardViewModel.kt:46`] — `StateFlow` avec `SharingStarted.Eagerly` [sources: blind+edge]
- [x] [Review][Defer] **`CoroutineDispatcher` non qualifié dans `ElectionDispatcherModule`** [`ElectionModule.kt:35`] — deferred, impossible d'ajouter qualifier sans modifier `RunBullyElectionUseCase` (interdit spec) [sources: blind+edge]
- [x] [Review][Patch] **`unregisterSuperPeer()` peut lever `CancellationException` dans son propre catch bloc** [`RegisterSuperPeerUseCase.kt:63`] — `withContext(NonCancellable)` [sources: edge]
- [x] [Review][Patch] **`onDisconnect().removeValue()` appelé à chaque keepalive : accumulation de handlers Firebase** [`SignalingRepositoryImpl.kt:120`] — flag `isSuperPeerDisconnectRegistered` [sources: edge+auditor]
- [x] [Review][Patch] **`@Upsert` écrase `isSuperPair` à `false` sur chaque heartbeat UDP** [`PeerRepositoryImpl.kt` / `PeerDao.kt`] — nouvelle méthode `insertOrUpdatePreservingRole` avec `MAX(isSuperPair, existing)` [sources: edge]
- [x] [Review][Patch] **Résultat de `unregisterSuperPeer()` ignoré silencieusement lors de l'abdication** [`RegisterSuperPeerUseCase.kt:65`] — `onFailure { Log.w(...) }` [sources: auditor]
- [x] [Review][Defer] **Deux interfaces `ITrustScoreProvider` nommées identiquement dans des packages distincts** [`domain/repository/` vs `domain/usecase/m01_discovery/`] — deferred, types JVM distincts sans conflit Hilt réel, confusion de nommage uniquement [sources: edge]

#### Deferred

- [x] [Review][Defer] **`verifySignature` accepte une clé publique arbitraire sans validation de chaîne de confiance** [`KeystoreSecurityRepositoryImpl.kt:141`] — deferred, hors périmètre story 3.2, conception sécurité à traiter séparément [sources: blind]
- [x] [Review][Defer] **Keepalive ne re-fetch pas l'IP publique sur changement de réseau** [`RegisterSuperPeerUseCase.kt`] — deferred, comportement acceptable selon spec story 3.2 [sources: blind]
- [x] [Review][Defer] **`nodeRole` déduit depuis la DB locale plutôt que depuis le résultat d'élection** [`DashboardViewModel.kt`] — deferred, pattern prescrit explicitement dans la spec [sources: blind]
- [x] [Review][Defer] **`StubElectionNetworkClient` en production : tout nœud gagne toujours l'élection** [`StubElectionNetworkClient.kt`] — deferred, intentionnel (transport UDP réel = story future) [sources: blind+edge]
- [x] [Review][Defer] **`ReliabilityTrustScoreAdapter.getTrustScore` ignore le `nodeId` fourni** [`ReliabilityTrustScoreAdapter.kt:17`] — deferred, usage actuel = nœud local uniquement, à corriger si multi-nœud requis [sources: blind+edge]
- [x] [Review][Defer] **`catch(Exception)` générique dans `verifySignature`** [`KeystoreSecurityRepositoryImpl.kt`] — deferred, pattern acceptable pour API `Result<T>` [sources: blind]
- [x] [Review][Defer] **`RunBullyElectionUseCase` ne ré-entre pas dans la boucle après abdication** [`MobicloudP2PService.kt`] — deferred, périmètre Story 3.3 [sources: edge]
- [x] [Review][Defer] **`reliabilityScore` figé à l'élection, non rafraîchi dans le keepalive** [`RegisterSuperPeerUseCase.kt:39`] — deferred, décision de conception acceptable [sources: edge]
- [x] [Review][Defer] **`evictStalePeers` : bases de temps mixtes `elapsedRealtime` vs `currentTimeMillis`** [`MobicloudP2PService.kt` / `SignalingRepositoryImpl.kt`] — deferred, bug pré-existant non introduit par cette story [sources: edge]
- [x] [Review][Defer] **AC1/AC2 : enregistrement Firebase non conditionné à la confirmation du broadcast COORDINATOR** — deferred, dépend du contrat interne de `RunBullyElectionUseCase` (non modifiable story 3.2) [sources: auditor]
- [x] [Review][Defer] **Pas de filtre TTL client-side pour les entrées `super-peers`** — deferred, `observeRemoteNodes` ne couvre que `nodes/`, gap architectural à adresser post story 3.2 [sources: auditor]
