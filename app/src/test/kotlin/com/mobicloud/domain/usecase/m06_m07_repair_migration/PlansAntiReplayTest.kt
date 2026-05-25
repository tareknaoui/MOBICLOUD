package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.DepartureNoticeMessage
import com.mobicloud.domain.models.MigrateBlockDirective
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.PLAN_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.ReplicationPlanMessage
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests adversariaux du fix anti-replay sur les 3 messages signes :
 *  R1 — DEPARTURE_NOTICE rejoue (vieux timestamp) -> rejet, pas de plan emis
 *  R2 — MIGRATION_PLAN rejoue -> rejet, pas de transfert declenche
 *  R3 — REPLICATION_PLAN rejoue -> rejet, pas de transfert declenche
 *
 * Le control positif (timestamp frais) est implicitement teste par les suites
 * existantes du module (qui passaient avant le fix et passent toujours apres).
 */
class PlansAntiReplayTest {

    private val superPeerId = "super-peer-AAA"
    private val superPeerIdentity = NodeIdentity(superPeerId, ByteArray(65))
    private val localId = "local-node-BBB"
    private val localIdentity = NodeIdentity(localId, ByteArray(65))

    private fun activeSuperPeer() = Peer(
        identity = superPeerIdentity,
        lastSeenTimestampMs = System.currentTimeMillis(),
        isActive = true,
        isSuperPair = true,
        ipAddress = "10.0.0.1",
        port = 9000
    )

    // ── R1 — DEPARTURE_NOTICE replay ────────────────────────────────────────

    @Test
    fun `R1 - DEPARTURE_NOTICE avec timestamp ancien est rejete`() = runTest {
        val peerRepository = mockk<PeerRepository>()
        val securityRepository = mockk<SecurityRepository>()
        val gossipRelayChannel = mockk<com.mobicloud.data.p2p.relay.GossipRelayChannel>(relaxed = true)
        val dhtRepository = mockk<com.mobicloud.domain.repository.DhtRepository>(relaxed = true)
        val gossipSyncUseCase = mockk<com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase>(relaxed = true)
        val networkEventRepository = mockk<NetworkEventRepository>(relaxed = true)

        // Self est super-peer + sender connu
        val selfPeer = Peer(
            identity = localIdentity,
            lastSeenTimestampMs = System.currentTimeMillis(),
            isActive = true,
            isSuperPair = true,
            ipAddress = "10.0.0.2",
            port = 9001
        )
        val departingPeer = Peer(
            identity = superPeerIdentity, // n'importe quel peer
            lastSeenTimestampMs = System.currentTimeMillis(),
            isActive = true,
            isSuperPair = false,
            ipAddress = "10.0.0.3",
            port = 9003
        )
        every { peerRepository.peers } returns MutableStateFlow(listOf(selfPeer, departingPeer))
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)

        val orchestrator = OrchestrateBlockMigrationUseCase(
            peerRepository, dhtRepository, securityRepository,
            gossipRelayChannel, gossipSyncUseCase, networkEventRepository,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        // ATTAQUE : NOTICE rejoue (timestamp 5 min dans le passe = >> 30s window)
        val ancientTimestamp = System.currentTimeMillis() - 5 * 60_000L
        val replayedNotice = DepartureNoticeMessage(
            senderNodeId = superPeerId,
            hostedBlockIds = listOf("a".repeat(64)),
            signatureBytes = ByteArray(64),
            timestampMs = ancientTimestamp
        )

        orchestrator.onDepartureNoticeReceived(replayedNotice)

        // Aucun plan envoye, aucune signature verifiee, message d'erreur "hors fenetre"
        coVerify(exactly = 0) {
            gossipRelayChannel.sendMigrationPlan(any(), any())
        }
        // L'event reseau doit signaler le replay
        val capturedEvent = slot<String>()
        coVerify { networkEventRepository.pushEvent(capture(capturedEvent)) }
        assertTrue(
            "Event doit mentionner hors fenetre (replay)",
            capturedEvent.captured.contains("hors fenetre", ignoreCase = true) ||
            capturedEvent.captured.contains("replay", ignoreCase = true)
        )
    }

    // ── R2 — MIGRATION_PLAN replay ──────────────────────────────────────────

    @Test
    fun `R2 - MIGRATION_PLAN avec timestamp ancien est rejete`() = runTest {
        val hostedBlockRepository = mockk<HostedBlockRepository>(relaxed = true)
        val peerRepository = mockk<PeerRepository>()
        val securityRepository = mockk<SecurityRepository>()
        val blockSender = mockk<BlockSender>(relaxed = true)
        val networkEventRepository = mockk<NetworkEventRepository>(relaxed = true)

        every { peerRepository.peers } returns MutableStateFlow(listOf(activeSuperPeer()))
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)

        val executor = ExecuteMigrationPlanUseCase(
            hostedBlockRepository, peerRepository, securityRepository,
            blockSender, networkEventRepository
        )

        val ancientTimestamp = System.currentTimeMillis() - 5 * 60_000L
        val replayedPlan = MigrationPlanMessage(
            superPeerNodeId = superPeerId,
            directives = listOf(
                MigrateBlockDirective(
                    blockId = "a".repeat(64),
                    destinationNodeId = "dest-XX",
                    destinationIp = "10.0.0.99",
                    destinationPort = 9999,
                    destinationPublicKeyBytes = ByteArray(65)
                )
            ),
            signatureBytes = ByteArray(64),
            timestampMs = ancientTimestamp
        )

        executor.onMigrationPlanReceived(replayedPlan)

        // Pas de transfert declenche (sendBlock jamais appele)
        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }

        val capturedEvent = slot<String>()
        coVerify { networkEventRepository.pushEvent(capture(capturedEvent)) }
        assertTrue(
            "Event doit mentionner hors fenetre",
            capturedEvent.captured.contains("hors fenetre", ignoreCase = true)
        )
    }

    // ── R3 — REPLICATION_PLAN replay ────────────────────────────────────────

    @Test
    fun `R3 - REPLICATION_PLAN avec timestamp ancien est rejete`() = runTest {
        val hostedBlockRepository = mockk<HostedBlockRepository>(relaxed = true)
        val peerRepository = mockk<PeerRepository>()
        val securityRepository = mockk<SecurityRepository>()
        val blockSender = mockk<BlockSender>(relaxed = true)
        val networkEventRepository = mockk<NetworkEventRepository>(relaxed = true)

        every { peerRepository.peers } returns MutableStateFlow(listOf(activeSuperPeer()))
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)

        val executor = ExecuteReplicationPlanUseCase(
            hostedBlockRepository, peerRepository, securityRepository,
            blockSender, networkEventRepository
        )

        val ancientTimestamp = System.currentTimeMillis() - 5 * 60_000L
        val replayedPlan = ReplicationPlanMessage(
            superPeerNodeId = superPeerId,
            directive = MigrateBlockDirective(
                blockId = "a".repeat(64),
                destinationNodeId = "dest-XX",
                destinationIp = "10.0.0.99",
                destinationPort = 9999,
                destinationPublicKeyBytes = ByteArray(65)
            ),
            signatureBytes = ByteArray(64),
            timestampMs = ancientTimestamp
        )

        executor.onReplicationPlanReceived(replayedPlan)

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }

        val capturedEvent = slot<String>()
        coVerify { networkEventRepository.pushEvent(capture(capturedEvent)) }
        assertTrue(
            "Event doit mentionner hors fenetre",
            capturedEvent.captured.contains("hors fenetre", ignoreCase = true)
        )
    }

    // ── Sanity : la fenetre est bien definie ────────────────────────────────

    @Test
    fun `PLAN_TIMESTAMP_WINDOW_MS est non zero`() {
        assertTrue("Fenetre doit etre > 0", PLAN_TIMESTAMP_WINDOW_MS > 0)
    }
}
