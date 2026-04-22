package com.mobicloud.data.p2p.tcp

import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.BLOCK_NOT_FOUND
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.BLOCK_REQUEST
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.BLOCK_RESPONSE
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.CONNECT_TIMEOUT_MS
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.MAX_BLOCK_PAYLOAD_BYTES
import com.mobicloud.domain.models.BlockRequestMessage
import com.mobicloud.domain.models.BlockResponseMessage
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.BlockDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 6.2 — client TCP qui télécharge un seul bloc depuis un pair distant.
 *
 * Pattern repris de [BlockTransferClient] (symétrie upload/download). Vérification SHA-256
 * du ciphertext effectuée avant retour (défense en profondeur — détecte corruption disque
 * côté hoster ou tampering man-in-the-middle).
 */
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

            // AC#5 — vérification SHA-256 ciphertext == blockId annoncé == blockId attendu.
            val computed = sha256Hex(resp.ciphertext)
            if (computed != resp.blockId || resp.blockId != location.blockId) {
                return@withContext Result.failure(
                    SecurityException(
                        "Hash mismatch — attendu=${location.blockId.take(16)} reçu=${computed.take(16)}"
                    )
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
