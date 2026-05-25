package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.DepartureNoticeMessage
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrchestrateBlockMigrationUseCaseTest {

    private lateinit var peerRepository: PeerRepository
    private lateinit var dhtRepository: DhtRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var gossipRelayChannel: com.mobicloud.data.p2p.relay.GossipRelayChannel
    private lateinit var gossipSyncUseCase: GossipSyncUseCase
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: OrchestrateBlockMigrationUseCase

    private lateinit var peersFlow: MutableStateFlow<List<Peer>>

    private val selfIdentity = NodeIdentity(
        nodeId = "self".repeat(16),  // 64 chars
        publicKeyBytes = byteArrayOf(0x01)
    )
    private val departingIdentity = NodeIdentity(
        nodeId = "dep1".repeat(16),
        publicKeyBytes = byteArrayOf(0x02)
    )
    private val candidateAIdentity = NodeIdentity(
        nodeId = "cndA".repeat(16),
        publicKeyBytes = byteArrayOf(0x03)
    )
    private val candidateBIdentity = NodeIdentity(
        nodeId = "cndB".repeat(16),
        publicKeyBytes = byteArrayOf(0x04)
    )

    private fun peer(identity: NodeIdentity, ip: String? = "10.0.0.1", port: Int? = 9000,
                     isActive: Boolean = true, isSuperPair: Boolean = false) = Peer(
        identity = identity,
        lastSeenTimestampMs = 0L,
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = ip,
        port = port,
        isActive = isActive,
        isSuperPair = isSuperPair
    )

    @Before
    fun setup() {
        peerRepository = mockk()
        dhtRepository = mockk()
        securityRepository = mockk()
        gossipSyncUseCase = mockk()
        networkEventRepository = mockk()

        peersFlow = MutableStateFlow(emptyList())
        every { peerRepository.peers } returns peersFlow
        every { networkEventRepository.pushEvent(any()) } just Runs

        gossipRelayChannel = mockk(relaxed = true)
        useCase = OrchestrateBlockMigrationUseCase(
            peerRepository, dhtRepository, securityRepository,
            gossipRelayChannel, gossipSyncUseCase, networkEventRepository,
            // Dispatchers.Unconfined : les launch sur applicationScope (gossip post-MAJ DHT)
            // s'exécutent eagerly sur le thread courant — les coVerify post-call les voient.
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    private fun notice(blockIds: List<String> = listOf("a".repeat(64), "b".repeat(64), "c".repeat(64))) =
        DepartureNoticeMessage(
            senderNodeId = departingIdentity.nodeId,
            hostedBlockIds = blockIds,
            signatureBytes = byteArrayOf(0x0A)
        )

    @Test
    fun `test 1 - non super-pair court-circuite`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        // self listé comme NON super-pair
        peersFlow.value = listOf(peer(selfIdentity, isSuperPair = false))

        useCase.onDepartureNoticeReceived(notice())

        coVerify(exactly = 0) { gossipRelayChannel.sendMigrationPlan(any(), any()) }
        coVerify(exactly = 0) { dhtRepository.deleteByNodeId(any()) }
        coVerify(exactly = 0) { dhtRepository.insertEntry(any(), any(), any(), any()) }
        coVerify(exactly = 0) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 2 - signature DEPARTURE_NOTICE invalide`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(departingIdentity)
        )
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)

        useCase.onDepartureNoticeReceived(notice())

        coVerify(exactly = 0) { gossipRelayChannel.sendMigrationPlan(any(), any()) }
        coVerify(exactly = 0) { dhtRepository.deleteByNodeId(any()) }
    }

    @Test
    fun `test 3 - aucun candidat destination`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        // Pas de candidats (seulement self + partant)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(departingIdentity)
        )
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)

        useCase.onDepartureNoticeReceived(notice())

        coVerify(exactly = 0) { gossipRelayChannel.sendMigrationPlan(any(), any()) }
        coVerify(exactly = 0) { dhtRepository.deleteByNodeId(any()) }
    }

    @Test
    fun `test 4 - plan round-robin 3 blocs 2 candidats`() = runTest {
        val blockIds = listOf("a".repeat(64), "b".repeat(64), "c".repeat(64))
        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(departingIdentity, ip = "10.0.0.99", port = 5000),
            peer(candidateAIdentity, ip = "10.0.0.1", port = 6001),
            peer(candidateBIdentity, ip = "10.0.0.2", port = 6002)
        )
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        val planSlot = slot<MigrationPlanMessage>()
        coEvery { gossipRelayChannel.sendMigrationPlan(any(), capture(planSlot)) } returns Result.success(Unit)
        coEvery { dhtRepository.deleteByNodeId(any()) } returns Result.success(Unit)
        coEvery { dhtRepository.insertEntry(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)

        useCase.onDepartureNoticeReceived(notice(blockIds))

        val plan = planSlot.captured
        assertEquals(3, plan.directives.size)
        // Round-robin déterministe : candidates filtrés dans l'ordre de la liste de pairs,
        // donc [candidateA, candidateB] dans cet ordre.
        assertEquals(candidateAIdentity.nodeId, plan.directives[0].destinationNodeId)
        assertEquals(candidateBIdentity.nodeId, plan.directives[1].destinationNodeId)
        assertEquals(candidateAIdentity.nodeId, plan.directives[2].destinationNodeId)
        assertEquals(blockIds[0], plan.directives[0].blockId)
        assertEquals(blockIds[1], plan.directives[1].blockId)
        assertEquals(blockIds[2], plan.directives[2].blockId)
    }

    @Test
    fun `test 5 - DHT mise a jour post-envoi puis gossip`() = runTest {
        val blockIds = listOf("a".repeat(64), "b".repeat(64))
        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(departingIdentity, ip = "10.0.0.99", port = 5000),
            peer(candidateAIdentity, ip = "10.0.0.1", port = 6001)
        )
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        coEvery { gossipRelayChannel.sendMigrationPlan(any(), any()) } returns Result.success(Unit)
        coEvery { dhtRepository.deleteByNodeId(any()) } returns Result.success(Unit)
        coEvery { dhtRepository.insertEntry(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)

        useCase.onDepartureNoticeReceived(notice(blockIds))

        coVerify(exactly = 1) { dhtRepository.deleteByNodeId(departingIdentity.nodeId) }
        coVerify(exactly = 2) {
            dhtRepository.insertEntry(any(), candidateAIdentity.nodeId, "10.0.0.1", 6001)
        }
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 6 - budget NFR02 - timeout envoi du plan logge mais DHT mise a jour`() = runTest {
        val blockIds = listOf("a".repeat(64))
        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(departingIdentity, ip = "10.0.0.99", port = 5000),
            peer(candidateAIdentity, ip = "10.0.0.1", port = 6001)
        )
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        // Bloque > 5s => withTimeoutOrNull doit annuler
        coEvery { gossipRelayChannel.sendMigrationPlan(any(), any()) } coAnswers {
            delay(10_000L)
            Result.success(Unit)
        }
        coEvery { dhtRepository.deleteByNodeId(any()) } returns Result.success(Unit)
        coEvery { dhtRepository.insertEntry(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)

        var completed = false
        val job = backgroundScope.launch {
            useCase.onDepartureNoticeReceived(notice(blockIds))
            completed = true
        }

        // S'assurer que la coroutine a atteint le point de suspension (`delay(10_000)`) avant de vérifier l'état.
        runCurrent()
        advanceTimeBy(4_500L)
        runCurrent()
        assertTrue("ne doit pas être fini avant le budget NFR-02", !completed)
        // Dépasser `NFR02_BUDGET_MS = 5_000L` d'une marge suffisante pour éviter l'ambiguïté tick-boundary.
        advanceTimeBy(1_000L)
        job.join()

        // La MàJ DHT et le gossip doivent TOUJOURS être tentés après timeout
        coVerify(exactly = 1) { dhtRepository.deleteByNodeId(departingIdentity.nodeId) }
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
        coVerify { networkEventRepository.pushEvent(match { it.contains("Timeout") }) }
    }

    @Test
    fun `test 7 - super-pair inactif court-circuite (sub-condition isActive de AC1)`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        // self listé comme super-pair MAIS inactive — la garde AC#1 doit court-circuiter
        peersFlow.value = listOf(peer(selfIdentity, isSuperPair = true, isActive = false))

        useCase.onDepartureNoticeReceived(notice())

        coVerify(exactly = 0) { gossipRelayChannel.sendMigrationPlan(any(), any()) }
        coVerify(exactly = 0) { dhtRepository.deleteByNodeId(any()) }
        coVerify(exactly = 0) { dhtRepository.insertEntry(any(), any(), any(), any()) }
        coVerify(exactly = 0) { gossipSyncUseCase.runGossipCycle() }
    }
}
