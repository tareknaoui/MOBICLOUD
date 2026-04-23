# Story 7.1: Détection du Départ Imminent d'un Nœud

Status: done

## Story

En tant que nœud MobiCloud,
Je veux signaler mon départ imminent au cluster lorsque je détecte un basculement réseau Wifi → 4G,
Afin que le Super-Pair puisse orchestrer la migration de mes blocs avant ma déconnexion.

## Acceptance Criteria

1. **Given** le nœud est actif et héberge des blocs dans le cluster
   **When** le `ConnectivityManager` Android détecte un basculement de réseau (Wifi → 4G ou perte de signal)
   **Then** le nœud envoie immédiatement un message `DEPARTURE_NOTICE` Protobuf signé au Super-Pair

2. **And** le `DEPARTURE_NOTICE` contient la liste des `blockId` hébergés par ce nœud

3. **And** le nœud continue à servir les requêtes TCP pendant 5 secondes supplémentaires (fenêtre de migration)

4. **And** si le Super-Pair ne confirme pas le début de migration dans les 5 secondes, le nœud se déconnecte proprement

5. **And** la logique de détection est dans `core/network/NetworkChangeObserver.kt`

## Tasks / Subtasks

### 🔢 Bloc Données (Tasks 1–3) — nouveaux modèles et interfaces

- [x] **Task 1** : Créer `DepartureNoticeMessage` (AC: #1, #2)
  - [x] Subtask 1.1 : Créer `app/src/main/kotlin/com/mobicloud/domain/models/DepartureNoticeMessage.kt` :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class DepartureNoticeMessage(
        @ProtoNumber(1) val senderNodeId: String = "",
        @ProtoNumber(2) val hostedBlockIds: List<String> = emptyList(),
        @ProtoNumber(3) val signatureBytes: ByteArray = byteArrayOf()
    ) {
        override fun equals(other: Any?): Boolean { ... } // couvrir signatureBytes
        override fun hashCode(): Int { ... }
    }
    ```
    Pattern: identique à `ElectionPayload.kt` (mêmes imports : `@Serializable`, `@ProtoNumber`, `ExperimentalSerializationApi`).

- [x] **Task 2** : Étendre `HostedBlockRepository` et son DAO (AC: #2)
  - [x] Subtask 2.1 : Dans `domain/repository/HostedBlockRepository.kt`, ajouter :
    ```kotlin
    /** Retourne tous les blockId actuellement hébergés localement. */
    suspend fun getAllBlockIds(): Result<List<String>>
    ```
  - [x] Subtask 2.2 : Dans `data/local/dao/HostedBlockDao.kt`, ajouter :
    ```kotlin
    @Query("SELECT block_id FROM hosted_blocks")
    suspend fun getAllBlockIds(): List<String>
    ```
  - [x] Subtask 2.3 : Dans `data/repository_impl/HostedBlockRepositoryImpl.kt`, implémenter :
    ```kotlin
    override suspend fun getAllBlockIds(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching { hostedBlockDao.getAllBlockIds() }
        }
    ```

- [x] **Task 3** : Créer le canal TCP `DepartureChannel` (AC: #1)
  - [x] Subtask 3.1 : Créer `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/DepartureChannel.kt` :
    ```kotlin
    object DepartureChannel {
        const val DEPARTURE_NOTICE: Byte = 0x08
        const val DEPARTURE_ACK: Byte = 0x09    // réponse du Super-Pair (Story 7.2)
        const val MAX_DEPARTURE_PAYLOAD_BYTES = 200_000  // K+N blockIds × ~65 bytes = ~13 KB max
    }
    ```
    Ce byte `0x08` doit être unique parmi les bytes déjà utilisés dans `GossipChannel` et `BlockTransferChannel`. Vérifier les valeurs existantes avant d'assigner.

### 🌐 Bloc Réseau (Task 4) — NetworkChangeObserver

- [x] **Task 4** : Créer `NetworkChangeObserver` (AC: #1, #3, #4, #5)
  - [x] Subtask 4.1 : Créer `app/src/main/kotlin/com/mobicloud/core/network/NetworkChangeObserver.kt` :
    ```kotlin
    @Singleton
    class NetworkChangeObserver @Inject constructor(
        @ApplicationContext private val context: Context,
        private val sendDepartureNoticeUseCase: SendDepartureNoticeUseCase
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    // Perte du WiFi détectée — déclencher le DEPARTURE_NOTICE
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        sendDepartureNoticeUseCase()
                    }
                }
            }
        }

        fun register() {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }

        fun unregister() {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
    }
    ```
    Imports requis : `android.content.Context`, `android.net.*`, `dagger.hilt.android.qualifiers.ApplicationContext`, `javax.inject.Inject`, `javax.inject.Singleton`, `kotlinx.coroutines.*`.

### ⚙️ Bloc UseCase (Task 5) — orchestration du départ

- [x] **Task 5** : Créer `SendDepartureNoticeUseCase` (AC: #1, #2, #3, #4)
  - [x] Subtask 5.1 : Créer `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/SendDepartureNoticeUseCase.kt` :
    ```kotlin
    @Singleton
    class SendDepartureNoticeUseCase @Inject constructor(
        private val hostedBlockRepository: HostedBlockRepository,
        private val securityRepository: SecurityRepository,
        private val peerRepository: PeerRepository,
        private val tcpConnectionManager: TcpConnectionManager,
        private val networkEventRepository: NetworkEventRepository
    ) {
        companion object {
            const val MIGRATION_WINDOW_MS = 5_000L
        }

        suspend operator fun invoke(): Result<Unit> = runCatching {
            val identity = securityRepository.getIdentity().getOrThrow()
            val blockIds = hostedBlockRepository.getAllBlockIds().getOrThrow()

            // Construire le payload à signer (sans signatureBytes)
            val payloadToSign = "${identity.nodeId}:${blockIds.joinToString(",")}".toByteArray()
            val signature = securityRepository.signData(payloadToSign).getOrThrow()

            val notice = DepartureNoticeMessage(
                senderNodeId = identity.nodeId,
                hostedBlockIds = blockIds,
                signatureBytes = signature
            )

            // Envoyer au Super-Pair s'il est connu
            val superPeer = peerRepository.peers.value
                .firstOrNull { it.isSuperPeer && it.isActive }

            if (superPeer != null) {
                tcpConnectionManager.sendDepartureNotice(notice, superPeer.ipAddress, superPeer.port)
                networkEventRepository.pushEvent("[DÉPART] DEPARTURE_NOTICE envoyé à ${superPeer.identity.nodeId.take(8)} (${blockIds.size} blocs)")
            } else {
                networkEventRepository.pushEvent("[DÉPART] Aucun Super-Pair connu — départ immédiat")
            }

            // AC#3: fenêtre de migration de 5 secondes
            delay(MIGRATION_WINDOW_MS)
            networkEventRepository.pushEvent("[DÉPART] Fenêtre de migration expirée — déconnexion propre")
        }
    }
    ```
  - [x] Subtask 5.2 : Ajouter dans `TcpConnectionManager` la méthode d'envoi sortante :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendDepartureNotice(
        notice: DepartureNoticeMessage,
        ip: String,
        port: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3_000)
                val out = DataOutputStream(socket.getOutputStream())
                val bytes = MobiCloudProtoBuf.encodeToByteArray(DepartureNoticeMessage.serializer(), notice)
                out.writeByte(DepartureChannel.DEPARTURE_NOTICE.toInt())
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
                // AC#4: attendre ACK de migration (optionnel Story 7.1 — timeout 5s géré par le use case)
            }
        }
    }
    ```

### 🔌 Bloc TCP réception (Task 6) — Super-Pair reçoit DEPARTURE_NOTICE

- [x] **Task 6** : Étendre `TcpConnectionManager` pour recevoir `DEPARTURE_NOTICE` (AC: #1)
  - [x] Subtask 6.1 : Dans `handleIncomingConnection()` du `TcpConnectionManager`, ajouter dans le `when` :
    ```kotlin
    DepartureChannel.DEPARTURE_NOTICE -> handleIncomingDepartureNotice(pushback)
    ```
  - [x] Subtask 6.2 : Implémenter `handleIncomingDepartureNotice` :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingDepartureNotice(inp: InputStream) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > DepartureChannel.MAX_DEPARTURE_PAYLOAD_BYTES) {
                Log.w("MobiCloud:TCP", "DEPARTURE_NOTICE taille invalide: $len — ignoré")
                return
            }
            val bytes = ByteArray(len).also { data.readFully(it) }
            val notice = MobiCloudProtoBuf.decodeFromByteArray(DepartureNoticeMessage.serializer(), bytes)
            departureHandler?.onDepartureNoticeReceived(notice)
                ?: Log.w("MobiCloud:TCP", "DEPARTURE_NOTICE reçu mais aucun handler (Story 7.2)")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture DEPARTURE_NOTICE", e)
        }
    }
    ```
  - [x] Subtask 6.3 : Ajouter le champ handler dans `TcpConnectionManager` :
    ```kotlin
    @Volatile
    var departureHandler: DepartureNoticeHandler? = null
    ```
  - [x] Subtask 6.4 : Créer `domain/usecase/m06_m07_repair_migration/DepartureNoticeHandler.kt` (interface) :
    ```kotlin
    interface DepartureNoticeHandler {
        suspend fun onDepartureNoticeReceived(notice: DepartureNoticeMessage)
    }
    ```
    Ce handler sera implémenté par `OrchestrateBlockMigrationUseCase` en Story 7.2. Story 7.1 l'expose mais ne l'implémente pas.

### 🏗️ Bloc DI & Service (Task 7) — intégration dans Hilt et MobicloudP2PService

- [x] **Task 7** : Câbler l'injection Hilt et le service (AC: #1, #5)
  - [x] Subtask 7.1 : Créer `di/RepairMigrationModule.kt` (ou ajouter dans `NetworkModule.kt`) :
    ```kotlin
    @Module
    @InstallIn(SingletonComponent::class)
    object RepairMigrationModule {
        // SendDepartureNoticeUseCase et NetworkChangeObserver sont @Singleton avec @Inject — pas besoin de @Provides
    }
    ```
    `NetworkChangeObserver` et `SendDepartureNoticeUseCase` utilisent `@Singleton` + `@Inject constructor` — Hilt les résout automatiquement.
  - [x] Subtask 7.2 : Dans `MobicloudP2PService`, injecter et démarrer l'observer :
    ```kotlin
    @Inject lateinit var networkChangeObserver: NetworkChangeObserver

    // Dans startP2PNetworkLoops(), après démarrage des autres loops :
    networkChangeObserver.register()
    ```
  - [x] Subtask 7.3 : Dans `MobicloudP2PService.onDestroy()`, désenregistrer l'observer :
    ```kotlin
    override fun onDestroy() {
        networkChangeObserver.unregister()
        // ... reste du cleanup existant
    }
    ```

### 🧪 Bloc Tests (Task 8)

- [x] **Task 8** : Tests JVM pour `SendDepartureNoticeUseCase` (AC: #1, #2, #3, #4)
  - [x] Subtask 8.1 : Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/SendDepartureNoticeUseCaseTest.kt` :
    - **Test 1 — notice envoyée avec blockIds** : mocker `hostedBlockRepository.getAllBlockIds()` → `["abc...", "def..."]`, vérifier que `tcpConnectionManager.sendDepartureNotice()` est appelé avec `notice.hostedBlockIds.size == 2`.
    - **Test 2 — aucun Super-Pair** : `peerRepository.peers.value = emptyList()`, vérifier que `tcpConnectionManager.sendDepartureNotice()` n'est PAS appelé (pas de crash).
    - **Test 3 — nœud sans blocs hébergés** : `getAllBlockIds()` → `[]`, vérifier que `sendDepartureNotice` est appelé avec `hostedBlockIds = emptyList()` (pas de short-circuit).
    - **Test 4 — délai de 5 secondes** : utiliser `TestCoroutineScheduler`, avancer de `5_000ms`, vérifier que `invoke()` se termine après le délai (pas avant).

---

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Description | Action |
|---|---|---|
| `data/p2p/tcp/TcpConnectionManager.kt` | Dispatch messages TCP par premier byte, envoie/reçoit Protobuf | **MODIFIER** (Tasks 5.2, 6.1–6.3) |
| `data/network/service/MobicloudP2PService.kt` | Service avec serviceScope, Hilt, loops P2P | **MODIFIER** (Task 7.2–7.3) |
| `domain/repository/HostedBlockRepository.kt` | Interface `saveBlock`, `blockExists`, `deleteBlock`, `getBlock` | **MODIFIER** (Task 2.1 — ajouter getAllBlockIds) |
| `data/local/dao/HostedBlockDao.kt` | DAO Room : `insertHostedBlock`, `getHostedBlock`, `getAllHostedBlocksFlow`, `deleteHostedBlock` | **MODIFIER** (Task 2.2 — ajouter query) |
| `data/repository_impl/HostedBlockRepositoryImpl.kt` | Impl actuelle avec mutex par blockId, `runCatching` | **MODIFIER** (Task 2.3 — implémenter getAllBlockIds) |
| `domain/models/ElectionPayload.kt` | Pattern de message signé Protobuf avec `signatureBytes` | **COPIER LE PATRON** — même structure pour `DepartureNoticeMessage` |
| `domain/models/BlockRequestMessage.kt` | Pattern message Protobuf simple (`@ProtoNumber`, `@Serializable`) | **COPIER LE PATRON** |
| `domain/usecase/m06_m07_repair_migration/CircuitBreakerUseCase.kt` | Déjà dans le même package | **NE PAS MODIFIER** |
| `domain/usecase/m06_m07_repair_migration/LocalRepairBuffer.kt` | Buffer FIFO 50 entrées — Story 3.3 | **NE PAS MODIFIER** |
| `data/p2p/tcp/BlockDownloadClient.kt` | Pattern envoi TCP sortant (`Socket()`, `DataOutputStream`) | **COPIER LE PATRON** pour `sendDepartureNotice` |
| `domain/models/NetworkType.kt` | `enum class NetworkType { WIFI, CELLULAR, UNKNOWN }` — déjà défini | **NE PAS RECRÉER** |
| `core/network/utils/NetworkUtils.kt` | Utilitaire réseau existant — vérifier si `ConnectivityManager` déjà utilisé | **LIRE** avant d'ajouter imports |

### ⚠️ CONTRAINTES CRITIQUES

**1. `ConnectivityManager.NetworkCallback` — API Level et permissions**
- `registerNetworkCallback(NetworkRequest, NetworkCallback)` est disponible depuis API 21 (minSdk du projet = 24). Aucune permission spéciale requise pour observer les changements réseau via `NetworkCallback` (contrairement à `CONNECTIVITY_ACTION` broadcast qui nécessitait `ACCESS_NETWORK_STATE`).
- **NE PAS utiliser** `ConnectivityManager.CONNECTIVITY_ACTION` (broadcast) — déprécié API 28+, ne reçoit pas les changements en arrière-plan depuis API 26+.
- La permission `ACCESS_NETWORK_STATE` est déjà dans le Manifest (Story 1.4) — vérifier avant d'ajouter.

**2. `NetworkCallback.onLost()` détecte UNIQUEMENT la perte du réseau enregistré**
- Enregistrer un `NetworkRequest` filtré sur `TRANSPORT_WIFI` (via `.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)`).
- `onLost(network)` est appelé quand ce réseau WiFi est perdu — couvre le cas WiFi → 4G et WiFi → déconnexion totale.
- Le passage WiFi → 4G déclenche d'abord `onAvailable(cellularNetwork)` puis `onLost(wifiNetwork)` : l'ordre est garanti par Android. Déclencher sur `onLost` (pas `onCapabilitiesChanged`) pour capter la vraie perte WiFi.

**3. Byte de canal `0x08` — éviter les collisions**
- Vérifier les bytes utilisés dans `GossipChannel` et `BlockTransferChannel` **avant** d'assigner `DEPARTURE_NOTICE = 0x08`. Si collision, choisir `0x0A` ou `0x0B`. La valeur exacte importe peu tant qu'elle est unique dans les 256 valeurs.

**4. `PeerRepository.peers.value` — obtenir le Super-Pair**
- `peerRepository.peers` est un `StateFlow<List<Peer>>`. `Peer.isSuperPeer` (ou `Peer.nodeRole == NodeRole.SUPER_PEER`) donne le Super-Pair courant. Vérifier le nom exact du champ dans `domain/models/Peer.kt` — il s'appelle peut-être `role` ou `nodeRole`.
- Si `null` (pas de Super-Pair connu), le nœud logue et attend quand même les 5 secondes (pas de crash).

**5. Signer le payload DEPARTURE_NOTICE**
- `securityRepository.signData(ByteArray): Result<ByteArray>` — méthode existante dans `SecurityRepository`.
- Construire le payload à signer **avant** de créer `DepartureNoticeMessage` (le champ `signatureBytes` ne peut pas être inclus dans sa propre signature). Pattern : signer `"${nodeId}:${blockIds.joinToString(",")}"`.toByteArray()`.
- Alternative propre : encoder un `DepartureNoticeMessage(signatureBytes = byteArrayOf())` en Protobuf → signer ces bytes → créer le message final. Cohérent avec le pattern utilisé pour l'élection Bully dans `RunBullyElectionUseCase`.

**6. `coroutineScope` dans `NetworkChangeObserver.onLost()`**
- `onLost()` est appelé sur le thread du `ConnectivityManager` — pas un coroutine context.
- Lancer un `CoroutineScope(Dispatchers.IO + SupervisorJob()).launch { ... }` pour exécuter `sendDepartureNoticeUseCase()` — mais ce scope est "fire-and-forget" sans lifecycle. Alternatif plus propre : exposer un `Flow<Unit>` ou injecter le `serviceScope` du service via un `@Named` Hilt qualifier.
- **Approche recommandée pour PFE** : utiliser le scope fire-and-forget (pattern simple, acceptable pour PFE). Annoter avec un commentaire.

**7. `delay(5_000L)` dans `SendDepartureNoticeUseCase` — pas de `Thread.sleep()`**
- Utiliser `kotlinx.coroutines.delay()` (suspend) jamais `Thread.sleep()` — le use case s'exécute sur un dispatcher coroutine.
- Les requêtes TCP servies pendant ces 5 secondes passent via le `TcpConnectionManager.serverSocket` qui reste actif — aucune action requise, le TCP server continue normalement.

**8. Story 7.2 déclenchée après Story 7.1**
- `OrchestrateBlockMigrationUseCase` (Story 7.2) recevra les `DEPARTURE_NOTICE` via `TcpConnectionManager.departureHandler`.
- Story 7.1 crée l'interface `DepartureNoticeHandler` et le câble dans `TcpConnectionManager`, mais laisse `departureHandler = null` (logué comme warning si null). Pas de crash.

**9. `HostedBlockDao.getAllBlockIds()` — cohérence base de données**
- La liste retournée est un snapshot Room DB au moment de l'appel. Des blocs pourraient être ajoutés/supprimés entre la requête et l'envoi — acceptable pour Story 7.1 (best-effort, NFR-02 < 5s prioritaire sur l'exactitude parfaite).
- Utiliser `suspend fun getAllBlockIds()` (non-Flow) car on veut un snapshot one-shot, pas une subscription continue.

### 📁 Arborescence cible après implémentation

```
app/src/main/kotlin/com/mobicloud/
├── core/network/
│   └── NetworkChangeObserver.kt                              ← NOUVEAU
├── data/
│   ├── local/dao/
│   │   └── HostedBlockDao.kt                                 ← MODIFIÉ (+ getAllBlockIds query)
│   ├── p2p/tcp/
│   │   ├── DepartureChannel.kt                               ← NOUVEAU
│   │   └── TcpConnectionManager.kt                           ← MODIFIÉ (+ sendDepartureNotice, handleIncomingDepartureNotice, departureHandler)
│   ├── network/service/
│   │   └── MobicloudP2PService.kt                            ← MODIFIÉ (+ networkChangeObserver.register/unregister)
│   └── repository_impl/
│       └── HostedBlockRepositoryImpl.kt                      ← MODIFIÉ (+ getAllBlockIds impl)
├── domain/
│   ├── models/
│   │   └── DepartureNoticeMessage.kt                         ← NOUVEAU
│   ├── repository/
│   │   └── HostedBlockRepository.kt                          ← MODIFIÉ (+ getAllBlockIds)
│   └── usecase/m06_m07_repair_migration/
│       ├── DepartureNoticeHandler.kt                         ← NOUVEAU (interface)
│       └── SendDepartureNoticeUseCase.kt                     ← NOUVEAU
└── ...

app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/
└── SendDepartureNoticeUseCaseTest.kt                         ← NOUVEAU
```

### 🔗 Références

- `domain/models/ElectionPayload.kt` — patron exact pour `DepartureNoticeMessage` (structure signée Protobuf)
- `data/p2p/tcp/TcpConnectionManager.kt` l.132–144 — `when (firstByte.toByte())` à étendre pour `DepartureChannel.DEPARTURE_NOTICE`
- `data/p2p/tcp/BlockDownloadClient.kt` — patron `Socket().use { socket.connect(...); DataOutputStream(...) }` pour `sendDepartureNotice`
- `data/network/service/MobicloudP2PService.kt` l.319–324 — `onDestroy()` à étendre avec `networkChangeObserver.unregister()`
- `domain/usecase/m06_m07_repair_migration/CircuitBreakerUseCase.kt` — même package, même pattern `@Singleton @Inject constructor`
- Story 6.4 Dev Notes §3 — pattern `ConcurrentHashMap.newKeySet()` pour thread-safety (non requis ici mais contexte utile)
- Story 3.4 Epics — `CircuitBreakerUseCase` : si actif lors du départ, les repairs sont gelés ; pas d'interaction directe avec `SendDepartureNoticeUseCase` (périmètre Story 7.3)

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Debug Log References
- Compilation erreur : `publicKey` → `publicKeyBytes` dans NodeIdentity (test corrigé)
- Compilation erreur : `launch` non importé dans test avec `backgroundScope` (ajout import corrigé)
- 4 échecs `ErasureProgressViewModelTest` préexistants (non liés à Story 7.1)
- Bytes 0x01-0x03 (Gossip), 0x20-0x42 (BlockTransfer) vérifiés → 0x08 assigné à DEPARTURE_NOTICE sans collision

### Completion Notes List
- `DepartureNoticeMessage` créé avec pattern `@ProtoNumber` + `equals/hashCode` pour `ByteArray` (identique à `ElectionPayload`)
- `HostedBlockRepository.getAllBlockIds()` ajouté à l'interface, DAO et impl (snapshot one-shot Room)
- `DepartureChannel` définit bytes 0x08/0x09 avec MAX_DEPARTURE_PAYLOAD_BYTES = 200 000
- `NetworkChangeObserver` détecte perte WiFi via `ConnectivityManager.NetworkCallback` (API 24+, filtré `TRANSPORT_WIFI`)
- `SendDepartureNoticeUseCase` orchestre : identité + blockIds + signature → DEPARTURE_NOTICE → envoi TCP + délai 5s (AC#3/4)
- `TcpConnectionManager` étendu : `sendDepartureNotice()` (sortant) + `handleIncomingDepartureNotice()` (entrant) + champ `departureHandler`
- `DepartureNoticeHandler` interface créée (implémentation différée Story 7.2)
- `MobicloudP2PService` : `networkChangeObserver.register()` au démarrage, `unregister()` dans `onDestroy()`
- `RepairMigrationModule` Hilt créé (module vide, classes auto-découvertes via `@Singleton @Inject`)
- 4 tests JVM passent : notice avec blockIds, sans super-pair, sans blocs, délai 5s (virtual time)

### File List
- `app/src/main/kotlin/com/mobicloud/domain/models/DepartureNoticeMessage.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt` (MODIFIÉ)
- `app/src/main/kotlin/com/mobicloud/data/local/dao/HostedBlockDao.kt` (MODIFIÉ)
- `app/src/main/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImpl.kt` (MODIFIÉ)
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/DepartureChannel.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/core/network/NetworkChangeObserver.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/DepartureNoticeHandler.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/SendDepartureNoticeUseCase.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt` (MODIFIÉ)
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (MODIFIÉ)
- `app/src/main/kotlin/com/mobicloud/di/RepairMigrationModule.kt` (NOUVEAU)
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/SendDepartureNoticeUseCaseTest.kt` (NOUVEAU)

### Review Findings

- [x] [Review][Decision] AC4 — Déconnexion propre après fenêtre : Option 2 appliquée — `tcpConnectionManager.stopServer()` appelé après `delay(5000ms)` [SendDepartureNoticeUseCase.kt]

- [x] [Review][Patch] Scope non tracé dans onLost — leaks mémoire + DEPARTURE_NOTICE dupliqués [NetworkChangeObserver.kt:30]
- [x] [Review][Patch] getNetworkCapabilities sur réseau perdu retourne null (API 26+) — vérification WiFi toujours false, feature potentiellement inactive [NetworkChangeObserver.kt:27-28]
- [x] [Review][Patch] Double registration NetworkCallback sur redémarrage START_STICKY — register() non idempotente [NetworkChangeObserver.kt:37, MobicloudP2PService.kt:134]
- [x] [Review][Patch] NPE sur superPeer.ipAddress!! / port!! — champs nullable dans Peer [SendDepartureNoticeUseCase.kt:42]
- [x] [Review][Patch] Résultat de sendDepartureNotice ignoré — échec TCP silencieux [SendDepartureNoticeUseCase.kt:41]
- [x] [Review][Patch] CancellationException swallowée par runCatching { delay() } — brise la coopération coroutine [SendDepartureNoticeUseCase.kt:25]
- [x] [Review][Patch] Socket sans timeout d'écriture dans sendDepartureNotice — écriture peut bloquer indéfiniment [TcpConnectionManager.kt:sendDepartureNotice]
- [x] [Review][Patch] MAX_DEPARTURE_PAYLOAD_BYTES = 200_000 surdimensionné — 200 KB alloués avant tout parsing [DepartureChannel.kt:7]
- [x] [Review][Patch] getAllBlockIds sans ORDER BY — signature payload non déterministe entre appels [HostedBlockDao.kt]
- [x] [Review][Patch] DEPARTURE_ACK = 0x09 dead code — constante déclarée, jamais envoyée ni lue [DepartureChannel.kt:6]

- [x] [Review][Defer] Signature verification absente côté récepteur [TcpConnectionManager.kt:handleIncomingDepartureNotice] — deferred, Story 7.2
- [x] [Review][Defer] Pas de timestamp/nonce dans DepartureNoticeMessage — replay attack théorique — deferred, hors scope PFE
- [x] [Review][Defer] Migration Room pour champ `iv` non visible dans ce diff — deferred, migration ajoutée dans story précédente
- [x] [Review][Defer] loopsStarted volatile non-atomique (CAS manquant) [MobicloudP2PService.kt] — deferred, pre-existing
- [x] [Review][Defer] Risque collision byte legacy handshake (latent, 0xAC ≠ 0x08) [TcpConnectionManager.kt] — deferred, pre-existing
- [x] [Review][Defer] peerRepository.peers.value snapshot potentiellement périmé [SendDepartureNoticeUseCase.kt] — deferred, best-effort acceptable per spec
- [x] [Review][Defer] TOCTOU blockIds entre lecture DB et envoi signature [SendDepartureNoticeUseCase.kt] — deferred, best-effort acceptable per Dev Notes

## Change Log

- 2026-04-23 — Story 7.1 créée (ready-for-dev) : Détection départ imminent via NetworkChangeObserver + DEPARTURE_NOTICE Protobuf signé + fenêtre migration 5s
- 2026-04-23 — Story 7.1 implémentée (review) : 8 tâches/13 subtasks complétées — DepartureNoticeMessage, DepartureChannel (0x08), NetworkChangeObserver, SendDepartureNoticeUseCase, TcpConnectionManager étendu, DepartureNoticeHandler, RepairMigrationModule, MobicloudP2PService câblé, 4 tests JVM passants
- 2026-04-23 — Code review : 1 decision_needed, 10 patch, 7 defer, 7 dismissed
