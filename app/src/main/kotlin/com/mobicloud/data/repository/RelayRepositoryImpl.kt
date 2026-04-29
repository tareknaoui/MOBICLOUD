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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalSerializationApi::class)
class RelayRepositoryImpl @Inject constructor(
    private val client: RelayWebSocketClient,
    private val receiveAndHostBlockUseCase: ReceiveAndHostBlockUseCase
) : RelayRepository {

    private val _connectionState = MutableStateFlow(RelayConnectionState.CONNECTING)
    override val connectionState: StateFlow<RelayConnectionState> = _connectionState.asStateFlow()

    // Scope lié au Foreground Service — dans l'app réelle, ce scope est injecté via Hilt
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        repoScope.launch {
            client.connect(RELAY_SERVER_URLS.first()).collect { event ->
                when (event) {
                    is RelayEvent.Connected    -> _connectionState.value = RelayConnectionState.CONNECTED
                    is RelayEvent.Disconnected -> _connectionState.value = RelayConnectionState.OFFLINE
                    is RelayEvent.BlockReceived -> {
                        runCatching {
                            MobiCloudProtoBuf.decodeFromByteArray(
                                BlockTransferMessage.serializer(), event.data
                            )
                        }.onSuccess { blockMsg ->
                            runCatching {
                                receiveAndHostBlockUseCase.receive(blockMsg)
                            }.onFailure { e ->
                                Log.w("RelayRepo", "Échec hébergement bloc blockId=${event.blockId.take(32)}: ${e.message}")
                            }
                        }.onFailure { e ->
                            Log.w("RelayRepo", "Désérialisation FORWARD échouée blockId=${event.blockId.take(32)}: ${e.message}")
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    override suspend fun uploadBlock(destNodeId: String, blockId: String, data: ByteArray): Result<Unit> {
        val result = client.uploadBlock(destNodeId, blockId, data)
        if (result.isSuccess) _connectionState.value = RelayConnectionState.RELAY_HA
        return result
    }

    override suspend fun fetchSuperPeers(): Result<List<RelayPeer>> = runCatching {
        client.sendGetPeers()
        // La réponse arrive via Flow<RelayEvent.PeerList> — à consommer par SignalingRepositoryImpl (Story 2.1)
        // Ici on retourne une liste vide comme placeholder; l'impl complète est dans Story 2.1
        emptyList()
    }
}
