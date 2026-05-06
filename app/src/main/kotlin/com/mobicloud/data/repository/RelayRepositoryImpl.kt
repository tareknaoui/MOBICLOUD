package com.mobicloud.data.repository

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.data.p2p.websocket.RELAY_SERVER_URLS
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.repository.RelayConnectionState
import com.mobicloud.domain.repository.RelayRepository
import com.mobicloud.domain.usecase.m08_hosting.ReceiveAndHostBlockUseCase
import com.mobicloud.domain.usecase.m08_hosting.RespondToBlockRequestUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val MAX_REQUEST_BLOCK_TIMEOUT_MS = 5L * 60_000L

@Singleton
@OptIn(ExperimentalSerializationApi::class)
class RelayRepositoryImpl @Inject constructor(
    private val client: RelayWebSocketClient,
    private val receiveAndHostBlockUseCase: ReceiveAndHostBlockUseCase,
    // Provider casse le cycle Hilt : RespondToBlockRequestUseCase dépend de RelayRepository
    // (pour appeler uploadBlock côté réponse) ; RelayRepositoryImpl dépend du use-case
    // pour le dispatch des BlockRequestForwarded. Provider rompt le cycle à l'injection.
    private val respondToBlockRequestUseCase: Provider<RespondToBlockRequestUseCase>
) : RelayRepository {

    private val _connectionState = MutableStateFlow(RelayConnectionState.CONNECTING)
    override val connectionState: StateFlow<RelayConnectionState> = _connectionState.asStateFlow()

    // Bus interne partagé : tous les RelayEvent émis par le client y transitent,
    // ce qui permet à fetchSuperPeers() de réagir aux PeerList sans race condition.
    private val _relayEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 64)

    // Story 9.4 — requêtes pull en vol. Clé = blockId. Le BlockReceived correspondant fulfill
    // le deferred au lieu d'aller au pipeline d'hébergement.
    private val pendingBlockRequests =
        ConcurrentHashMap<String, CompletableDeferred<BlockTransferMessage>>()

    // Scope lié au Foreground Service — dans l'app réelle, ce scope est injecté via Hilt
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        repoScope.launch {
            client.connect(RELAY_SERVER_URLS.first()).collect { event ->
                _relayEvents.tryEmit(event)
                when (event) {
                    is RelayEvent.Connected    -> _connectionState.value = RelayConnectionState.CONNECTED
                    is RelayEvent.Disconnected -> {
                        _connectionState.value = RelayConnectionState.OFFLINE
                        purgePendingBlockRequestsOnDisconnect()
                    }
                    is RelayEvent.BlockReceived -> handleBlockReceived(event)
                    is RelayEvent.BlockRequestForwarded -> handleBlockRequestForwarded(event)
                    is RelayEvent.Ack, is RelayEvent.PeerList, is RelayEvent.Error -> Unit
                }
            }
        }
    }

    /**
     * Story 9.4 — dispatch dichotomique : si ce blockId est en attente d'une requête pull,
     * fulfill le deferred (le bloc est consommé par le download courant). Sinon, route vers
     * le pipeline d'hébergement (placement initial 5.5 ou push direct 9.3).
     */
    private suspend fun handleBlockReceived(event: RelayEvent.BlockReceived) {
        val pending = pendingBlockRequests.remove(event.blockId)
        if (pending != null) {
            runCatching {
                MobiCloudProtoBuf.decodeFromByteArray(BlockTransferMessage.serializer(), event.data)
            }.fold(
                onSuccess = { pending.complete(it) },
                onFailure = { e ->
                    Log.w("RelayRepo", "[INTER-CLUSTER][PULL] désérialisation réponse échouée ${event.blockId.take(16)}: ${e.message}")
                    pending.completeExceptionally(e)
                }
            )
            return
        }

        // Comportement legacy : route vers le pipeline d'hébergement (Story 5.5 / 9.3 push direct).
        val blockMsg = runCatching {
            MobiCloudProtoBuf.decodeFromByteArray(BlockTransferMessage.serializer(), event.data)
        }.getOrElse { e ->
            Log.w("RelayRepo", "Désérialisation FORWARD échouée blockId=${event.blockId.take(32)}: ${e.message}")
            return
        }
        try {
            receiveAndHostBlockUseCase.receive(blockMsg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("RelayRepo", "Échec hébergement bloc blockId=${event.blockId.take(32)}: ${e.message}")
        }
    }

    /**
     * Story 9.4 — fail-fast tous les deferreds en attente quand la WSS coupe.
     * Sans ça, un caller qui a demandé un bloc juste avant la déco attend `timeoutMs`
     * complet en pure perte (la réponse ne peut plus arriver — le serveur ne buffer pas REQUEST_BLOCK).
     */
    private fun purgePendingBlockRequestsOnDisconnect() {
        val keys = pendingBlockRequests.keys.toList()
        keys.forEach { blockId ->
            pendingBlockRequests.remove(blockId)?.completeExceptionally(
                IOException("Relais déconnecté — REQUEST_BLOCK ${blockId.take(16)} abandonné")
            )
        }
    }

    /**
     * Story 9.4 — un pair distant me demande un bloc (mode pull inter-cluster).
     * Délègue au use-case répondeur sur le scope du repo, en best-effort.
     */
    private fun handleBlockRequestForwarded(event: RelayEvent.BlockRequestForwarded) {
        repoScope.launch {
            try {
                respondToBlockRequestUseCase.get().respond(event.fromNodeId, event.blockId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("RelayRepo", "[INTER-CLUSTER][RESPOND] échec ${event.blockId.take(16)}: ${e.message}")
            }
        }
    }

    override suspend fun uploadBlock(destNodeId: String, blockId: String, data: ByteArray): Result<Unit> {
        val result = client.uploadBlock(destNodeId, blockId, data)
        if (result.isSuccess) _connectionState.value = RelayConnectionState.RELAY_HA
        return result
    }

    override suspend fun fetchSuperPeers(): Result<List<RelayPeer>> = runCatching {
        // Abonnement AVANT l'envoi de GET_PEERS pour éviter la race condition
        // (réponse serveur arrivant avant que le collecteur soit prêt).
        val deferred = CompletableDeferred<List<RelayPeer>>()
        val listenJob = repoScope.launch {
            _relayEvents
                .filterIsInstance<RelayEvent.PeerList>()
                .first()
                .let { deferred.complete(it.peers) }
        }
        if (!client.sendGetPeers()) {
            listenJob.cancel()
            throw IllegalStateException("Aucune connexion active — GET_PEERS impossible")
        }
        try {
            withTimeoutOrNull(5_000L) { deferred.await() } ?: emptyList()
        } finally {
            listenJob.cancel()
        }
    }

    /**
     * Story 9.4 — pull inter-cluster. Pose un deferred AVANT d'envoyer la frame
     * (pattern pendingUploads pour éviter race FORWARD-arrive-avant-attente).
     *
     * Note : on n'utilise PAS `runCatching` extérieur — il avalerait `CancellationException`
     * et neutraliserait W-9.3-7. On gère explicitement `CancellationException` (re-throw)
     * vs autres exceptions (Result.failure).
     */
    override suspend fun requestBlock(
        remoteNodeId: String,
        blockId: String,
        timeoutMs: Long
    ): Result<BlockTransferMessage> {
        require(timeoutMs in 1..MAX_REQUEST_BLOCK_TIMEOUT_MS) {
            "timeoutMs hors bornes (1..$MAX_REQUEST_BLOCK_TIMEOUT_MS) : $timeoutMs"
        }
        val deferred = CompletableDeferred<BlockTransferMessage>()
        if (pendingBlockRequests.putIfAbsent(blockId, deferred) != null) {
            return Result.failure(
                IllegalStateException("Requête déjà en cours pour blockId=${blockId.take(16)}")
            )
        }
        return try {
            val sent = client.sendRequestBlock(remoteNodeId, blockId)
            if (!sent) {
                pendingBlockRequests.remove(blockId)
                Result.failure(IllegalStateException("Aucune connexion relais active — REQUEST_BLOCK impossible"))
            } else {
                val received = withTimeoutOrNull(timeoutMs) { deferred.await() }
                if (received == null) {
                    pendingBlockRequests.remove(blockId)
                    Result.failure(SocketTimeoutException(
                        "Timeout REQUEST_BLOCK ${blockId.take(16)} après ${timeoutMs}ms"
                    ))
                } else {
                    Result.success(received)
                }
            }
        } catch (e: CancellationException) {
            pendingBlockRequests.remove(blockId)
            throw e
        } catch (e: Exception) {
            pendingBlockRequests.remove(blockId)
            Result.failure(e)
        }
    }
}
