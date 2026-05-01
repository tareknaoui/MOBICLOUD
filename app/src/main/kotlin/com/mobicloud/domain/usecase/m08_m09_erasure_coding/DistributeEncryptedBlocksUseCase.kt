package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.InsertDhtEntryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistributeEncryptedBlocksUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val blockSender: BlockSender,
    private val catalogRepository: CatalogRepository,
    private val gossipSyncUseCase: GossipSyncUseCase,
    private val securityRepository: SecurityRepository,
    private val insertDhtEntryUseCase: InsertDhtEntryUseCase
) {

    private companion object {
        const val BASE_ACK_TIMEOUT_MS = 10_000L
        const val MAX_ACK_TIMEOUT_MS = 30_000L
    }

    private data class DeliveryRecord(
        val frag: EncryptedFragment,
        val blockId: String,
        val nodeId: String,
        val peer: Peer
    )

    suspend fun distribute(
        encryptedBundle: EncryptedBundle,
        fileHash: String,
        k: Int,
        originalFileName: String = "",
        onBlockResult: ((blockIndex: Int, success: Boolean) -> Unit)? = null
    ): Result<CatalogEntry> = withContext(Dispatchers.IO) {
        android.util.Log.i("MobiCloud:Distribute", "[DIAG] distribute START fileHash=${fileHash.take(8)} fragments=${encryptedBundle.encryptedFragments.size} k=$k")
        val allPeers = peerRepository.peers.value
        val activePeers = allPeers.filter {
            it.isActive && it.ipAddress != null && it.port != null
        }
        android.util.Log.i("MobiCloud:Distribute", "[DIAG] peers total=${allPeers.size} actifs+IP=${activePeers.size} ${activePeers.map { "${it.identity.nodeId.take(8)}@${it.ipAddress}:${it.port}" }}")
        android.util.Log.i("MobiCloud:Distribute", "[DIAG] détail tous pairs : ${allPeers.map { "${it.identity.nodeId.take(8)}@${it.ipAddress}:${it.port} active=${it.isActive}" }}")
        if (activePeers.isEmpty()) {
            android.util.Log.w("MobiCloud:Distribute", "[DIAG] ABORT — aucun pair actif avec IP/port")
            return@withContext Result.failure(
                IllegalStateException("Aucun nœud actif disponible pour la distribution")
            )
        }

        val localIdentity = securityRepository.getIdentity()
            .getOrElse { return@withContext Result.failure(it) }

        val deliveries = mutableListOf<DeliveryRecord>()

        encryptedBundle.encryptedFragments.forEachIndexed { i, frag ->
            val blockId = sha256Hex(frag.ciphertext)
            val primaryIndex = i % activePeers.size
            val primaryPeer = activePeers[primaryIndex]

            val msg = BlockTransferMessage(
                blockId = blockId,
                ownerId = localIdentity.nodeId,
                fragmentIndex = frag.index,
                isParity = frag.isParity,
                ciphertext = frag.ciphertext,
                iv = frag.iv,
                originalFileSize = frag.originalFileSize
            )

            var confirmedPeer = primaryPeer
            android.util.Log.i("MobiCloud:Distribute", "[DIAG] sendBlock #${frag.index} parity=${frag.isParity} → ${primaryPeer.identity.nodeId.take(8)}@${primaryPeer.ipAddress}:${primaryPeer.port}")
            var result = blockSender.sendBlock(msg, primaryPeer, BASE_ACK_TIMEOUT_MS)
            android.util.Log.i("MobiCloud:Distribute", "[DIAG] sendBlock #${frag.index} result=${if (result.isSuccess) "OK" else "FAIL: ${result.exceptionOrNull()?.message}"}")

            if (result.isFailure) {
                val usedIndices = mutableSetOf(primaryIndex)
                val fallbackIndex = activePeers.indices.firstOrNull { it !in usedIndices }
                if (fallbackIndex != null) {
                    confirmedPeer = activePeers[fallbackIndex]
                    result = blockSender.sendBlock(msg, confirmedPeer, MAX_ACK_TIMEOUT_MS)
                }
            }

            val success = result.isSuccess
            onBlockResult?.invoke(frag.index, success)
            if (success) {
                deliveries.add(
                    DeliveryRecord(
                        frag = frag,
                        blockId = blockId,
                        nodeId = result.getOrThrow().receiverNodeId,
                        peer = confirmedPeer
                    )
                )
            }
        }

        val dataBlocksConfirmed = deliveries.count { !it.frag.isParity }
        if (dataBlocksConfirmed < k) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Distribution partielle — seulement $dataBlocksConfirmed/$k blocs de données confirmés"
                )
            )
        }

        deliveries.forEach { delivery ->
            val ip = delivery.peer.ipAddress ?: return@forEach
            val port = delivery.peer.port ?: return@forEach
            insertDhtEntryUseCase(delivery.blockId, delivery.nodeId, ip, port)
        }

        val catalogEntry = CatalogEntry(
            fileHash = fileHash,
            ownerPubKeyHash = sha256Hex(localIdentity.publicKeyBytes),
            versionClock = System.currentTimeMillis(),
            fragmentLocations = deliveries.map { delivery ->
                FragmentLocation(
                    fragmentIndex = delivery.frag.index,
                    fragmentHash = delivery.blockId,
                    nodeIds = listOf(delivery.nodeId)
                )
            },
            wrappedMasterKey = encryptedBundle.wrappedFileMasterKey,
            originalFileSize = encryptedBundle.encryptedFragments.firstOrNull()?.originalFileSize ?: 0L,
            originalFileName = originalFileName
        )

        catalogRepository.insertOwnerEntry(catalogEntry)
            .getOrElse { return@withContext Result.failure(it) }

        gossipSyncUseCase.runGossipCycle()

        Result.success(catalogEntry)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
