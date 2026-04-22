package com.mobicloud.data.p2p.tcp

import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.BLOCK_ACK
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.BLOCK_NACK
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.BLOCK_TRANSFER
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.CONNECT_TIMEOUT_MS
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.MAX_ACK_PAYLOAD_BYTES
import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockTransferClient @Inject constructor(
    private val securityRepository: SecurityRepository
) : BlockSender {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun sendBlock(
        block: BlockTransferMessage,
        peer: Peer,
        timeoutMs: Long
    ): Result<BlockAckMessage> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(peer.ipAddress!!, peer.port!!), CONNECT_TIMEOUT_MS)
            socket.soTimeout = timeoutMs.toInt()

            val out = DataOutputStream(socket.getOutputStream())
            val msgBytes = MobiCloudProtoBuf.encodeToByteArray(BlockTransferMessage.serializer(), block)
            out.writeByte(BLOCK_TRANSFER.toInt())
            out.writeInt(msgBytes.size)
            out.write(msgBytes)
            out.flush()

            val inp = DataInputStream(socket.getInputStream())
            val discriminator = inp.readByte()
            if (discriminator == BLOCK_NACK) {
                // [Review][Patch] Le serveur transmet désormais un code de raison après le discriminant.
                val nackCode = try { inp.readInt() } catch (_: Exception) { BlockTransferChannel.NACK_UNKNOWN }
                val reason = when (nackCode) {
                    BlockTransferChannel.NACK_STORAGE_FULL -> "STORAGE_FULL"
                    BlockTransferChannel.NACK_HASH_MISMATCH -> "HASH_MISMATCH"
                    BlockTransferChannel.NACK_IO_ERROR -> "IO_ERROR"
                    else -> "UNKNOWN"
                }
                return@withContext Result.failure(
                    IOException("Nœud ${peer.identity.nodeId} a rejeté le bloc ${block.blockId} — $reason")
                )
            }
            if (discriminator != BLOCK_ACK) {
                return@withContext Result.failure(
                    IllegalStateException("Discriminateur inattendu: $discriminator")
                )
            }

            val ackLen = inp.readInt()
            if (ackLen <= 0 || ackLen > MAX_ACK_PAYLOAD_BYTES) {
                return@withContext Result.failure(
                    IllegalStateException("Taille ACK invalide: $ackLen")
                )
            }
            val ackBytes = ByteArray(ackLen)
            inp.readFully(ackBytes)
            val ack = MobiCloudProtoBuf.decodeFromByteArray(BlockAckMessage.serializer(), ackBytes)

            // [Review][Patch] Domain separation : le payload signé est
            // "$ACK_DOMAIN_PREFIX|$receiverNodeId|$blockHash".toByteArray(UTF-8)
            val signingPayload = "${com.mobicloud.domain.usecase.m08_hosting.ReceiveAndHostBlockUseCase.ACK_DOMAIN_PREFIX}|${ack.receiverNodeId}|${ack.blockHash}"
                .toByteArray(Charsets.UTF_8)
            val valid = securityRepository.verifySignature(
                data = signingPayload,
                signature = ack.signature,
                publicKey = peer.identity.publicKeyBytes
            ).getOrDefault(false)
            if (!valid) {
                return@withContext Result.failure(
                    SecurityException("Signature ACK invalide pour bloc ${block.blockId}")
                )
            }

            Result.success(ack)
        } catch (e: SocketTimeoutException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
