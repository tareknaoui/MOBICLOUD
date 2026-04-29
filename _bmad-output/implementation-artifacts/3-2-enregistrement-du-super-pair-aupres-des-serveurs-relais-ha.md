# Story 3.2 : Enregistrement du Super-Pair auprès des Serveurs Relais HA

Status: done

> **Note de pivot V5.0 (2026-04-28)** : Cette story remplace l'ancienne 3.2 Firebase. Firebase est totalement supprimé ; la signalisation passe par les **Serveurs Relais HA WebSocket** définis à l'Epic 8. Le fichier historique `3-2-enregistrement-du-super-pair-sur-le-tracker-firebase.md` documente l'implémentation Firebase (obsolète).

---

## Story

En tant que Super-Pair élu,
Je veux publier ma présence auprès des Serveurs Relais HA,
Afin que les nœuds d'autres clusters (4G ou WiFi distinct) puissent me trouver et rejoindre la fédération.

## Acceptance Criteria

1. **Given** un nœud remporte l'élection Bully et devient Super-Pair
2. **When** le message `COORDINATOR` est envoyé
3. **Then** le Super-Pair envoie un message `REGISTER_PEER` au Serveur Relais HA avec `{nodeId, ip, port, reliabilityScore, electedAt}` — l'authenticité est garantie par le frame AUTH EC P-256 signé à l'ouverture de session WSS (pas de signature par message)
4. **And** cet enregistrement est rafraîchi toutes les **30 secondes** (keepalive PING) pour maintenir le TTL en RAM (TTL serveur = 60 secondes)
5. **And** si le Super-Pair abdique ou que la connexion WSS se ferme, l'annuaire HA purge automatiquement l'entrée après expiration TTL (60s)
6. **And** l'enregistrement réutilise `SignalingRepository` (impl HA WebSocket `SignalingRepositoryImpl`) défini à l'Epic 2 — **ne pas réimplémenter**
7. **And** en cas d'échec d'enregistrement, le client bascule sur le serveur HA suivant (failover séquentiel géré dans `RelayWebSocketClient`)
8. **And** l'état Super-Pair est exposé via `StateFlow<NodeRole>` (PEER / SUPER_PAIR) dans `DashboardViewModel` et affiché dans `DashboardScreen`
9. **And** un événement de log est poussé dans `NetworkEventRepository` via `pushEvent` lors de l'enregistrement HA réussi

## Tasks / Subtasks

- [x] Task 1 : Vérifier l'interface `SignalingRepository` (déjà définie Epic 2 / Epic 8)
  - [x] Confirmer présence de `registerAsSuperPeer(ip, port, reliabilityScore, electedAt): Result<Unit>`
  - [x] Confirmer présence de `unregisterAsSuperPeer(): Result<Unit>`
  - [x] Confirmer présence de `fetchActiveSuperPeers(): Result<Unit>`

- [x] Task 2 : Implémenter `RegisterSuperPeerUseCase`
  - [x] Créer `domain/usecase/m10_election/RegisterSuperPeerUseCase.kt`
  - [x] Injecter : `SignalingRepository`, `IdentityRepository`, `NetworkEventRepository`, `ITrustScoreProvider`, `PublicIpFetcher`
  - [x] `operator fun invoke(tcpPort: Int, electedAt: Long): Flow<Result<Unit>>` :
    1. Récupère `nodeId` via `identityRepository.getIdentity()`
    2. Récupère score via `trustScoreProvider.getTrustScore(nodeId)`
    3. Récupère IP publique via `publicIpFetcher.fetchPublicIp()`
    4. Appelle `signalingRepository.registerAsSuperPeer(...)`
    5. Pousse log dans `networkEventRepository.pushEvent("[ELECTION] Super-Pair enregistré sur Relais HA: $ip:$tcpPort")`
    6. Émet `Result.success(Unit)` puis entre dans la boucle keepalive toutes les 30s
    7. Sur `CancellationException` : appelle `unregisterAsSuperPeer()` dans `withContext(NonCancellable)` puis propage
  - [x] Ne jamais swallower `CancellationException`

- [x] Task 3 : Intégration dans `MobicloudP2PService`
  - [x] Écouter `RunBullyElectionUseCase` (Loop 7 existant)
  - [x] Sur `Result.success(SuperPairElection)` → lancer `RegisterSuperPeerUseCase(tcpPort, electedAt)`
  - [x] Stocker la coroutine dans `@Volatile private var superPeerJob: Job?`
  - [x] Exposer `abdicate()` : `superPeerJob?.cancel()` (utilisé par Story 3.3)

- [x] Task 4 : Dashboard — Exposition du `NodeRole`
  - [x] Ajouter `val nodeRole: StateFlow<NodeRole>` dans `DashboardViewModel`
  - [x] Dériver via `combine(peerRepository.peers, localNodeIdFlow) { peers, id -> … }`
  - [x] `localNodeIdFlow` = `StateFlow` Eagerly (jamais null après init)
  - [x] Afficher badge `★ Super-Pair` (`#00FF41`) ou `● Nœud Connecté` dans `DashboardScreen`

- [x] Task 5 : Tests unitaires
  - [x] Créer `RegisterSuperPeerUseCaseTest.kt` avec `StandardTestDispatcher`
  - [x] Tester : enregistrement HA réussi → `Result.success` + log event
  - [x] Tester : keepalive → `registerAsSuperPeer` rappelé après 30s (`advanceTimeBy(30_001L)`)
  - [x] Tester : deux keepalives après 60s (3 appels total)
  - [x] Tester : échec Relais HA → `Result.failure` sans crash
  - [x] Tester : abdication → `unregisterAsSuperPeer()` appelé lors de l'annulation
  - [x] Tester : échec `getIdentity()` → `Result.failure`, `registerAsSuperPeer` jamais appelé
  - [x] Tester : échec `fetchPublicIp()` → `Result.failure`, `registerAsSuperPeer` jamais appelé
  - [x] Créer `SignalingRepositoryImplTest.kt`
  - [x] Tester : `registerAsSuperPeer` → `Result.success` quand `sendRegisterPeer` retourne `true`
  - [x] Tester : `registerAsSuperPeer` → `Result.failure` + log quand `sendRegisterPeer` retourne `false`
  - [x] Tester : `fetchActiveSuperPeers` → `Result.success` quand `sendGetPeers` retourne `true`
  - [x] Tester : `fetchActiveSuperPeers` → `Result.failure` + log quand injoignable
  - [x] Tester : `processPeerList` filtre entrées TTL > 60s
  - [x] Tester : `processPeerList` insère peers valides avec `source = RELAY_HA`

---

## Dev Notes

### ARCHITECTURE CRITIQUE — À LIRE EN PREMIER

**V5.0 Zero-Firebase : signalisation 100% via Serveurs Relais HA**

Depuis le pivot V5.0, Firebase est totalement absent du projet. La signalisation inter-clusters repose exclusivement sur un cluster de **min 2 instances Node.js** (`RelayWebSocketClient`). L'annuaire des Super-Pairs est en RAM côté serveur avec TTL 60s.

**Deux rôles distincts de `SignalingRepository` :**

| Méthode | Rôle |
|---|---|
| `registerAsSuperPeer(ip, port, score, electedAt)` | Annonce du Super-Pair élu auprès des Serveurs Relais HA |
| `fetchActiveSuperPeers()` | Récupère la liste des Super-Pairs d'autres clusters (GET_PEERS) |
| `unregisterAsSuperPeer()` | Abdication explicite (TTL serveur purge de toute façon après 60s) |

### FICHIERS EXISTANTS — Réutiliser, NE PAS Recréer

**`SignalingRepository`** (`domain/repository/SignalingRepository.kt`) — **DÉJÀ DÉFINI, ne pas modifier** :
```kotlin
interface SignalingRepository {
    suspend fun registerAsSuperPeer(ip: String, port: Int, reliabilityScore: Float, electedAt: Long): Result<Unit>
    suspend fun fetchActiveSuperPeers(): Result<Unit>
    suspend fun unregisterAsSuperPeer(): Result<Unit>
}
```

**`SignalingRepositoryImpl`** (`data/repository/SignalingRepositoryImpl.kt`) — **DÉJÀ IMPLÉMENTÉ** :
- Délègue à `relayClient.sendRegisterPeer(ip, port, score, electedAt)` → retourne `Boolean`
- Si `false` → `error("RelayWebSocketClient non connecté")` → `Result.failure`
- `processPeerList()` : filtre TTL > 60s, insère via `peerRepository.registerOrUpdatePeer(..., source = RELAY_HA, isSuperPair = true)`

**`RelayWebSocketClient`** (`data/p2p/websocket/RelayWebSocketClient.kt`) — client HA WebSocket :
- `fun sendRegisterPeer(ip, port, score, electedAt): Boolean` — envoie message binaire au relais actif
- `fun sendGetPeers(): Boolean` — demande la liste des Super-Pairs
- `fun connect(url: String): Flow<RelayEvent>` — écoute les événements relais (PeerList, Disconnected…)
- Failover multi-instances géré en interne (`RELAY_SERVER_URLS` dans companion)

**`MobicloudP2PService`** (`data/network/service/MobicloudP2PService.kt`) — service Foreground existant :
- Injecte déjà `RegisterSuperPeerUseCase` et `AbdicateSuperPeerUseCase`
- `@Volatile private var superPeerJob: Job?` — référence à annuler sur abdication

**`DashboardViewModel`** (`presentation/dashboard/DashboardViewModel.kt`) — **DÉJÀ IMPLÉMENTÉ** :
```kotlin
val nodeRole: StateFlow<NodeRole> = combine(
    peerRepository.peers,
    localNodeIdFlow
) { peers, localNodeId ->
    if (localNodeId != null && peers.any { p -> p.isSuperPair && p.isActive && p.identity.nodeId == localNodeId })
        NodeRole.SUPER_PAIR else NodeRole.PEER
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NodeRole.PEER)
```

### Pattern Keepalive — Implémentation Correcte

```kotlin
operator fun invoke(tcpPort: Int, electedAt: Long = System.currentTimeMillis()): Flow<Result<Unit>> = flow {
    val identity = identityRepository.getIdentity().getOrElse { e -> emit(Result.failure(e)); return@flow }
    val reliabilityScore = trustScoreProvider.getTrustScore(identity.nodeId).toFloat()
    val ip = publicIpFetcher.fetchPublicIp().getOrElse { e -> emit(Result.failure(e)); return@flow }

    try {
        signalingRepository.registerAsSuperPeer(ip, tcpPort, reliabilityScore, electedAt).getOrThrow()
        networkEventRepository.pushEvent("[ELECTION] Super-Pair enregistré sur Relais HA: $ip:$tcpPort")
        emit(Result.success(Unit))

        while (currentCoroutineContext().isActive) {
            delay(30_000L)
            signalingRepository.registerAsSuperPeer(ip, tcpPort, reliabilityScore, electedAt)
                .onFailure { Log.w(TAG, "Keepalive Relais HA échoué — mode P2P local actif", it) }
        }
    } catch (e: CancellationException) {
        withContext(NonCancellable) {
            signalingRepository.unregisterAsSuperPeer()
                .onFailure { Log.w(TAG, "Abdication Relais HA échouée — TTL serveur purgera l'entrée", it) }
        }
        throw e  // Propager — NE JAMAIS swallower CancellationException
    } catch (e: Exception) {
        Log.w(TAG, "Enregistrement Super-Pair Relais HA échoué — mode P2P local", e)
        emit(Result.failure(e))
    }
}
```

**CRITIQUE :** `withContext(NonCancellable)` autour de `unregisterAsSuperPeer()` lors de l'abdication — sinon le contexte annulé empêche l'appel.

### Intégration MobicloudP2PService — Loop 7

```kotlin
// Dans MobicloudP2PService, Loop 7 (déjà existante) :
@Volatile private var superPeerJob: Job? = null

// Sur victoire élection :
superPeerJob = serviceScope.launch {
    registerSuperPeerUseCase(tcpPort = TCP_PORT, electedAt = election.electedAt).collect { result ->
        result.onFailure { Log.w(TAG, "RegisterSuperPeer échec : ${it.message}") }
    }
}

// Abdication (Story 3.3) :
fun abdicate() { superPeerJob?.cancel() }
```

`@Volatile` sur `superPeerJob` : visibilité inter-thread garantie (accès depuis binder et serviceScope).

### `localNodeIdFlow` — Pattern Eagerly Requis

```kotlin
private val localNodeIdFlow: StateFlow<String?> = flow {
    emit(identityRepository.getIdentity().getOrNull()?.nodeId)
}.stateIn(viewModelScope, SharingStarted.Eagerly, null)
```

`SharingStarted.Eagerly` : résolu immédiatement au démarrage du ViewModel. `WhileSubscribed` causerait une émission `null` si le premier collecteur arrive avant la résolution.

### Modèle `NodeRole` — Déjà Créé en Story 3.2 (ancienne)

```kotlin
// domain/models/NodeRole.kt — DÉJÀ EXISTANT, ne pas recréer
enum class NodeRole { PEER, SUPER_PAIR }
```

### TTL et Purge Automatique

| Paramètre | Valeur |
|---|---|
| TTL annuaire serveur | 60 secondes |
| Intervalle keepalive client | 30 secondes |
| Marge de sécurité | 2x keepalive avant expiration TTL |

Sur abdication ou coupure WSS : `unregisterAsSuperPeer()` tente une désinscription explicite. En cas d'échec réseau, le TTL 60s côté serveur assure la purge automatique — **pas de zombie entry possible**.

### Tests — Dispatcher et Timing

Utiliser **`StandardTestDispatcher`** (pas `UnconfinedTestDispatcher`) pour contrôler `delay(30_000L)` :

```kotlin
@Test
fun `keepalive - registerAsSuperPeer rappele apres 30 secondes`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val job = launch(testDispatcher) { useCase(tcpPort = 7777).toList(mutableListOf()) }
    advanceTimeBy(1L)   // enregistrement initial
    advanceTimeBy(30_001L)  // 1 keepalive
    coVerify(exactly = 2) { signalingRepository.registerAsSuperPeer(any(), any(), any(), any()) }
    job.cancel()
}
```

### Préparation pour Story 3.3 (Abdication)

`superPeerJob` dans `MobicloudP2PService` est la seule référence à annuler pour déclencher l'abdication. Ne pas internaliser, ne pas supprimer. Story 3.3 appelle `abdicate()` après 30 minutes de mandat.

---

## UX Requirements

- Badge `★ Super-Pair` (couleur `#00FF41` — Green Terminal) affiché dans `DashboardScreen` quand `nodeRole == NodeRole.SUPER_PAIR`
- Badge `● Nœud Connecté` affiché quand `nodeRole == NodeRole.PEER`
- Log dans `RadarLogConsole` : `"[ELECTION] Super-Pair enregistré sur Relais HA: {ip}:{port}"` via `networkEventRepository.pushEvent()`
- Aucune animation (NFR-03 — conservation batterie) — changement de badge uniquement

## Project Structure

**Nouveaux fichiers à créer :**
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCaseTest.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt`

**Fichiers à utiliser tels quels (ne pas modifier) :**
- `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt` — interface déjà complète (Epic 8)
- `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt` — implémentation déjà complète (Epic 8)
- `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt` — client HA déjà implémenté (Story 8.2)
- `app/src/main/kotlin/com/mobicloud/domain/models/NodeRole.kt` — enum déjà créé (ancienne Story 3.2)

**Fichiers à modifier :**
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` — ajouter Loop 7 + `superPeerJob`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt` — ajouter `nodeRole: StateFlow<NodeRole>`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt` — afficher badge Super-Pair

**Fichiers à NE PAS modifier :**
- `RunBullyElectionUseCase.kt` — déjà complet, ne pas toucher
- `RelayModule.kt` / `SignalingModule.kt` — bindings Hilt déjà configurés

---

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Completion Notes List

- **Context pivot V5.0** : story créée post-migration Zero-Firebase. L'implémentation `SignalingRepository` et `RelayWebSocketClient` était déjà en place (Epics 2 et 8). Cette story documente l'assemblage côté élection.
- **`RegisterSuperPeerUseCase`** : `invoke(tcpPort, electedAt)` émet `Result.success(Unit)` à l'enregistrement initial, puis keepalive toutes les 30s. `CancellationException` propagée après `unregisterAsSuperPeer()` dans `NonCancellable`. Fallback gracieux sur Relais HA inaccessible.
- **`SignalingRepositoryImpl`** : délègue à `relayClient.sendRegisterPeer`. Filtre TTL dans `processPeerList`. `pushEvent` sur échec.
- **`MobicloudP2PService`** : `@Volatile superPeerJob` stocke la coroutine keepalive. `abdicate()` annule le job → Story 3.3.
- **`DashboardViewModel.nodeRole`** : `StateFlow<NodeRole>` via `combine`. `localNodeIdFlow` `Eagerly` pour éviter émission `null`.
- **`DashboardScreen`** : badge `★ Super-Pair` (`#00FF41`) ou `● Nœud Connecté`.
- **Tests** : 7 `RegisterSuperPeerUseCaseTest` + 5 `SignalingRepositoryImplTest` — `StandardTestDispatcher` pour contrôle du temps.
- **Patches code review (2026-04-29)** : 9 corrections appliquées suite à code review adversariale — D2 (`nodeId` ajouté dans payload REGISTER_PEER + cascade interface/impl/usecase/tests), `collectLatest`→`collect` dans TCP Handshake loop, délai initial 3s avant GET_PEERS, `relayState` wrapé dans `stateIn`, test TTL hermétique avec timestamp fixe, log Firebase→Relais HA, commentaires `ByteArray(0)` et scope process-scoped documentés.

### File List

**Nouveaux fichiers :**
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCaseTest.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt`

**Fichiers modifiés :**
- `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt`
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/DashboardViewModelTest.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/PeerRepositoryImplTest.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RegisterSuperPeerUseCaseTest.kt`

### Change Log

- 2026-04-29 (review patches) : 9 corrections issues de code review adversariale — `nodeId` ajouté dans payload REGISTER_PEER (interface + impl + usecase + tests), `collectLatest`→`collect` TCP Handshake, délai 3s GET_PEERS, `relayState.stateIn`, test TTL hermétique, log stale Firebase corrigé.
- 2026-04-29 : Story 3.2 V5.0 créée — Remplace l'ancienne story Firebase. Enregistrement du Super-Pair via Serveurs Relais HA WebSocket (`RegisterSuperPeerUseCase` + `SignalingRepositoryImpl` + Loop 7 `MobicloudP2PService` + badge `DashboardScreen`). 7 + 5 tests unitaires.

---

### Review Findings — 2e passage (2026-04-29)

> Code review adversarial 2e passage — 1 décision, 5 patches, 4 différés, 6 rejetés.

**Décision requise**

- [x] [Review][Decision] **D1 — REGISTER_PEER non signé EC P-256** → RÉSOLU : session AUTH EC P-256 couvre tous les messages ; AC#3 mis à jour. Dismiss. — AC#3 spécifie "message REGISTER_PEER signé EC P-256" mais seul le frame AUTH initial est signé (`RelayAuthSigner` dans `onOpen`). Chaque REGISTER_PEER (initial + keepalives) est envoyé en JSON brut sans signature. Choix : (a) signer chaque REGISTER_PEER avec la clé privée EC du nœud, ou (b) considérer que l'AUTH de session couvre tous les messages et mettre à jour l'AC#3 pour le clarifier. [`RelayWebSocketClient.kt:sendRegisterPeer`]

**Patches à appliquer**

- [x] [Review][Patch] **P1 — Test compile failure : `registerAsSuperPeer` appelé avec 4 args au lieu de 5** — ajout de `"test-node-id"` dans les 2 appels de test. [`SignalingRepositoryImplTest.kt`]
- [x] [Review][Patch] **P2 — `RELAY_SERVER_URLS.first()` sans garde contre liste vide** — remplacé par `firstOrNull() ?: return@launch`. [`SignalingRepositoryImpl.kt:init`]
- [x] [Review][Patch] **P3 — Vulnérabilité clock skew dans TTL check de `processPeerList`** — `now - peer.lastSeen` remplacé par `maxOf(0L, now - peer.lastSeen)`. [`SignalingRepositoryImpl.kt:processPeerList`]
- [x] [Review][Patch] **P4 — `NodeIdentity(ByteArray(0))` dans `coVerify` — matcher jamais satisfait** — remplacé par `match { it.nodeId == "fresh-node" && it.publicKeyBytes.isEmpty() }`. [`SignalingRepositoryImplTest.kt:processPeerList insere les peers valides`]
- [x] [Review][Patch] **P5 — Log stale "Firebase" dans `MobicloudP2PService`** — 2 occurrences corrigées (`Firebase` → `Relais HA`). [`MobicloudP2PService.kt`]

**Différés (pré-existants ou hors scope)**

- [x] [Review][Defer] **W1 — `connectionJobs` map croît sans nettoyage des jobs terminés** — pattern pré-existant depuis l'ancien code Firebase, non introduit par ce diff ; à adresser dans une story dédiée. [`MobicloudP2PService.kt:TCP Handshake loop`] — deferred, pre-existing
- [x] [Review][Defer] **W2 — `elapsedRealtime()` pour `timestampMs` vs wall-clock pour TTL** — le filtre TTL est correct (wall-clock vs wall-clock) mais `timestampMs` stocké en `elapsedRealtime` est cohérent avec l'éviction locale ; incohérence de sémantique déjà identifiée en P4 du 1er review. [`SignalingRepositoryImpl.kt:processPeerList`] — deferred, pre-existing
- [x] [Review][Defer] **W3 — Failover REGISTER_PEER au niveau message non implémenté** — sur `sendRegisterPeer() = false`, le UseCase logue et reprend au prochain keepalive (30s) sur la nouvelle connexion ; le failover connection-level dans `RelayWebSocketClient.connect()` couvre ce cas de manière asynchrone. [`SignalingRepositoryImpl.kt:registerAsSuperPeer`] — deferred, acceptable by design
- [x] [Review][Defer] **W4 — `ByteArray(0)` clé publique sans résolution démontrée** — D1 du 1er review, documenté comme résolution lazy lors du TCP handshake ; aucune couche de validation présente dans le diff. [`SignalingRepositoryImpl.kt:processPeerList`] — deferred, pre-existing

---

### Review Findings

> Code review du 2026-04-29 — 2 décisions, 9 patches, 4 différés, 4 rejetés.

**Décisions requises (non résolues)**

- [x] [Review][Decision] **D1 — `ByteArray(0)` comme clé publique des pairs RELAY_HA** — `processPeerList` insère `NodeIdentity(peer.nodeId, ByteArray(0))` pour tous les pairs reçus du relais. La clé publique est absente du payload `PeerList`. Si une couche downstream (TCP handshake, vérification de bloc) utilise cette clé pour de la crypto, l'authentification est impossible. Choix : (a) étendre le protocole relais pour inclure `publicKeyBase64` dans `RelayPeer`, ou (b) documenter explicitement que la clé publique des pairs RELAY_HA sera résolue lors du TCP handshake (lazy resolution). [`SignalingRepositoryImpl.kt:processPeerList`]
- [x] [Review][Decision] **D2 — `nodeId` absent du payload JSON de `sendRegisterPeer`** — AC#3 spécifie `{nodeId, ip, port, reliabilityScore, electedAt}` mais le `JSONObject` dans `RelayWebSocketClient.sendRegisterPeer()` n'inclut pas `nodeId`. Si le serveur relais dérive le `nodeId` de la session AUTH, c'est acceptable ; sinon, l'entrée côté serveur est anonyme. Choix : (a) ajouter `nodeId` dans `sendRegisterPeer()` et confirmer que le serveur l'utilise, ou (b) documenter que le serveur lie le `nodeId` à la session AUTH. [`RelayWebSocketClient.kt:sendRegisterPeer`]

**Patches à appliquer**

- [x] [Review][Patch] **P1 — Scope `CoroutineScope` non annulé dans `@Singleton`** [`SignalingRepositoryImpl.kt:scope`]
- [x] [Review][Patch] **P2 — Race condition : `connectionJobs` partagé entre invocations `collectLatest`** [`MobicloudP2PService.kt:bloc TCP Handshake`]
- [x] [Review][Patch] **P3 — `collectLatest` annule les `launch{}` internes — connexions TCP orphelines** [`MobicloudP2PService.kt:bloc TCP Handshake`]
- [x] [Review][Patch] **P4 — Mélange `System.currentTimeMillis()` / `SystemClock.elapsedRealtime()` pour le TTL** [`SignalingRepositoryImpl.kt:processPeerList`]
- [x] [Review][Patch] **P5 — Test TTL non hermétique : `System.currentTimeMillis()` non mocké** [`SignalingRepositoryImplTest.kt:processPeerList filtre TTL`]
- [x] [Review][Patch] **P6 — `relayState` dans `DashboardViewModel` non wrapé dans `stateIn`** [`DashboardViewModel.kt:relayState`]
- [x] [Review][Patch] **P7 — Aucune reconnexion après `RelayEvent.Disconnected` dans `init{}`** [`SignalingRepositoryImpl.kt:init`]
- [x] [Review][Patch] **P8 — Crash si `RELAY_SERVER_URLS` est vide (`first()` lève `NoSuchElementException`)** [`SignalingRepositoryImpl.kt:init`]
- [x] [Review][Patch] **P9 — `fetchActiveSuperPeers` dispatché avant que la connexion WSS soit établie — faux "injoignable" au boot** [`MobicloudP2PService.kt:boucle GET_PEERS`]

**Différés (pre-existing ou hors scope)**

- [x] [Review][Defer] **W1 — Polling `fetchActiveSuperPeers` sans backoff exponentiel** [`MobicloudP2PService.kt`] — deferred, pre-existing
- [x] [Review][Defer] **W2 — `processPeerList` sans garantie d'accès séquentiel en cas de futures sources concurrentes** [`SignalingRepositoryImpl.kt`] — deferred, pre-existing
- [x] [Review][Defer] **W3 — Race condition `superPeerJob` entre abdication `NonCancellable` et nouvelle élection** [`MobicloudP2PService.kt`] — deferred, pre-existing
- [x] [Review][Defer] **W4 — Tests : comportement post-`RelayEvent.Disconnected` non couvert** [`SignalingRepositoryImplTest.kt`] — deferred, pre-existing
