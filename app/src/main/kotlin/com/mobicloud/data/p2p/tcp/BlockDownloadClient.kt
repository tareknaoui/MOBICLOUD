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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
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
        val startMs = System.currentTimeMillis()
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(location.ipAddress, location.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = timeoutMs.toInt()

            // [Review][Patch] AC#4 — enveloppe wall-clock : `soTimeout` s'applique par read(),
            // un pair "drip" (1 octet/s) contournerait le timeout ACK. `withTimeout` borne
            // le temps total ; à l'expiration, le finally ferme la socket et interrompt l'I/O.
            val socketRef = socket
            return@withContext withTimeout(timeoutMs) { doTransfer(socketRef, location) }
                .map { it.copy(latencyMs = System.currentTimeMillis() - startMs) }
        } catch (e: TimeoutCancellationException) {
            Result.failure(SocketTimeoutException("Timeout download ${location.blockId.take(16)} après ${timeoutMs}ms"))
        } catch (e: SocketTimeoutException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            // [Review][Patch] P1 — payload Protobuf corrompu / tronqué. Sans ce catch, l'exception
            // échappait `doTransfer` et `downloadBlock`, violant le contrat `Result<>` de BlockDownloader.
            Result.failure(e)
        } catch (e: IllegalStateException) {
            // [Review][Patch] P1 — frame invalid (discriminant inconnu, taille hors borne) doit
            // remonter comme échec et non comme throw.
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun doTransfer(socket: Socket, location: ResolvedBlockLocation): Result<DownloadedBlock> {
        try {
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
                return Result.failure(
                    IOException("Bloc ${location.blockId.take(16)} introuvable sur ${location.nodeId.take(8)}")
                )
            }
            if (disc != BLOCK_RESPONSE) {
                return Result.failure(
                    IllegalStateException("Discriminateur inattendu: $disc")
                )
            }
            val len = inp.readInt()
            if (len <= 0 || len > MAX_BLOCK_PAYLOAD_BYTES) {
                return Result.failure(
                    IllegalStateException("Taille BLOCK_RESPONSE invalide: $len")
                )
            }
            val respBytes = ByteArray(len)
            inp.readFully(respBytes)
            val resp = MobiCloudProtoBuf.decodeFromByteArray(BlockResponseMessage.serializer(), respBytes)

            // AC#5 — vérification SHA-256 ciphertext == blockId annoncé == blockId attendu.
            val computed = sha256Hex(resp.ciphertext)
            if (computed != resp.blockId || resp.blockId != location.blockId) {
                return Result.failure(
                    SecurityException(
                        "Hash mismatch — attendu=${location.blockId.take(16)} reçu=${computed.take(16)}"
                    )
                )
            }
            // [Review][Patch] P2 — un pair malicieux pourrait renvoyer un bloc valide (hash OK)
            // mais pour un autre fragmentIndex que celui attendu, corrompant le reassembly.
            if (resp.fragmentIndex != location.fragmentIndex) {
                return Result.failure(
                    SecurityException(
                        "Fragment mismatch — attendu=${location.fragmentIndex} reçu=${resp.fragmentIndex}"
                    )
                )
            }
            // Story 6.3 — IV (12 bytes AES-GCM nonce) requis pour le déchiffrement aval.
            // Un pair non conforme (legacy ou bogué) renvoyant iv.size != 12 → échec immédiat.
            if (resp.iv.size != 12) {
                return Result.failure(
                    IOException("IV size invalide: ${resp.iv.size} (attendu 12)")
                )
            }
            return Result.success(
                DownloadedBlock(
                    blockId = resp.blockId,
                    fragmentIndex = resp.fragmentIndex,
                    isParity = resp.isParity,
                    ciphertext = resp.ciphertext,
                    iv = resp.iv
                )
            )
        } catch (e: SocketTimeoutException) {
            return Result.failure(e)
        } catch (e: IOException) {
            return Result.failure(e)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
