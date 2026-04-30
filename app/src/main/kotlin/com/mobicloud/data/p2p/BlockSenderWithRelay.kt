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
        // ---- Priorité 1 : Relay HA ----
        val blockPayload = runCatching {
            MobiCloudProtoBuf.encodeToByteArray(BlockTransferMessage.serializer(), block)
        }.getOrElse { serErr ->
            _transferChannelState.value = TransferChannelState.OFFLINE
            return Result.failure(IOException("Sérialisation échouée : ${serErr.message}", serErr))
        }

        val relayResult = relayRepository.uploadBlock(
            destNodeId = peer.identity.nodeId,
            blockId = block.blockId,
            data = blockPayload
        )

        if (relayResult.isSuccess) {
            _transferChannelState.value = TransferChannelState.RELAY_HA
            val syntheticAck = BlockAckMessage(
                blockId = block.blockId,
                blockHash = block.blockId,
                receiverNodeId = peer.identity.nodeId,
                signature = ByteArray(0)
            )
            return Result.success(syntheticAck)
        }

        val relayCause = relayResult.exceptionOrNull()

        // ---- Priorité 2 : TCP direct (fallback si relay indisponible) ----
        val tcpResult = tcpSender.sendBlock(block, peer, timeoutMs)
        if (tcpResult.isSuccess) {
            _transferChannelState.value = TransferChannelState.DIRECT
            return tcpResult
        }

        _transferChannelState.value = TransferChannelState.OFFLINE
        return Result.failure(
            IOException(
                "Tous les canaux de transfert ont échoué — Relay: ${relayCause?.message} ; TCP: ${tcpResult.exceptionOrNull()?.message}"
            )
        )
    }
}
