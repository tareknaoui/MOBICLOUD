package com.mobicloud.data.p2p.join

import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.JoinSubType
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.models.m11_join.toJoinSubType
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.JoinIncomingMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class JoinNetworkClientImpl @Inject constructor(
    private val relayWebSocketClient: RelayWebSocketClient
) : IJoinNetworkClient {

    companion object {
        /** Byte de magie préfixant tout payload JOIN forward.
         *  Empêche la collision avec BlockReceived dont le premier byte peut tomber dans 0x01-0x06.
         *  Cohérence avec RelayWebSocketClient.onMessage(FORWARD) early-dispatch. */
        const val JOIN_MAGIC: Byte = 0xFF.toByte()
    }

    private val _incomingJoinRequests = MutableSharedFlow<JoinIncomingMessage>(replay = 0, extraBufferCapacity = 64)
    override val incomingJoinRequests: SharedFlow<JoinIncomingMessage> = _incomingJoinRequests.asSharedFlow()

    init {
        // Forward les messages du relai vers notre SharedFlow
        // L'abonnement est géré par MobicloudP2PService qui collecte relayWebSocketClient.joinIncomingFlow
    }

    /**
     * Reçoit un message entrant depuis le relai (appelé par le collecteur dans le Service).
     * Dispatch vers les abonnés de [incomingJoinRequests].
     */
    suspend fun onRelayMessage(msg: JoinIncomingMessage) {
        _incomingJoinRequests.emit(msg)
    }

    override suspend fun sendJoinRequest(hint: SuperPeerHint, request: JoinRequest): Result<JoinResponse> {
        return runCatching {
            val protobufBytes = ProtoBuf.encodeToByteArray(request)
            val payload = byteArrayOf(JOIN_MAGIC, JoinSubType.JOIN_REQUEST.byte) + protobufBytes
            // blockId synthétique pour traçabilité serveur ; le relai forward sans interpréter.
            val blockId = "JOIN-${UUID.randomUUID().toString().take(16)}"

            val destNodeId = hint.nodeId.toHexString()
            relayWebSocketClient.uploadBlock(destNodeId, blockId, payload).getOrThrow()

            // Attendre la réponse (JOIN_ACCEPT ou JOIN_REDIRECT) avec timeout
            val response = withTimeoutOrNull(5_000L) {
                _incomingJoinRequests
                    .filter { it.fromNodeId == destNodeId && (it.subTypeByte == JoinSubType.JOIN_ACCEPT.byte || it.subTypeByte == JoinSubType.JOIN_REDIRECT.byte) }
                    .first()
            } ?: error("Timeout waiting for JoinResponse from ${destNodeId.take(8)}")

            decodeResponse(response)
        }
    }

    private fun decodeResponse(msg: JoinIncomingMessage): JoinResponse {
        return when (msg.subTypeByte.toJoinSubType()) {
            JoinSubType.JOIN_ACCEPT -> ProtoBuf.decodeFromByteArray<JoinResponse.JoinAccept>(msg.payload)
            JoinSubType.JOIN_REDIRECT -> ProtoBuf.decodeFromByteArray<JoinResponse.JoinRedirect>(msg.payload)
            else -> error("Sous-type inattendu: ${msg.subTypeByte}")
        }
    }

    /** Encode et envoie un JoinResponse (côté Super-Pair) vers un candidat. */
    suspend fun sendJoinResponse(destNodeId: String, response: JoinResponse): Result<Unit> {
        return runCatching {
            val (subType, bytes) = when (response) {
                is JoinResponse.JoinAccept -> JoinSubType.JOIN_ACCEPT to ProtoBuf.encodeToByteArray(response)
                is JoinResponse.JoinRedirect -> JoinSubType.JOIN_REDIRECT to ProtoBuf.encodeToByteArray(response)
            }
            val payload = byteArrayOf(JOIN_MAGIC, subType.byte) + bytes
            val blockId = "JOIN-${UUID.randomUUID().toString().take(16)}"
            relayWebSocketClient.uploadBlock(destNodeId, blockId, payload).getOrThrow()
        }
    }
}
