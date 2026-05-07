package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
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
    private val insertDhtEntryUseCase: InsertDhtEntryUseCase,
    private val requestInterClusterHostingUseCase: RequestInterClusterHostingUseCase,
    private val nodeSettingsRepository: NodeSettingsRepository
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
        params: ErasureParameters,
        originalFileName: String = "",
        onBlockResult: ((blockIndex: Int, success: Boolean) -> Unit)? = null
    ): Result<CatalogEntry> = withContext(Dispatchers.IO) {
        val k = params.k
        android.util.Log.i("MobiCloud:Distribute", "[DIAG] distribute START fileHash=${fileHash.take(8)} fragments=${encryptedBundle.encryptedFragments.size} k=$k n=${params.n}")
        val allPeers = peerRepository.peers.value
        val activePeers = allPeers.filter {
            it.isActive && it.ipAddress != null && it.port != null
        }
        android.util.Log.i("MobiCloud:Distribute", "[DIAG] peers total=${allPeers.size} actifs+IP=${activePeers.size} ${activePeers.map { "${it.identity.nodeId.take(8)}@${it.ipAddress}:${it.port}" }}")
        android.util.Log.i("MobiCloud:Distribute", "[DIAG] détail tous pairs : ${allPeers.map { "${it.identity.nodeId.take(8)}@${it.ipAddress}:${it.port} active=${it.isActive}" }}")
        if (activePeers.isEmpty()) {
            android.util.Log.w("MobiCloud:Distribute", "[DIAG] cluster local vide — tentative directe via fallback inter-cluster (Story 9.3)")
        }

        val localIdentity = securityRepository.getIdentity()
            .getOrElse { return@withContext Result.failure(it) }

        // Lu UNE fois (guardrail 9.3 Subtask 4.2 — éviter une lecture DB par fragment).
        val localClusterId = nodeSettingsRepository.getSettings().clusterId
        if (localClusterId.isBlank()) {
            android.util.Log.w(
                "MobiCloud:Distribute",
                "[INTER-CLUSTER] désactivé : localClusterId blank (cluster pas encore provisionné)"
            )
        }

        val deliveries = mutableListOf<DeliveryRecord>()

        encryptedBundle.encryptedFragments.forEachIndexed { i, frag ->
            val blockId = sha256Hex(frag.ciphertext)

            val msg = BlockTransferMessage(
                blockId = blockId,
                ownerId = localIdentity.nodeId,
                fragmentIndex = frag.index,
                isParity = frag.isParity,
                ciphertext = frag.ciphertext,
                iv = frag.iv,
                originalFileSize = frag.originalFileSize
            )

            var confirmedPeer: Peer? = null
            var result: Result<com.mobicloud.domain.models.BlockAckMessage> =
                Result.failure(IllegalStateException("not attempted"))
            var placedInterCluster = false

            // Niveau 1 — placement local primary (si cluster local non vide).
            if (activePeers.isNotEmpty()) {
                val primaryIndex = i % activePeers.size
                val primaryPeer = activePeers[primaryIndex]
                confirmedPeer = primaryPeer
                android.util.Log.i("MobiCloud:Distribute", "[DIAG] sendBlock #${frag.index} parity=${frag.isParity} → ${primaryPeer.identity.nodeId.take(8)}@${primaryPeer.ipAddress}:${primaryPeer.port}")
                result = blockSender.sendBlock(msg, primaryPeer, BASE_ACK_TIMEOUT_MS)
                android.util.Log.i("MobiCloud:Distribute", "[DIAG] sendBlock #${frag.index} result=${if (result.isSuccess) "OK" else "FAIL: ${result.exceptionOrNull()?.message}"}")

                // Niveau 2 — fallback local (autre pair du cluster).
                if (result.isFailure) {
                    val fallbackIndex = activePeers.indices.firstOrNull { it != primaryIndex }
                    if (fallbackIndex != null) {
                        confirmedPeer = activePeers[fallbackIndex]
                        result = blockSender.sendBlock(msg, confirmedPeer, MAX_ACK_TIMEOUT_MS)
                    }
                }
            }

            // Niveau 3 — fallback inter-cluster (Story 9.3) si local vide ou local échoue.
            if (result.isFailure) {
                val remote = requestInterClusterHostingUseCase.selectRemoteHost(
                    msg.ciphertext.size,
                    localClusterId
                )
                if (remote != null) {
                    val remotePeer = Peer(
                        identity = NodeIdentity(remote.nodeId, ByteArray(0)),
                        lastSeenTimestampMs = System.currentTimeMillis(),
                        source = com.mobicloud.domain.models.DiscoverySource.RELAY_HA,
                        ipAddress = remote.ip,
                        port = remote.port,
                        isActive = true,
                        isSuperPair = true
                    )
                    android.util.Log.i(
                        "MobiCloud:Distribute",
                        "[INTER-CLUSTER] tentative #${frag.index} → ${remote.nodeId.take(8)}@${remote.ip}:${remote.port} cluster=${remote.clusterId.take(8)} freeBytes=${remote.freeBytes}"
                    )
                    result = blockSender.sendBlock(msg, remotePeer, MAX_ACK_TIMEOUT_MS)
                    if (result.isSuccess) {
                        confirmedPeer = remotePeer
                        placedInterCluster = true
                    }
                }
            }

            val success = result.isSuccess
            onBlockResult?.invoke(frag.index, success)
            if (success && confirmedPeer != null) {
                android.util.Log.i(
                    "MobiCloud:Distribute",
                    "[DIAG] fragment #${frag.index} placé ${if (placedInterCluster) "INTER-CLUSTER" else "LOCAL"} sur ${confirmedPeer.identity.nodeId.take(8)}"
                )
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
            originalFileName = originalFileName,
            k = params.k,
            n = params.n
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
