package com.mobicloud.data.p2p

import android.util.Log
import com.mobicloud.data.p2p.tcp.BlockDownloadClient
import com.mobicloud.data.p2p.tcp.BlockTransferChannel.MAX_BLOCK_PAYLOAD_BYTES
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.BlockDownloader
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.RelayRepository
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 9.4 — wrapper de téléchargement qui choisit le canal selon la résolution du nodeId :
 *   - nodeId ∈ peerRepository.peers actif (intra-cluster) → TCP direct ([BlockDownloadClient])
 *   - nodeId ∉ peerRepository.peers actif (inter-cluster) → relay-pull ([RelayRepository.requestBlock])
 *
 * La validation post-réponse (sha256, fragmentIndex, iv.size) est strictement identique à
 * `BlockDownloadClient.doTransfer` — un Super-Pair distant peut être compromis, on ne lui fait
 * pas plus confiance qu'à un pair direct (défense en profondeur).
 *
 * Asymétrie volontaire vs `BlockSenderWithRelay` (Story 8.3) : pour la lecture, le direct est
 * privilégié quand un pair est dans peers (= LAN/proche, plus rapide) ; le relais sert
 * uniquement quand le pair est hors-cluster (NAT-traversal nécessaire).
 */
@Singleton
class BlockDownloaderWithRelay @Inject constructor(
    private val direct: BlockDownloadClient,
    private val relay: RelayRepository,
    private val peerRepository: PeerRepository
) : BlockDownloader {

    override suspend fun downloadBlock(
        location: ResolvedBlockLocation,
        timeoutMs: Long
    ): Result<DownloadedBlock> {
        // Décision de canal — critère identique au filtre `activePeers` de LocalizeFileBlocksUseCase
        // pour cohérence cross-couches : un Peer inactif ou sans ip/port ne peut servir une réponse
        // directe, donc on bascule en relay-pull.
        val isIntraCluster = peerRepository.peers.value.any {
            it.identity.nodeId == location.nodeId
                && it.isActive
                && it.ipAddress != null
                && it.port != null
        }

        if (isIntraCluster) {
            Log.d(TAG, "[INTER-CLUSTER][PULL] direct pour ${location.blockId.take(16)} → ${location.nodeId.take(8)}")
            return direct.downloadBlock(location, timeoutMs)
        }

        Log.i(TAG, "[INTER-CLUSTER][PULL] relay-pull pour ${location.blockId.take(16)} → ${location.nodeId.take(8)} (hôte hors-cluster)")
        val startMs = System.currentTimeMillis()
        val msgResult = relay.requestBlock(location.nodeId, location.blockId, timeoutMs)
        val msg = msgResult.getOrElse { return Result.failure(it) }

        // Validation copiée de BlockDownloadClient.doTransfer (AC#4) — défense en profondeur :
        // un Super-Pair distant compromis pourrait renvoyer un bloc valide pour un autre fragmentIndex.
        if (msg.ciphertext.size > MAX_BLOCK_PAYLOAD_BYTES) {
            Log.w(TAG, "[INTER-CLUSTER][PULL] taille payload hors bornes ${msg.ciphertext.size} pour ${location.blockId.take(16)}")
            return Result.failure(IOException(
                "Ciphertext size invalide: ${msg.ciphertext.size} (max=$MAX_BLOCK_PAYLOAD_BYTES)"
            ))
        }
        val computed = sha256Hex(msg.ciphertext)
        if (!computed.equals(msg.blockId, ignoreCase = true)
            || !msg.blockId.equals(location.blockId, ignoreCase = true)
        ) {
            Log.w(TAG, "[INTER-CLUSTER][PULL] hash mismatch ${location.blockId.take(16)} (computed=${computed.take(16)} received=${msg.blockId.take(16)})")
            return Result.failure(SecurityException(
                "Hash mismatch — attendu=${location.blockId.take(16)} reçu=${computed.take(16)}"
            ))
        }
        if (msg.fragmentIndex != location.fragmentIndex) {
            Log.w(TAG, "[INTER-CLUSTER][PULL] fragment mismatch ${location.blockId.take(16)} (attendu=${location.fragmentIndex} reçu=${msg.fragmentIndex})")
            return Result.failure(SecurityException(
                "Fragment mismatch — attendu=${location.fragmentIndex} reçu=${msg.fragmentIndex}"
            ))
        }
        if (msg.iv.size != 12) {
            Log.w(TAG, "[INTER-CLUSTER][PULL] IV invalide ${location.blockId.take(16)} (size=${msg.iv.size})")
            return Result.failure(IOException("IV size invalide: ${msg.iv.size} (attendu 12)"))
        }

        return Result.success(
            DownloadedBlock(
                blockId = msg.blockId,
                fragmentIndex = msg.fragmentIndex,
                isParity = msg.isParity,
                ciphertext = msg.ciphertext,
                iv = msg.iv,
                latencyMs = System.currentTimeMillis() - startMs
            )
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "BlockDownloaderRelay"
    }
}
