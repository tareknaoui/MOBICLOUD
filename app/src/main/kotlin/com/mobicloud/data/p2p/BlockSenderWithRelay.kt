package com.mobicloud.data.p2p

import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.data.p2p.tcp.BlockTransferClient
import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.TransferChannelState
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.RelayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockSenderWithRelay @Inject constructor(
    private val tcpSender: BlockTransferClient,
    private val relayRepository: RelayRepository
) : BlockSender {

    private val _transferChannelState = MutableStateFlow(TransferChannelState.DIRECT)
    val transferChannelState: StateFlow<TransferChannelState> = _transferChannelState.asStateFlow()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun sendBlock(
        block: BlockTransferMessage,
        peer: Peer,
        timeoutMs: Long
    ): Result<BlockAckMessage> {
        // ---- Priorité 1 : TCP direct ----
        val tcpResult = tcpSender.sendBlock(block, peer, timeoutMs)
        if (tcpResult.isSuccess) {
            _transferChannelState.value = TransferChannelState.DIRECT
            return tcpResult
        }

        // N'essayer le relay que pour des erreurs réseau (pas sécurité / NACK hash)
        val tcpCause = tcpResult.exceptionOrNull()
        if (tcpCause != null && tcpCause !is IOException) {
            _transferChannelState.value = TransferChannelState.OFFLINE
            return tcpResult
        }

        // ---- Priorité 2 : Relay HA (failover multi-instance géré par RelayWebSocketClient) ----
        // Sérialiser le BlockTransferMessage complet (opaque pour le relay — Zero-Knowledge).
        val blockPayload = runCatching {
            MobiCloudProtoBuf.encodeToByteArray(BlockTransferMessage.serializer(), block)
        }.getOrElse { serErr ->
            _transferChannelState.value = TransferChannelState.OFFLINE
            return Result.failure(IOException(
                "Relay inaccessible — TCP: ${tcpCause?.message} ; Sérialisation: ${serErr.message}", serErr
            ))
        }

        val relayResult = relayRepository.uploadBlock(
            destNodeId = peer.identity.nodeId,
            blockId = block.blockId,
            data = blockPayload
        )

        return if (relayResult.isSuccess) {
            _transferChannelState.value = TransferChannelState.RELAY_HA
            // ACK synthétique : le relay server confirme la réception (store-and-forward).
            // signature vide = pas de signature pair (relay ACK ≠ pair ACK).
            val syntheticAck = BlockAckMessage(
                blockId = block.blockId,
                blockHash = block.blockId, // blockId = sha256(ciphertext) — identique
                receiverNodeId = peer.identity.nodeId,
                signature = ByteArray(0)
            )
            Result.success(syntheticAck)
        } else {
            _transferChannelState.value = TransferChannelState.OFFLINE
            Result.failure(
                IOException(
                    "Tous les canaux de transfert ont échoué — TCP: ${tcpCause?.message} ; Relay: ${relayResult.exceptionOrNull()?.message}"
                )
            )
        }
    }
}
