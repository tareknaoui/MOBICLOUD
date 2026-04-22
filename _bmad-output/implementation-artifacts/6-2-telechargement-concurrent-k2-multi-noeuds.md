# Story 6.2: Téléchargement Concurrent K+2 (Multi-Nœuds)

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant qu'utilisateur,
Je veux que les blocs de mon fichier soient téléchargés simultanément depuis plusieurs nœuds avec une stratégie K+2 compétitive,
Afin d'obtenir le ciphertext complet le plus rapidement possible, même si certains nœuds sont lents ou retournent une erreur — en préparation du pipeline de déchiffrement/réassemblage (Story 6.3).

## Acceptance Criteria

1. **Given** la localisation des blocs (Story 6.1) est complète et `DownloadState.Located(fileHash, blockMap)` est émis par `ExplorerViewModel`
   **When** `ExplorerViewModel.startDownload(fileHash)` est appelé (chaîné automatiquement après `Located`)
   **Then** `DownloadFileBlocksUseCase.invoke(blockMap, k)` est déclenché

2. **And** le use case ouvre **K+2** connexions TCP parallèles (`connectionScope.launch(Dispatchers.IO)`) — K blocs requis + 2 blocs de secours compétitifs. Si `blockMap.size < k+2`, tous les blocs disponibles sont utilisés (borne inférieure = `blockMap.size`).

3. **And** dès que **K blocs valides** sont reçus (ciphertext SHA-256 vérifié = blockId), les 2 téléchargements les plus lents sont annulés via `Job.cancel()` — le set "gagnant" = les K premiers à avoir validé. Les blocs excédentaires (> K) reçus après la fermeture du set sont ignorés mais non mis en erreur.

4. **And** chaque requête TCP utilise un **timeout ACK adaptatif** : démarre à `BASE_ACK_TIMEOUT_MS = 10_000L`. En cas d'échec (timeout ou I/O), un retry avec `MAX_ACK_TIMEOUT_MS = 30_000L` est déclenché contre un **pair de secours** sélectionné depuis `peerRepository.peers.value` (pair actif non déjà utilisé pour ce blocId).

5. **And** chaque bloc reçu est **immédiatement vérifié** : `sha256Hex(ciphertext) == blockId`. En cas de mismatch, le bloc est rejeté (compté comme échec pour ce pair) et le retry de secours s'applique.

6. **And** si un nœud retourne `BLOCK_NOT_FOUND` (code `0x42`) ou une erreur I/O, un nœud de secours est sollicité depuis `peerRepository.peers.value.filter { isActive && nodeId != failedNodeId && it.ipAddress != null && it.port != null }`. Si aucun fallback disponible, ce bloc est marqué en échec définitif.

7. **And** la progression est exposée via un `Flow<DownloadProgressState>` retourné par le use case — émet `Progress(received, k, failed)` à chaque réception/échec de bloc et `Completed(blocks: Map<Int, DownloadedBlock>)` quand K blocs valides sont accumulés, ou `Failed(reason)` si < K blocs sont récupérables.

8. **And** le résultat final est un `Map<Int, DownloadedBlock>` où `Int = fragmentIndex`, `DownloadedBlock = (blockId, fragmentIndex, isParity, ciphertext)` — prêt à être consommé par Story 6.3 (déchiffrement/réassemblage).

9. **And** côté serveur, `TcpConnectionManager` traite un nouveau discriminant `BLOCK_REQUEST` (`0x40`) via `handleBlockRequest(blockId)` : lit `HostedBlockRepository.getBlock(blockId)` → si trouvé, répond `BLOCK_RESPONSE` (`0x41`) + payload Protobuf `BlockResponseMessage(blockId, fragmentIndex, isParity, ciphertext)`; si absent, répond `BLOCK_NOT_FOUND` (`0x42`) sans payload.

10. **And** la logique client est dans `data/p2p/tcp/BlockDownloadClient.kt` (implémente nouvelle interface `BlockDownloader`), et la logique use case dans `domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCase.kt`.

11. **And** l'état `DownloadState` de `ExplorerViewModel` est étendu : nouveaux variants `Downloading(fileHash, received, k, failed)`, `Downloaded(fileHash, blocks)`, et `Error` réutilisé pour les échecs (< K blocs). Le `DownloadState.Located` déclenche automatiquement `startDownload()`.

## Tasks / Subtasks

- [x] **Task 1** : Ajouter les constantes de canal download dans `BlockTransferChannel` (AC: #9)
  - [x] Subtask 1.1 : Dans `data/p2p/tcp/BlockTransferChannel.kt` ajouter :
    ```kotlin
    const val BLOCK_REQUEST: Byte = 0x40
    const val BLOCK_RESPONSE: Byte = 0x41
    const val BLOCK_NOT_FOUND: Byte = 0x42
    // Limite payload BlockRequestMessage — uniquement un blockId de 64 chars + overhead
    const val MAX_REQUEST_PAYLOAD_BYTES = 256
    ```

- [x] **Task 2** : Créer les messages Protobuf pour le canal download (AC: #9)
  - [x] Subtask 2.1 : Créer `domain/models/BlockRequestMessage.kt` :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class BlockRequestMessage(
        @ProtoNumber(1) val blockId: String = ""
    )
    ```
  - [x] Subtask 2.2 : Créer `domain/models/BlockResponseMessage.kt` :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class BlockResponseMessage(
        @ProtoNumber(1) val blockId: String = "",
        @ProtoNumber(2) val fragmentIndex: Int = 0,
        @ProtoNumber(3) val isParity: Boolean = false,
        @ProtoNumber(4) val ciphertext: ByteArray = ByteArray(0)
    ) {
        override fun equals(other: Any?): Boolean { /* contentEquals sur ciphertext — pattern BlockTransferMessage */ }
        override fun hashCode(): Int { /* contentHashCode pattern */ }
    }
    ```

- [x] **Task 3** : Créer le modèle domaine `DownloadedBlock` (AC: #8)
  - [x] Subtask 3.1 : Créer `domain/models/DownloadedBlock.kt` :
    ```kotlin
    data class DownloadedBlock(
        val blockId: String,
        val fragmentIndex: Int,
        val isParity: Boolean,
        val ciphertext: ByteArray
    ) {
        override fun equals(other: Any?): Boolean { /* contentEquals */ }
        override fun hashCode(): Int { /* contentHashCode */ }
    }
    ```

- [x] **Task 4** : Créer le sealed class `DownloadProgressState` (AC: #7)
  - [x] Subtask 4.1 : Créer `domain/usecase/m08_m09_erasure_coding/DownloadProgressState.kt` :
    ```kotlin
    sealed class DownloadProgressState {
        data class Progress(val received: Int, val k: Int, val failed: Int) : DownloadProgressState()
        data class Completed(val blocks: Map<Int, DownloadedBlock>) : DownloadProgressState()
        data class Failed(val reason: String, val received: Int, val k: Int) : DownloadProgressState()
    }
    ```

- [x] **Task 5** : Étendre `HostedBlockRepository` pour le download serveur (AC: #9)
  - [x] Subtask 5.1 : Ajouter dans `domain/repository/HostedBlockRepository.kt` :
    ```kotlin
    suspend fun getBlock(blockId: String): Result<HostedBlockPayload?>
    ```
  - [x] Subtask 5.2 : Créer `domain/models/HostedBlockPayload.kt` :
    ```kotlin
    data class HostedBlockPayload(
        val blockId: String,
        val fragmentIndex: Int,
        val isParity: Boolean,
        val ciphertext: ByteArray
    ) {
        override fun equals(other: Any?): Boolean { /* contentEquals */ }
        override fun hashCode(): Int { /* contentHashCode */ }
    }
    ```
  - [x] Subtask 5.3 : Implémenter dans `data/repository_impl/HostedBlockRepositoryImpl.kt` :
    ```kotlin
    override suspend fun getBlock(blockId: String): Result<HostedBlockPayload?> = withContext(Dispatchers.IO) {
        runCatching {
            // Validation format blockId — défense en profondeur (même regex que ReceiveAndHost)
            if (!BLOCK_ID_REGEX.matches(blockId)) return@runCatching null
            val entity = hostedBlockDao.getHostedBlock(blockId) ?: return@runCatching null
            val file = File(entity.filePath)
            if (!file.exists() || !file.isFile) return@runCatching null
            // Verrou partagé via blockLocks.computeIfAbsent pour éviter lecture pendant écriture atomique
            lockFor(blockId).withLock {
                val bytes = file.readBytes()
                HostedBlockPayload(
                    blockId = entity.blockId,
                    fragmentIndex = entity.fragmentIndex,
                    isParity = entity.isParity,
                    ciphertext = bytes
                )
            }
        }
    }
    ```
    Ajouter `private val BLOCK_ID_REGEX = Regex("^[0-9a-f]{64}$")` dans la companion.

- [x] **Task 6** : Ajouter handler `BLOCK_REQUEST` dans `TcpConnectionManager` (AC: #9)
  - [x] Subtask 6.1 : Ajouter champ :
    ```kotlin
    @Volatile
    var hostedBlockProvider: HostedBlockRepository? = null
    ```
  - [x] Subtask 6.2 : Dans `handleIncomingConnection()` le `when`, ajouter :
    ```kotlin
    BlockTransferChannel.BLOCK_REQUEST -> handleBlockRequest(pushback, socket)
    ```
  - [x] Subtask 6.3 : Implémenter `handleBlockRequest(inp, socket)` :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleBlockRequest(inp: InputStream, socket: Socket) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > BlockTransferChannel.MAX_REQUEST_PAYLOAD_BYTES) {
                Log.w("MobiCloud:TCP", "BLOCK_REQUEST taille invalide: $len — connexion fermée")
                return
            }
            val reqBytes = ByteArray(len).also { data.readFully(it) }
            val req = MobiCloudProtoBuf.decodeFromByteArray(BlockRequestMessage.serializer(), reqBytes)
            if (!BLOCK_ID_REGEX.matches(req.blockId)) {
                Log.w("MobiCloud:TCP", "BLOCK_REQUEST blockId invalide: ${req.blockId.take(16)}")
                return
            }
            val provider = hostedBlockProvider
            if (provider == null) {
                Log.w("MobiCloud:TCP", "BLOCK_REQUEST reçu mais aucun provider configuré")
                sendBlockNotFound(socket)
                return
            }
            val payloadResult = provider.getBlock(req.blockId)
            val payload = payloadResult.getOrNull()
            if (payload == null) {
                sendBlockNotFound(socket)
                return
            }
            val resp = BlockResponseMessage(
                blockId = payload.blockId,
                fragmentIndex = payload.fragmentIndex,
                isParity = payload.isParity,
                ciphertext = payload.ciphertext
            )
            val respBytes = MobiCloudProtoBuf.encodeToByteArray(BlockResponseMessage.serializer(), resp)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(BlockTransferChannel.BLOCK_RESPONSE.toInt())
            out.writeInt(respBytes.size)
            out.write(respBytes)
            out.flush()
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur handleBlockRequest", e)
        }
    }

    private fun sendBlockNotFound(socket: Socket) {
        try {
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(BlockTransferChannel.BLOCK_NOT_FOUND.toInt())
            out.flush()
        } catch (_: Exception) {}
    }
    ```

- [x] **Task 7** : Câbler `hostedBlockProvider` dans `MobicloudP2PService` (AC: #9)
  - [x] Subtask 7.1 : Injecter `HostedBlockRepository` dans `MobicloudP2PService` :
    ```kotlin
    @Inject lateinit var hostedBlockRepository: HostedBlockRepository
    ```
  - [x] Subtask 7.2 : Assigner **avant** `startServer()` (juste après `dhtRelayHandler`) :
    ```kotlin
    tcpConnectionManager.hostedBlockProvider = hostedBlockRepository
    ```

- [x] **Task 8** : Créer l'interface domaine `BlockDownloader` (AC: #10)
  - [x] Subtask 8.1 : Créer `domain/repository/BlockDownloader.kt` :
    ```kotlin
    interface BlockDownloader {
        suspend fun downloadBlock(
            location: ResolvedBlockLocation,
            timeoutMs: Long
        ): Result<DownloadedBlock>
    }
    ```

- [x] **Task 9** : Implémenter `BlockDownloadClient` (AC: #5, #9, #10)
  - [x] Subtask 9.1 : Créer `data/p2p/tcp/BlockDownloadClient.kt` :
    ```kotlin
    @Singleton
    class BlockDownloadClient @Inject constructor() : BlockDownloader {

        @OptIn(ExperimentalSerializationApi::class)
        override suspend fun downloadBlock(
            location: ResolvedBlockLocation,
            timeoutMs: Long
        ): Result<DownloadedBlock> = withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(location.ipAddress, location.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = timeoutMs.toInt()

                val out = DataOutputStream(socket.getOutputStream())
                val reqBytes = MobiCloudProtoBuf.encodeToByteArray(
                    BlockRequestMessage.serializer(),
                    BlockRequestMessage(location.blockId)
                )
                out.writeByte(BLOCK_REQUEST.toInt())
                out.writeInt(reqBytes.size)
                out.write(reqBytes)
                out.flush()

                val inp = DataInputStream(socket.getInputStream())
                val disc = inp.readByte()
                if (disc == BLOCK_NOT_FOUND) {
                    return@withContext Result.failure(
                        IOException("Bloc ${location.blockId.take(16)} introuvable sur ${location.nodeId.take(8)}")
                    )
                }
                if (disc != BLOCK_RESPONSE) {
                    return@withContext Result.failure(
                        IllegalStateException("Discriminateur inattendu: $disc")
                    )
                }
                val len = inp.readInt()
                if (len <= 0 || len > MAX_BLOCK_PAYLOAD_BYTES) {
                    return@withContext Result.failure(
                        IllegalStateException("Taille BLOCK_RESPONSE invalide: $len")
                    )
                }
                val respBytes = ByteArray(len)
                inp.readFully(respBytes)
                val resp = MobiCloudProtoBuf.decodeFromByteArray(BlockResponseMessage.serializer(), respBytes)

                // AC#5 : vérification SHA-256 ciphertext == blockId annoncé
                val computed = sha256Hex(resp.ciphertext)
                if (computed != resp.blockId || resp.blockId != location.blockId) {
                    return@withContext Result.failure(
                        SecurityException("Hash mismatch — attendu=${location.blockId.take(16)} reçu=${computed.take(16)}")
                    )
                }
                Result.success(
                    DownloadedBlock(
                        blockId = resp.blockId,
                        fragmentIndex = resp.fragmentIndex,
                        isParity = resp.isParity,
                        ciphertext = resp.ciphertext
                    )
                )
            } catch (e: SocketTimeoutException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
    ```

- [x] **Task 10** : Créer `DownloadFileBlocksUseCase` avec stratégie K+2 compétitive (AC: #1–#8)
  - [x] Subtask 10.1 : Créer `domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCase.kt` :
    ```kotlin
    @Singleton
    class DownloadFileBlocksUseCase @Inject constructor(
        private val blockDownloader: BlockDownloader,
        private val peerRepository: PeerRepository
    ) {
        fun invoke(
            blockMap: Map<String, ResolvedBlockLocation>,
            k: Int
        ): Flow<DownloadProgressState>
    }
    ```
  - [x] Subtask 10.2 : Algorithme (via `channelFlow { ... }`, consumer-driven via `Channel<DownloadResult>`) :
    ```kotlin
    fun invoke(blockMap: Map<String, ResolvedBlockLocation>, k: Int): Flow<DownloadProgressState> = channelFlow {
        // 1. Guard : locations insuffisantes
        if (blockMap.size < k) {
            send(DownloadProgressState.Failed("Insuffisant : ${blockMap.size}/$k blocs localisés", 0, k))
            return@channelFlow
        }

        // 2. Sélection du pool K+2 (clamp sur blockMap.size), priorité aux pairs les plus fiables
        val poolSize = minOf(blockMap.size, k + 2)
        val locations = blockMap.values.sortedByDescending { it.reliabilityScore }.take(poolSize)

        // 3. Snapshot pairs actifs pour fallback (unique, conforme pattern 6.1 C3)
        val activePeers = peerRepository.peers.value
            .filter { it.isActive && it.ipAddress != null && it.port != null }

        // 4. Consumer-driven race via Channel (pas de polling)
        //    Chaque job tente download primaire, puis fallback si échec, puis émet UN résultat.
        //    Capacité = locations.size pour que les jobs ne suspendent jamais sur send().
        val results = Channel<DownloadResult>(capacity = locations.size)
        val completed = linkedMapOf<Int, DownloadedBlock>()  // ordre d'arrivée préservé
        val usedNodeIds = ConcurrentHashMap.newKeySet<String>().apply { addAll(locations.map { it.nodeId }) }
        var failedCount = 0

        coroutineScope {
            // 5. Lancer K+2 jobs en parallèle
            val jobs = locations.map { loc ->
                launch {
                    try {
                        var result = blockDownloader.downloadBlock(loc, BASE_ACK_TIMEOUT_MS)
                        if (result.isFailure) {
                            // AC#4/AC#6 : fallback = premier pair actif jamais utilisé pour ce round
                            val fallbackPeer = activePeers.firstOrNull { p ->
                                usedNodeIds.add(p.identity.nodeId)  // true si insertion réelle
                            }
                            if (fallbackPeer != null) {
                                val fallbackLoc = loc.copy(
                                    nodeId = fallbackPeer.identity.nodeId,
                                    ipAddress = fallbackPeer.ipAddress!!,
                                    port = fallbackPeer.port!!,
                                    reliabilityScore = fallbackPeer.identity.reliabilityScore
                                )
                                result = blockDownloader.downloadBlock(fallbackLoc, MAX_ACK_TIMEOUT_MS)
                            }
                        }
                        results.trySend(DownloadResult(loc.fragmentIndex, result))
                    } catch (e: CancellationException) {
                        throw e  // propager l'annulation (AC#3 : slow jobs cancelled)
                    } catch (e: Exception) {
                        results.trySend(DownloadResult(loc.fragmentIndex, Result.failure(e)))
                    }
                }
            }

            // 6. Consumer : reçoit résultats, émet Progress, s'arrête dès que k blocs valides OU jobs épuisés
            var remaining = jobs.size
            while (completed.size < k && remaining > 0) {
                val dr = results.receive()
                remaining--
                dr.result.onSuccess { block ->
                    // Dédupe par fragmentIndex (2 pairs redondants → ne compter qu'une fois)
                    if (completed.putIfAbsent(block.fragmentIndex, block) == null) {
                        send(DownloadProgressState.Progress(completed.size, k, failedCount))
                    }
                }.onFailure {
                    failedCount++
                    send(DownloadProgressState.Progress(completed.size, k, failedCount))
                }
            }

            // 7. AC#3 : K atteint → annuler les jobs "perdants" (structured concurrency)
            jobs.forEach { it.cancel() }
            // coroutineScope { } attend que tous les jobs soient terminés (cancelled)
        }

        // 8. Verdict terminal
        if (completed.size >= k) {
            send(DownloadProgressState.Completed(completed.toMap()))
        } else {
            send(DownloadProgressState.Failed(
                "Seulement ${completed.size}/$k blocs valides",
                completed.size,
                k
            ))
        }
    }

    private data class DownloadResult(val fragmentIndex: Int, val result: Result<DownloadedBlock>)
    ```
  - [x] Subtask 10.3 : Companion object :
    ```kotlin
    companion object {
        const val BASE_ACK_TIMEOUT_MS = 10_000L
        const val MAX_ACK_TIMEOUT_MS = 30_000L
    }
    ```
  - [x] Subtask 10.4 : Imports clés : `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.flow.channelFlow`, `kotlinx.coroutines.coroutineScope`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.CancellationException`, `java.util.concurrent.ConcurrentHashMap`.

- [x] **Task 11** : Étendre `DownloadState` (AC: #11)
  - [x] Subtask 11.1 : Modifier `presentation/explorer/DownloadState.kt` :
    ```kotlin
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Locating(val fileHash: String) : DownloadState()
        data class Located(val fileHash: String, val blockMap: Map<String, ResolvedBlockLocation>) : DownloadState()
        data class Downloading(val fileHash: String, val received: Int, val k: Int, val failed: Int) : DownloadState()
        data class Downloaded(val fileHash: String, val blocks: Map<Int, DownloadedBlock>) : DownloadState()
        data class Error(val fileHash: String, val message: String) : DownloadState()
    }
    ```

- [x] **Task 12** : Modifier `ExplorerViewModel` pour chaîner Located → Downloading (AC: #1, #11)
  - [x] Subtask 12.1 : Injecter `DownloadFileBlocksUseCase` + récupérer `k` via `ErasureParameters` :
    ```kotlin
    @Inject constructor(..., private val downloadFileBlocksUseCase: DownloadFileBlocksUseCase, ...)
    ```
  - [x] Subtask 12.2 : Modifier `initiateDownload()` : après `onSuccess { map -> ... }`, **chaîner automatiquement** le download :
    ```kotlin
    localizeFileBlocksUseCase.invoke(fileHash)
        .onSuccess { map ->
            _downloadState.value = DownloadState.Located(fileHash, map)
            startDownload(fileHash, map)
        }
        .onFailure { e -> _downloadState.value = DownloadState.Error(fileHash, e.message ?: "Localisation échouée") }
    ```
  - [x] Subtask 12.3 : Ajouter `private fun startDownload(fileHash: String, blockMap: Map<String, ResolvedBlockLocation>)` :
    ```kotlin
    private fun startDownload(fileHash: String, blockMap: Map<String, ResolvedBlockLocation>) {
        val k = ErasureParameters().k  // même paramètre que l'encodage (4 par défaut)
        viewModelScope.launch {
            downloadFileBlocksUseCase.invoke(blockMap, k).collect { state ->
                when (state) {
                    is DownloadProgressState.Progress ->
                        _downloadState.value = DownloadState.Downloading(fileHash, state.received, state.k, state.failed)
                    is DownloadProgressState.Completed ->
                        _downloadState.value = DownloadState.Downloaded(fileHash, state.blocks)
                    is DownloadProgressState.Failed ->
                        _downloadState.value = DownloadState.Error(fileHash, state.reason)
                }
            }
        }
    }
    ```

- [x] **Task 13** : Mettre à jour `ExplorerScreen` pour afficher les nouveaux états (AC: #11)
  - [x] Subtask 13.1 : Étendre le `LaunchedEffect` snackbar pour couvrir `Downloading`/`Downloaded` :
    ```kotlin
    val terminalDownloadState = remember(downloadState) {
        downloadState.takeIf {
            it is DownloadState.Downloaded || it is DownloadState.Error
        }
    }
    LaunchedEffect(terminalDownloadState) {
        when (val s = terminalDownloadState) {
            is DownloadState.Downloaded -> snackbarHostState.showSnackbar(
                "${s.blocks.size} blocs téléchargés pour ${s.fileHash.take(8)}..."
            )
            is DownloadState.Error -> snackbarHostState.showSnackbar("Erreur : ${s.message}")
            else -> Unit
        }
    }
    ```
  - [x] Subtask 13.2 : **Ne PAS** créer d'UI détaillée de progression — cela est le périmètre explicite de **Story 6.4**. Se limiter à un snackbar pour le feedback terminal. Un log INFO structuré sur chaque `Progress` via `Log.i("MobiCloud:DL", ...)` est acceptable pour débogage.

- [x] **Task 14** : Tests JVM pour `BlockDownloadClient` (AC: #5, #9)
  - [x] Subtask 14.1 : Créer `app/src/test/kotlin/com/mobicloud/data/p2p/tcp/BlockDownloadClientTest.kt`
  - [x] Subtask 14.2 : Utiliser un `ServerSocket` local (`127.0.0.1`, port 0) qui simule un serveur minimal et répond avec un frame Protobuf `BLOCK_RESPONSE`.
  - [x] Subtask 14.3 : Test 1 — **Happy path** : serveur mock répond `BLOCK_RESPONSE` avec ciphertext valide (sha256 = blockId) → `downloadBlock` retourne `Success(DownloadedBlock)` avec même ciphertext.
  - [x] Subtask 14.4 : Test 2 — **Hash mismatch** : serveur mock répond avec `ciphertext` dont `sha256 != blockId` → `Result.failure(SecurityException)`.
  - [x] Subtask 14.5 : Test 3 — **BLOCK_NOT_FOUND** : serveur répond `0x42` → `Result.failure(IOException)`.
  - [x] Subtask 14.6 : Test 4 — **Socket timeout** : serveur accepte mais ne répond jamais, `timeoutMs=500` → `Result.failure(SocketTimeoutException)`.
  - [x] Subtask 14.7 : Test 5 — **Taille frame invalide** (`len = -1` ou `> MAX_BLOCK_PAYLOAD_BYTES`) → `Result.failure(IllegalStateException)`.

- [x] **Task 15** : Tests JVM pour `DownloadFileBlocksUseCase` (AC: #1–#8)
  - [x] Subtask 15.1 : Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCaseTest.kt`
  - [x] Subtask 15.2 : Utiliser `MockBlockDownloader` (`mockk<BlockDownloader>()`) avec `coEvery { downloadBlock(any(), any()) } coAnswers { ... }` pour simuler délais.
  - [x] Subtask 15.3 : Framework : `mockk`, `kotlinx-coroutines-test` (`runTest`, `TestScope`), `turbine` (ou `flow.toList()` sur `flowOf`).
  - [x] Subtask 15.4 : Test 1 — **Happy path K+2, les K premiers gagnent** : 6 pairs (k=4 + 2 extra), 4 répondent en 50ms et 2 en 5000ms → `Completed` émis avec les 4 premiers; vérifier que les 2 slow sont annulés avant 5000ms (observer via `coVerify(exactly = 4)` ou flag side-effect).
  - [x] Subtask 15.5 : Test 2 — **Hash mismatch → fallback** : 4 pairs, 1 retourne `SecurityException`, `peerRepository.peers` contient un pair actif alternatif → fallback appelé avec `MAX_ACK_TIMEOUT_MS`, `Completed` émis.
  - [x] Subtask 15.6 : Test 3 — **Échec définitif** : k=4, seulement 3 blocs valides reçus, aucun fallback possible → `Failed("Seulement 3/4 blocs valides", 3, 4)` émis.
  - [x] Subtask 15.7 : Test 4 — **blockMap insuffisant** : k=4, `blockMap.size = 3` → `Failed("Insuffisant...", 0, 4)` émis immédiatement.
  - [x] Subtask 15.8 : Test 5 — **Progression streaming** : vérifier que `Progress` est émis à chaque réception (ex: `Progress(1, 4, 0)`, `Progress(2, 4, 0)`, ..., `Completed`) via collecte du Flow en liste.
  - [x] Subtask 15.9 : Test 6 — **Dédupe par fragmentIndex** : 2 pairs retournent le même `fragmentIndex=0` (cluster redondant) → une seule entrée dans le `Map<Int, DownloadedBlock>` résultat.

- [x] **Task 16** : Tests JVM pour `HostedBlockRepositoryImpl.getBlock` (AC: #9)
  - [x] Subtask 16.1 : Étendre `app/src/test/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImplTest.kt` (ou créer s'il n'existe pas — cohérence avec pattern story 5.5).
  - [x] Subtask 16.2 : Test 1 — **Happy path** : bloc préalablement sauvegardé via `saveBlock(...)` → `getBlock(blockId)` retourne `Success(HostedBlockPayload)` avec ciphertext identique.
  - [x] Subtask 16.3 : Test 2 — **Bloc absent** : `getBlock("unknown")` → `Success(null)`.
  - [x] Subtask 16.4 : Test 3 — **Fichier DB présent, fichier disque absent** (simuler `file.delete()`) → `Success(null)` (guard `!file.exists()`).
  - [x] Subtask 16.5 : Test 4 — **blockId format invalide** (non hex 64 chars) → `Success(null)` (guard regex).

---

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Description | Action |
|---|---|---|
| `data/p2p/tcp/BlockTransferChannel.kt` | Canal TCP avec 0x20/21/22 (upload) + 0x30/31 (DHT relay) — **MODIFIER** pour ajouter 0x40/41/42 | **MODIFIER** |
| `data/p2p/tcp/TcpConnectionManager.kt` | Pattern `blockReceiverHandler` + `dhtRelayHandler` + `handleIncomingConnection()` `when` — **MODIFIER** pour ajouter `hostedBlockProvider` + case `0x40` | **MODIFIER** |
| `data/p2p/tcp/BlockTransferClient.kt` | Client upload existant (`sendBlock`) — **NE PAS MODIFIER** : créer `BlockDownloadClient.kt` séparé (responsabilités distinctes, pattern SRP) | **RÉUTILISER** (pattern) |
| `data/network/service/MobicloudP2PService.kt` | Pattern wiring handler avant `startServer()` — **MODIFIER** pour ajouter `hostedBlockProvider = hostedBlockRepository` | **MODIFIER** |
| `data/repository_impl/HostedBlockRepositoryImpl.kt` | Impl existante avec `saveBlock/blockExists/deleteBlock` + `blockLocks` Mutex — **MODIFIER** pour ajouter `getBlock()` | **MODIFIER** |
| `domain/repository/HostedBlockRepository.kt` | Interface — **MODIFIER** pour ajouter `getBlock()` | **MODIFIER** |
| `domain/models/ResolvedBlockLocation.kt` | `blockId, fragmentIndex, nodeId, ipAddress, port, reliabilityScore` — produit par Story 6.1 | **RÉUTILISER** |
| `domain/models/ErasureParameters.kt` | `k, n, fragmentSize` (k=4 par défaut) — utilisé pour déterminer le `k` du download | **RÉUTILISER** |
| `domain/repository/PeerRepository.kt` | `peers: StateFlow<List<Peer>>` — lire `.value` pour fallback peer | **RÉUTILISER** |
| `presentation/explorer/DownloadState.kt` | Sealed class existante (Idle/Locating/Located/Error) — **MODIFIER** pour ajouter `Downloading`/`Downloaded` | **MODIFIER** |
| `presentation/explorer/ExplorerViewModel.kt` | `initiateDownload()` + `_downloadState` existants — **MODIFIER** pour chaîner `startDownload()` après `Located` | **MODIFIER** |
| `presentation/explorer/ExplorerScreen.kt` | Collecte `downloadState` + Snackbar — **MODIFIER** pour couvrir `Downloaded` (feedback terminal uniquement, UI détaillée = Story 6.4) | **MODIFIER** |
| `core/format/MobiCloudProtoBuf.kt` | `ProtoBuf` configuré avec `ignoreUnknownKeys = true` — **RÉUTILISER** pour tous les encode/decode | **RÉUTILISER** |
| `domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` | Pattern `BASE_ACK_TIMEOUT_MS=10s / MAX_ACK_TIMEOUT_MS=30s` + fallback peer — **PATTERN À REPRODUIRE** pour le download | **RÉUTILISER** (pattern) |

### ⚠️ CONTRAINTES CRITIQUES

**1. IV et `wrappedMasterKey` — hors scope Story 6.2 :**
La `BlockResponseMessage` ne transporte PAS l'IV ni la clé maîtresse chiffrée. Story 6.2 s'arrête à la récupération des `ciphertext` vérifiés par hash. L'IV et la clé sont gérés par Story 6.3 (pipeline déchiffrement). Si Story 6.3 découvre un gap de stockage de l'IV côté hoster, la solution sera traitée dans le périmètre de 6.3 (pas ici). **Ne pas ajouter de migration DB 7→8 dans 6.2**.

**2. Pas de nouvelle migration Room — DB reste version 7 :**
`HostedBlockEntity` n'est pas modifiée. `getBlock()` utilise les colonnes existantes (`block_id, fragment_index, is_parity, file_path`). Aucun changement de schéma.

**3. `MobiCloudProtoBuf` obligatoire — pas `ProtoBuf` nu :**
Utiliser `com.mobicloud.core.format.MobiCloudProtoBuf` partout (cohérence avec `BlockTransferClient`, `handleIncomingBlockTransfer`). NE PAS importer `kotlinx.serialization.protobuf.ProtoBuf` directement — le handler DHT relay utilise `ProtoBuf` nu historiquement, mais pour le nouveau canal download on s'aligne sur `MobiCloudProtoBuf` (décision de convergence).

**4. `connectionScope.launch(Dispatchers.IO)` — pas de `runBlocking` :**
Le nouveau handler `handleBlockRequest` est `suspend` et s'exécute dans `connectionScope.launch(Dispatchers.IO)` qui dispatche déjà chaque connexion entrante. Zéro `runBlocking` supplémentaire. Ne PAS introduire le pattern `runBlocking` que la story 5.5 a volontairement évité.

**5. K+2 vs `blockMap.size` — clamp inférieur :**
Si `blockMap.size < k+2` (ex: 5 blocs localisés pour k=4 → seulement 5 candidats), utiliser `min(blockMap.size, k+2)` comme pool initial. Si `blockMap.size < k` → échec immédiat (`Failed`). La stratégie K+2 est best-effort sur le nombre de locations disponibles.

**6. Dédupe par `fragmentIndex`, PAS par `blockId` :**
Deux pairs peuvent héberger le même `blockId` (même ciphertext) — cas normal d'une réplication. Le résultat est un `Map<Int, DownloadedBlock>` indexé par `fragmentIndex`. Utiliser `ConcurrentHashMap.putIfAbsent(fragmentIndex, block)` — si `wasAbsent == false`, ignorer (déjà reçu). Cela évite aussi le double-comptage dans `Progress.received`.

**7. Annulation coopérative des jobs "perdants" :**
Après `completed.size >= k`, itérer `jobs.forEach { it.cancel() }`. Les téléchargements en cours déclenchent `SocketTimeoutException` ou `InterruptedIOException` → catch dans `downloadBlock` → traité comme failure silencieuse (ne pas incrémenter `failedCount` pour une annulation volontaire — différencier via `if (e is CancellationException) throw e` AVANT le catch générique IOException).

**8. `sha256Hex` vérifie ciphertext côté client (défense en profondeur) :**
Même si le hoster a déjà vérifié à la réception (Story 5.5 AC), on revérifie à la sortie pour détecter :
- Corruption disque côté hoster
- Man-in-the-middle (pair malveillant relayant un bloc incorrect)
- Bug de sérialisation Protobuf
Pattern identique à `BlockTransferClient` qui vérifie la signature de l'ACK.

**9. Fallback peer — stratégie simple pour MVP :**
Ne PAS re-requêter la DHT en runtime pour trouver un autre hoster du même bloc. Le fallback parcourt `peerRepository.peers.value` et sélectionne le **premier pair actif différent** (best-effort). En pratique, si le bloc est répliqué sur plusieurs nœuds, `blockMap` contiendra déjà plusieurs entrées (cas couvert par le parallelisme K+2 initial). Le fallback peer est une dernière chance — si le pair ne détient pas le bloc, il répondra `BLOCK_NOT_FOUND`, ce qui est géré.

**10. Race K+2 via `Channel<DownloadResult>` consumer-driven — PAS de polling :**
Pattern choisi : chaque job `launch` envoie UN résultat (`trySend`) sur un `Channel<DownloadResult>(capacity = locations.size)`. Un consumer unique fait `results.receive()` dans une boucle `while (completed.size < k && remaining > 0)`. Dès que K blocs valides sont accumulés, on sort de la boucle et `jobs.forEach { it.cancel() }` annule les lents. `coroutineScope { }` attend naturellement la fin des cancellations (structured concurrency). **Aucun `delay(50)` / polling** — l'arrêt est déterministe, piloté par les événements. Backpressure : `Channel` à capacité `locations.size` garantit que `trySend` ne peut jamais échouer pour cause de buffer plein. `channelFlow` pour émettre `Progress`/`Completed`/`Failed` vers le ViewModel.

**11. Timeouts adaptatifs — version simplifiée Story 6.2 :**
La spec epic mentionne "timeout ACK adaptatif qui s'allonge en cas d'interférences Wi-Fi élevées". Pour Story 6.2, on implémente la version simplifiée : `BASE_ACK_TIMEOUT_MS=10s` pour le tentative initiale, `MAX_ACK_TIMEOUT_MS=30s` pour le retry/fallback — pattern identique à `DistributeEncryptedBlocksUseCase`. La détection Wi-Fi BSSID density dynamique est **hors scope** (aucune infra `WifiDensityProvider` n'existe dans le codebase — à créer en Story future si besoin).

**12. Protobuf — valeurs par défaut obligatoires :**
`BlockRequestMessage` et `BlockResponseMessage` doivent avoir des valeurs par défaut sur tous les champs (`= ""`/`= 0`/`= false`/`= ByteArray(0)`) — requis par kotlinx.serialization avec `ignoreUnknownKeys=true` (convention MobiCloud). Sans défaut, les messages manquants déclenchent `MissingFieldException`.

**13. `equals`/`hashCode` pour les classes à `ByteArray` :**
`BlockResponseMessage`, `DownloadedBlock`, `HostedBlockPayload` — toutes contiennent `ciphertext: ByteArray`. Override `equals`/`hashCode` avec `contentEquals` / `contentHashCode` (pattern établi dans `BlockTransferMessage.kt`, `BlockAckMessage.kt`). Sans override, `data class` utilise `==` référentielle pour `ByteArray`.

**14. `BLOCK_ID_REGEX = Regex("^[0-9a-f]{64}$")` — réutiliser la constante :**
Validation du `blockId` reçu via réseau identique à Stories 5.5 et 6.1. La regex est définie dans `ReceiveAndHostBlockUseCase.Companion` (private). Soit exposer en `internal`, soit dupliquer localement dans `TcpConnectionManager.Companion` (déjà fait pour DHT relay : `BLOCK_ID_REGEX`). Préférer la duplication si la constante reste `private` — cohérent avec le status quo.

**15. Scope de `coroutineScope { ... }` pour la collection de jobs :**
Utiliser `coroutineScope { ... }` (pas `GlobalScope`) pour que la cancellation du consumer (ViewModel scope cancelled) propage aux téléchargements en cours. Pattern : structured concurrency.

### 📁 Arborescence cible après implémentation

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   ├── models/
│   │   ├── BlockRequestMessage.kt                      ← NOUVEAU
│   │   ├── BlockResponseMessage.kt                     ← NOUVEAU
│   │   ├── DownloadedBlock.kt                          ← NOUVEAU
│   │   └── HostedBlockPayload.kt                       ← NOUVEAU
│   ├── repository/
│   │   ├── BlockDownloader.kt                          ← NOUVEAU (interface)
│   │   └── HostedBlockRepository.kt                    ← MODIFIÉ (+getBlock)
│   └── usecase/
│       └── m08_m09_erasure_coding/
│           ├── DownloadFileBlocksUseCase.kt            ← NOUVEAU
│           └── DownloadProgressState.kt                ← NOUVEAU
├── data/
│   ├── p2p/tcp/
│   │   ├── BlockTransferChannel.kt                     ← MODIFIÉ (+0x40/41/42)
│   │   ├── BlockDownloadClient.kt                      ← NOUVEAU (impl BlockDownloader)
│   │   └── TcpConnectionManager.kt                     ← MODIFIÉ (+hostedBlockProvider, +handleBlockRequest)
│   ├── repository_impl/
│   │   └── HostedBlockRepositoryImpl.kt                ← MODIFIÉ (+getBlock, +BLOCK_ID_REGEX)
│   └── network/service/
│       └── MobicloudP2PService.kt                      ← MODIFIÉ (+hostedBlockRepository inject, +wiring)
├── di/
│   └── HostingModule.kt (ou NetworkModule.kt)          ← MODIFIÉ (+@Binds BlockDownloader → BlockDownloadClient)
└── presentation/explorer/
    ├── DownloadState.kt                                 ← MODIFIÉ (+Downloading, +Downloaded)
    ├── ExplorerViewModel.kt                            ← MODIFIÉ (+downloadFileBlocksUseCase, +startDownload chaîné)
    └── ExplorerScreen.kt                               ← MODIFIÉ (+snackbar Downloaded)

app/src/test/kotlin/com/mobicloud/
├── data/p2p/tcp/
│   └── BlockDownloadClientTest.kt                      ← NOUVEAU (5 tests JVM avec ServerSocket local)
├── data/repository_impl/
│   └── HostedBlockRepositoryImplTest.kt                ← NOUVEAU ou MODIFIÉ (4 tests getBlock)
└── domain/usecase/m08_m09_erasure_coding/
    └── DownloadFileBlocksUseCaseTest.kt                ← NOUVEAU (6 tests JVM)
```

### 🔗 Dépendances inter-stories

- **Story 5.2 (done) → Story 6.2 :** `EncryptedFragment` produit l'`iv` au chiffrement — IV non stocké côté hoster aujourd'hui ; gap à traiter en **Story 6.3**, pas ici.
- **Story 5.3 (done) → Story 6.2 :** `DistributeEncryptedBlocksUseCase` pattern `BASE_ACK_TIMEOUT_MS`/`MAX_ACK_TIMEOUT_MS` + fallback peer → reproduit symétriquement pour le download.
- **Story 5.5 (done) → Story 6.2 :** `HostedBlockRepositoryImpl` avec `blockLocks`/`ConcurrentHashMap<String, Mutex>` → réutilisé pour `getBlock` (lecture sous `lockFor(blockId).withLock`). Pattern `connectionScope.launch(Dispatchers.IO)` dans `TcpConnectionManager`. Constantes `BLOCK_ID_REGEX`, `MAX_BLOCK_PAYLOAD_BYTES`.
- **Story 6.1 (done) → Story 6.2 :** `LocalizeFileBlocksUseCase` → `Map<String, ResolvedBlockLocation>` consommé en input. `DownloadState.Located(fileHash, blockMap)` enchaîné sur `startDownload()`.
- **Story 6.2 → Story 6.3 :** `Map<Int, DownloadedBlock>` (clé = fragmentIndex) est exactement ce que le décodeur EC attend. `DownloadedBlock.ciphertext` est consommé par `DecodeErasureFragmentsUseCase.decode()` après déchiffrement AES-256 GCM (Story 6.3).
- **Story 6.2 → Story 6.4 :** L'UI détaillée (barre de progression avec nodeId tronqué, latence, indicateur "⏳ Attente", ModalBottomSheet final) est entièrement le périmètre de 6.4. Ici on se limite à un Snackbar simple + `DownloadState.Downloading(received, k, failed)` qui fournit déjà toute la donnée nécessaire.
- **Story 6.2 ← Story 5.5 (report)** : Le défer `runBlocking` dans `handleIncomingBlockTransfer` est marqué "à planifier avec la correction des handlers Gossip". Comme Story 6.1 a déjà rendu `handleIncomingBlockTransfer` suspend et supprimé le `runBlocking` (cf. review findings 6.1), **cette résolution est acquise** — `handleBlockRequest` s'ajoute suspend naturellement.

### 🧪 Testing Requirements

**Total attendu : ~15 tests JVM purs** — pas de Robolectric, pas d'émulateur Android.

**Mocks clés :**
- `mockk<BlockDownloader>()` — `coEvery { downloadBlock(any(), any()) } coAnswers { delay(...); Result.success(...) }` pour simuler des latences variables.
- `mockk<HostedBlockRepository>()` — `coEvery { getBlock(any()) } returns Result.success(payload)` / `returns Result.success(null)`.
- `mockk<PeerRepository>()` — `every { peers } returns MutableStateFlow(listOf(peer1, peer2, fallbackPeer))`.
- Pour `BlockDownloadClientTest` : `ServerSocket(0)` + `Thread` qui lit/écrit un frame manuellement (pas de mock — test d'intégration socket local).

**Builders réutilisables (à définir en `TestFixtures.kt` si pas déjà fait) :**
```kotlin
fun buildResolvedLocation(
    blockId: String = "a".repeat(64),
    fragmentIndex: Int = 0,
    nodeId: String = "node-$fragmentIndex",
    ip: String = "127.0.0.1",
    port: Int = 9000,
    score: Float = 0.8f
) = ResolvedBlockLocation(blockId, fragmentIndex, nodeId, ip, port, score)

fun buildDownloadedBlock(
    blockId: String = sha256Hex(ByteArray(32) { 1 }),
    fragmentIndex: Int = 0,
    isParity: Boolean = false,
    ciphertext: ByteArray = ByteArray(32) { 1 }
) = DownloadedBlock(blockId, fragmentIndex, isParity, ciphertext)
```

**Stratégie de test race condition (Test 15.4) :**
Utiliser `runTest` avec `UnconfinedTestDispatcher` pour contrôler l'ordonnancement. Simuler 4 downloads rapides (50ms) et 2 lents (5000ms) via `delay(50)` / `delay(5000)` dans `coAnswers`. Avancer le temps virtuel : `advanceTimeBy(100)` → les 4 rapides doivent avoir emit `Completed`, les 2 lents doivent être `cancelled` (vérifier via `isActive=false` ou `coVerify(exactly = 4)` sur un side-effect compteur).

### 📚 Références patterns

- [BlockTransferChannel.kt](../../app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferChannel.kt) — constantes 0x20/21/22 + 0x30/31 à étendre avec 0x40/41/42. MAX payloads, timeouts.
- [BlockTransferClient.kt](../../app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferClient.kt) — pattern `Socket() + connect + soTimeout + MobiCloudProtoBuf + DataInput/OutputStream` à reproduire pour `BlockDownloadClient`.
- [TcpConnectionManager.kt](../../app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt) — `@Volatile var handler`, `when (firstByte.toByte())`, `connectionScope.launch(Dispatchers.IO)`, pattern `sendNack`/sendNotFound.
- [HostedBlockRepositoryImpl.kt](../../app/src/main/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImpl.kt) — `blockLocks.computeIfAbsent + withLock` pour lectures cohérentes pendant les écritures concurrentes.
- [DistributeEncryptedBlocksUseCase.kt](../../app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt) — pattern `BASE_ACK_TIMEOUT_MS / MAX_ACK_TIMEOUT_MS`, fallback peer depuis `peerRepository.peers.value`, structure `DeliveryRecord` parallèle à `DownloadedBlock`.
- [LocalizeFileBlocksUseCase.kt](../../app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/LocalizeFileBlocksUseCase.kt) — input `fileHash` → `Map<String, ResolvedBlockLocation>`; invariants sur `activePeers` filter.
- [MobicloudP2PService.kt](../../app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt) — ligne 129-130 pattern wiring handler avant `startServer()`.
- [ExplorerViewModel.kt](../../app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt) — guard atomique `_downloadState.update { }`, pattern `onSuccess/onFailure`.
- [Source: epics.md#Story 6.2](../planning-artifacts/epics.md) — AC originaux et user story.
- [Source: architecture.md#Module 8 Récupération Pipeline de Streaming Actif](../planning-artifacts/architecture.md) — "téléchargement compétitif (K+2) couplé à un pipeline de rendu réactif".

## Previous Story Intelligence

**Learnings critiques de Story 6.1 (Localisation DHT) :**

- **`handleIncomingConnection` est `suspend`** : depuis 6.1, le signature est `suspend fun` pour permettre aux handlers (`handleDhtLookupRelay`) d'appeler des opérations suspend sans `runBlocking`. `handleBlockRequest` suit la même forme.
- **`dhtRelayHandler = dhtRepository`** : wiring avant `startServer()` — reproduire `hostedBlockProvider = hostedBlockRepository`.
- **Regex `^[0-9a-f]{64}$` systématique** : toute entrée `blockId` venant du réseau est validée. Applicable à `BlockRequestMessage.blockId`.
- **Snapshot `peerRepository.peers.value`** : synchrone thread-safe dans coroutine. Prendre le snapshot UNE fois au début du use case pour la boucle fallback, éviter de le re-lire à chaque itération (cohérent avec C3 de 6.1).
- **Validation peer-supplied IP/port** : déferrée en 6.1 (SSRF P2P inhérent). **6.2 hérite de la même décision** : `ResolvedBlockLocation.ipAddress/port` est utilisée sans validation supplémentaire pour la connexion TCP sortante — contrat P2P.
- **Lookups séquentiels → parallélisme candidat 6.2** : le defer de 6.1 mentionne "Parallélisme (N goroutines) non requis par la spec 6.1, candidat Story 6.2". **Story 6.2 matérialise ce parallélisme** : `coroutineScope { jobs = locations.map { launch { downloadBlock(it) } } }`.

**Learnings de Stories 5.x (Distribution/Hébergement) :**

- **`BASE_ACK_TIMEOUT_MS=10s + MAX_ACK_TIMEOUT_MS=30s`** : tuples éprouvés en 5.3 pour upload — repris tels quels pour download symétrique.
- **`sha256Hex` dupliqué** : `DistributeEncryptedBlocksUseCase` et `ExplorerViewModel` contiennent le même `sha256Hex`. Ne pas consolider dans 6.2 (déferré en W9 story 5.4) — dupliquer localement dans `BlockDownloadClient`.
- **`blockLocks` non purgé** : bug connu (W de 5.5) — `ConcurrentHashMap<String, Mutex>` croît indéfiniment. Ne PAS corriger dans 6.2 (hors scope), juste réutiliser `lockFor(blockId)` pour `getBlock()`.
- **`connectionScope.launch(Dispatchers.IO)`** : anti-DoS pattern établi. Zéro `runBlocking`.
- **`runBlocking` dans `handleIncomingBlockTransfer`** : cette defer de 5.5 a été **résolue dans 6.1** (handler rendu suspend). L'état actuel du codebase ne contient plus de `runBlocking` dans les handlers TCP — `handleBlockRequest` profite de cette correction.

**Learnings de Stories 4.x (CRDT/Gossip) :**

- **`MobiCloudProtoBuf`** (pas `ProtoBuf` nu) : configuration `ignoreUnknownKeys=true` requise pour la compat Protobuf. Pattern ancré dans le domaine pour tous les messages réseau depuis Epic 4.

## NFR Compliance

**NFR-03 (CPU ≤ 5%) :** Le parallélisme K+2 crée `k+2 = 6` coroutines sur `Dispatchers.IO` (thread pool borné JVM). Lecture fichier + sérialisation Protobuf + socket — peu intensif CPU. Vérification SHA-256 par bloc : ~1ms pour 256 KB sur ARM moderne. Overhead négligeable.

**NFR-01 (Latence cluster stable) :** La stratégie K+2 compétitive garantit que le download termine en `max(latency des K plus rapides)`, indépendant des pairs les plus lents (annulés). Objectif tacite : `<3s` pour un fichier de 1 MB sur un cluster LAN.

**NFR — Résilience :** Le fallback sur `MAX_ACK_TIMEOUT_MS=30s` + nouveau pair si échec initial couvre les pertes de pair unique. Si < K blocs récupérables → `Failed` explicite (pas de blocage infini).

**Sécurité :**
- `BlockRequestMessage` ne contient que `blockId` (SHA-256 ciphertext). Zéro info sur contenu/propriétaire. Zero-Trust préservé.
- `BlockResponseMessage` contient le ciphertext AES-GCM chiffré + métadonnées non sensibles (`fragmentIndex`, `isParity`). Pas de clé transmise.
- Vérification SHA-256 côté client (défense en profondeur) détecte tampering et corruption disque.
- Pas d'authentification du pair relayant — acceptable car le ciphertext est auto-vérifié (hash = blockId engagement). Un pair malveillant ne peut que refuser le service (DoS léger, non confidentialité).

**NFR — Limites mémoire :** Un bloc = max 2 MB (`MAX_BLOCK_PAYLOAD_BYTES`). K+2 blocs simultanés = max 12 MB peak RAM. Acceptable sur Android 2+ GB. Pas de streaming dans 6.2 (différé en 6.3).

---

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (1M context) — Claude Code

### Debug Log References

- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL (warnings pré-existants uniquement).
- `./gradlew :app:testDebugUnitTest` ciblé sur les 3 nouvelles classes : 15/15 tests OK.
- `./gradlew :app:testDebugUnitTest` complet : 163/167 OK ; 4 échecs `ErasureProgressViewModelTest` **pré-existants** (vérifié via `git stash -u` sur baseline `d1cf04b` — mêmes 4 échecs sans le code 6.2). Aucune régression introduite.

### Completion Notes List

**Implémentation conforme au spec, périmètre respecté :**

1. **Canal TCP download (0x40/0x41/0x42)** ajouté dans `BlockTransferChannel`, handler suspend `handleBlockRequest` câblé dans `TcpConnectionManager` (sans `runBlocking`, comme acquis depuis 6.1). Wiring `hostedBlockProvider = hostedBlockRepository` placé **avant** `startServer()` dans `MobicloudP2PService` — pas de race-window.
2. **`HostedBlockRepository.getBlock`** lit sous `lockFor(blockId).withLock` pour cohérence avec l'écriture atomique de `saveBlock`. Validation regex `^[0-9a-f]{64}$` côté repo + côté handler (défense en profondeur).
3. **`BlockDownloadClient`** symétrique de `BlockTransferClient` : `Socket + MobiCloudProtoBuf + DataInput/Output`. Vérification SHA-256 systématique : `computed == resp.blockId == location.blockId` — détecte tampering, corruption, et confusion entre blocs annoncés.
4. **`DownloadFileBlocksUseCase` — race K+2 consumer-driven** : `Channel<DownloadResult>(capacity = locations.size)` garantit qu'aucun `trySend` ne suspend ; consumer émet `Progress` à chaque réception unique (`putIfAbsent` sur `fragmentIndex` pour dédupe les répliques) ; `coroutineScope` annule structurellement les jobs perdants dès K atteint. Pas de polling, pas de `delay`, arrêt déterministe.
5. **Fallback peer** : snapshot unique `peerRepository.peers.value` (pattern 6.1 C3), `usedNodeIds = ConcurrentHashMap.newKeySet()` initialisé avec les nodeId du pool primaire, `add()` retourne `true` uniquement si nouveau — sélection atomique d'un fallback distinct par job concurrent. Retry avec `MAX_ACK_TIMEOUT_MS = 30s` (vs `BASE_ACK_TIMEOUT_MS = 10s` initial).
6. **ViewModel chaîné** : `initiateDownload()` → `Located` → `startDownload()` automatique (k = `ErasureParameters().k = 4`). Émet `Downloading(received, k, failed)` à chaque `Progress`, `Downloaded(blocks)` ou `Error(message)` à la sortie.
7. **UI minimale (Story 6.4 = périmètre futur)** : Snackbar uniquement sur états terminaux (`Downloaded`/`Error`). Log `MobiCloud:DL` INFO sur chaque `Progress` pour débogage.

**Tests JVM purs (15) :**
- `BlockDownloadClientTest` — 5 tests `ServerSocket` local : happy path, hash mismatch (SecurityException), BLOCK_NOT_FOUND (IOException), socket timeout (SocketTimeoutException), frame size invalide (IllegalStateException).
- `DownloadFileBlocksUseCaseTest` — 6 tests mockk : K+2 race (4 rapides gagnent, 2 lents annulés), fallback réussit avec timeout étendu vérifié, échec définitif <K, blockMap insuffisant Failed immédiat, streaming Progress strictement croissant, dédupe par `fragmentIndex` (k=2 sur 4 répliques).
- `HostedBlockRepositoryImplTest` — 4 tests TemporaryFolder + DAO mocké : happy path lecture, DAO retourne null, fichier disque manquant, blockId format invalide (regex).

**Hors scope confirmé (à traiter en stories ultérieures) :**
- IV / wrappedMasterKey transport pour le pipeline de déchiffrement → **Story 6.3** (la `BlockResponseMessage` ne transporte que le ciphertext + métadonnées non sensibles).
- UI détaillée de progression (barre nodeId, latence, ModalBottomSheet) → **Story 6.4**.
- WifiDensityProvider pour timeouts adaptatifs dynamiques → version simplifiée 10s/30s, identique à la distribution 5.3.

### Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-04-22 | 0.1.0 | Story 6.2 implémentée — canal TCP download (0x40/41/42), `BlockDownloadClient`, `DownloadFileBlocksUseCase` K+2 compétitif, chaînage `Located → Downloading → Downloaded` dans `ExplorerViewModel`. 15 tests JVM ajoutés, tous au vert. Status: review. | Claude (Opus 4.7) |

### File List

**Nouveaux fichiers (production) :**
- `app/src/main/kotlin/com/mobicloud/domain/models/BlockRequestMessage.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/BlockResponseMessage.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/DownloadedBlock.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/HostedBlockPayload.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/BlockDownloader.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DownloadProgressState.kt`
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockDownloadClient.kt`

**Fichiers modifiés (production) :**
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferChannel.kt` — constantes `BLOCK_REQUEST/RESPONSE/NOT_FOUND` + `MAX_REQUEST_PAYLOAD_BYTES`.
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt` — `hostedBlockProvider`, case `BLOCK_REQUEST` dans `when`, `handleBlockRequest()`, `sendBlockNotFound()`.
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` — `@Inject hostedBlockRepository` + wiring `hostedBlockProvider`.
- `app/src/main/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImpl.kt` — `getBlock()` + `BLOCK_ID_REGEX` companion.
- `app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt` — signature `getBlock(): Result<HostedBlockPayload?>`.
- `app/src/main/kotlin/com/mobicloud/di/BlockTransferModule.kt` — `provideBlockDownloader(BlockDownloadClient): BlockDownloader`.
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/DownloadState.kt` — variants `Downloading`, `Downloaded`.
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt` — injection `DownloadFileBlocksUseCase`, méthode `startDownload()`, chaînage automatique post-`Located`.
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt` — terminal snackbar étendu à `Downloaded` (UI détaillée déférée à 6.4).

**Nouveaux fichiers (tests) :**
- `app/src/test/kotlin/com/mobicloud/data/p2p/tcp/BlockDownloadClientTest.kt` (5 tests).
- `app/src/test/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImplTest.kt` (4 tests).
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCaseTest.kt` (6 tests).

**Fichiers modifiés (tests) :**
- `app/src/test/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModelTest.kt` — ajout du paramètre `downloadFileBlocksUseCase` au constructeur (mock `relaxed = true`).
- `app/src/test/kotlin/com/mobicloud/presentation/explorer/ErasureProgressViewModelTest.kt` — idem.
