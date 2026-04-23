package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.data.p2p.tcp.TcpConnectionManager
import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.RepairRequest
import com.mobicloud.domain.models.ReplicationPlanMessage
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriggerAutoRepairUseCaseTest {

    private lateinit var peerRepository: PeerRepository
    private lateinit var dhtRepository: DhtRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var tcpConnectionManager: TcpConnectionManager
    private lateinit var gossipSyncUseCase: GossipSyncUseCase
    private lateinit var circuitBreakerUseCase: CircuitBreakerUseCase
    private lateinit var localRepairBuffer: LocalRepairBuffer
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: TriggerAutoRepairUseCase

    private lateinit var peersFlow: MutableStateFlow<List<Peer>>
    private lateinit var circuitOpenFlow: MutableStateFlow<Boolean>

    private val selfIdentity = NodeIdentity(
        nodeId = "self".repeat(16),
        publicKeyBytes = byteArrayOf(0x01)
    )
    private val donorIdentity = NodeIdentity(
        nodeId = "dnor".repeat(16),
        publicKeyBytes = byteArrayOf(0x02)
    )
    private val inactiveIdentity = NodeIdentity(
        nodeId = "inac".repeat(16),
        publicKeyBytes = byteArrayOf(0x03)
    )
    private val destIdentity = NodeIdentity(
        nodeId = "dest".repeat(16),
        publicKeyBytes = byteArrayOf(0x04)
    )

    private fun peer(
        identity: NodeIdentity,
        ip: String? = "10.0.0.1",
        port: Int? = 9000,
        isActive: Boolean = true,
        isSuperPair: Boolean = false
    ) = Peer(
        identity = identity,
        lastSeenTimestampMs = 0L,
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = ip,
        port = port,
        isActive = isActive,
        isSuperPair = isSuperPair
    )

    private fun entry(blockId: String, nodeId: String, ip: String = "10.0.0.99", port: Int = 5000) =
        DhtEntry(blockId, nodeId, ip, port, timestamp = 1L)

    @Before
    fun setup() {
        peerRepository = mockk()
        dhtRepository = mockk()
        securityRepository = mockk()
        tcpConnectionManager = mockk()
        gossipSyncUseCase = mockk()
        circuitBreakerUseCase = mockk()
        localRepairBuffer = mockk()
        networkEventRepository = mockk()

        peersFlow = MutableStateFlow(emptyList())
        circuitOpenFlow = MutableStateFlow(false)
        every { peerRepository.peers } returns peersFlow
        every { circuitBreakerUseCase.isCircuitOpen } returns circuitOpenFlow
        every { networkEventRepository.pushEvent(any()) } just Runs

        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        coEvery { dhtRepository.insertEntry(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { dhtRepository.deleteByNodeId(any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)
        coEvery { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) } returns Result.success(Unit)
        coEvery { localRepairBuffer.enqueue(any()) } returns null

        useCase = TriggerAutoRepairUseCase(
            peerRepository, dhtRepository, securityRepository,
            tcpConnectionManager, gossipSyncUseCase, circuitBreakerUseCase,
            localRepairBuffer, networkEventRepository
        )
    }

    @Test
    fun `test 1 - non super-pair court-circuite sans effet`() = runTest {
        peersFlow.value = listOf(peer(selfIdentity, isSuperPair = false))

        val result = useCase.scanAndRepair()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 0) { dhtRepository.insertEntry(any(), any(), any(), any()) }
        coVerify(exactly = 0) { localRepairBuffer.enqueue(any()) }
        coVerify(exactly = 0) { gossipSyncUseCase.runGossipCycle() }
        coVerify(exactly = 0) { dhtRepository.findByNodeId(any()) }
    }

    @Test
    fun `test 2 - aucun pair INACTIVE - pas de scan DHT ni gossip`() = runTest {
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity)
        )

        val result = useCase.scanAndRepair()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { dhtRepository.findByNodeId(any()) }
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 0) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 3 - bloc PERDU sans hote actif - log PERDU aucun plan emis`() = runTest {
        val blockA = "a".repeat(64)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId))

        val result = useCase.scanAndRepair()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 0) { localRepairBuffer.enqueue(any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("PERDU") }) }
        // La purge DHT et le gossip unique doivent tout de même être effectués
        coVerify(exactly = 1) { dhtRepository.deleteByNodeId(inactiveIdentity.nodeId) }
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 4 - bloc avec donneur actif suffisant - skip sans plan ni enqueue (MVP seuil=1)`() = runTest {
        val blockA = "a".repeat(64)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(destIdentity, ip = "10.0.0.3", port = 6003),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId, donorIdentity.nodeId))

        val result = useCase.scanAndRepair()

        assertTrue(result.isSuccess)
        // MVP threshold=1 et 1 hôte actif => bloc considéré comme OK => skip
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 0) { localRepairBuffer.enqueue(any()) }
        coVerify(exactly = 0) { securityRepository.signData(any()) }
        // Mais la purge et le gossip ont lieu
        coVerify(exactly = 1) { dhtRepository.deleteByNodeId(inactiveIdentity.nodeId) }
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 5 - circuit OPEN - log explicite et aucun envoi TCP`() = runTest {
        val blockA = "a".repeat(64)
        circuitOpenFlow.value = true
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId))

        val result = useCase.scanAndRepair()

        assertTrue(result.isSuccess)
        coVerify { networkEventRepository.pushEvent(match { it.contains("Circuit-Breaker OPEN") }) }
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
    }

    @Test
    fun `test 6 - purge DHT 1x par noeud INACTIVE et gossip 1x total multi-blocs`() = runTest {
        val blockA = "a".repeat(64)
        val blockB = "b".repeat(64)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId), entry(blockB, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(any()) } returns
            Result.success(listOf(inactiveIdentity.nodeId))

        useCase.scanAndRepair()

        coVerify(exactly = 1) { dhtRepository.deleteByNodeId(inactiveIdentity.nodeId) }
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 7 - identite indisponible - scan silencieux sans exception`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.failure(IllegalStateException("no identity"))
        peersFlow.value = listOf(peer(selfIdentity, isSuperPair = true))

        val result = useCase.scanAndRepair()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 0) { dhtRepository.findByNodeId(any()) }
    }

    @Test
    fun `test 8 - sendReplicationPlan jamais appele avec threshold MVP (inaccessibilite documentee)`() = runTest {
        // Scénario : inactive hébergeait le bloc, un donneur actif l'héberge aussi.
        // Avec UNDER_REPLICATION_THRESHOLD=1, activeHosts.size=1 >= 1 => skip.
        // Avec activeHosts.isEmpty() => log PERDU => skip.
        // Donc la branche sendReplicationPlan n'est atteinte QUE si threshold >= 2 (futur multi-replica).
        val planSlot = slot<ReplicationPlanMessage>()
        coEvery {
            tcpConnectionManager.sendReplicationPlan(capture(planSlot), any(), any())
        } returns Result.success(Unit)

        val blockA = "a".repeat(64)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(destIdentity, ip = "10.0.0.3", port = 6003),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId, donorIdentity.nodeId))

        useCase.scanAndRepair()

        assertTrue(
            "sendReplicationPlan ne doit pas être capturé avec threshold=1 MVP",
            !planSlot.isCaptured
        )
        assertEquals(1, TriggerAutoRepairUseCase.UNDER_REPLICATION_THRESHOLD)
    }

    @Test
    fun `test 9 - aucun pair et aucune entree orphelin - noop global sans gossip`() = runTest {
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(emptyList())

        val result = useCase.scanAndRepair()

        assertTrue(result.isSuccess)
        // Aucune entrée orpheline => aucune mutation DHT => aucun gossip (évite tempête sur scans no-op).
        coVerify(exactly = 0) { dhtRepository.findHostNodeIdsByBlockId(any()) }
        coVerify(exactly = 0) { dhtRepository.deleteByNodeId(any()) }
        coVerify(exactly = 0) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 10 - threshold=2 happy path - sendReplicationPlan et insertEntry + gossip`() = runTest {
        // Override threshold pour forcer la branche sendReplicationPlan (inaccessible avec MVP=1).
        useCase.threshold = 2

        val blockA = "a".repeat(64)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(destIdentity, ip = "10.0.0.3", port = 6003),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        // donor est hôte → activeHosts=[donor], size=1 < 2 → branche send s'active.
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId, donorIdentity.nodeId))

        val planSlot = slot<ReplicationPlanMessage>()
        coEvery {
            tcpConnectionManager.sendReplicationPlan(capture(planSlot), "10.0.0.2", 6002)
        } returns Result.success(Unit)

        useCase.scanAndRepair()

        // Plan émis vers le donneur, structure directive conforme.
        coVerify(exactly = 1) { tcpConnectionManager.sendReplicationPlan(any(), "10.0.0.2", 6002) }
        assertTrue(planSlot.isCaptured)
        val plan = planSlot.captured
        assertEquals(selfIdentity.nodeId, plan.superPeerNodeId)
        assertEquals(blockA, plan.directive.blockId)
        assertEquals(destIdentity.nodeId, plan.directive.destinationNodeId)
        assertEquals("10.0.0.3", plan.directive.destinationIp)
        assertEquals(6003, plan.directive.destinationPort)
        assertTrue("Signature non vide", plan.signatureBytes.isNotEmpty())

        // DHT insertEntry pour la destination + purge du pair INACTIVE + gossip 1×.
        coVerify(exactly = 1) { dhtRepository.insertEntry(blockA, destIdentity.nodeId, "10.0.0.3", 6003) }
        coVerify(exactly = 1) { dhtRepository.deleteByNodeId(inactiveIdentity.nodeId) }
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
        coVerify(exactly = 0) { localRepairBuffer.enqueue(any()) }
    }

    @Test
    fun `test 11 - threshold=2 circuit OPEN - enqueue + insertEntry mais pas de send ni purge`() = runTest {
        useCase.threshold = 2
        circuitOpenFlow.value = true

        val blockA = "a".repeat(64)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(destIdentity, ip = "10.0.0.3", port = 6003),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId, donorIdentity.nodeId))

        val reqSlot = slot<RepairRequest>()
        coEvery { localRepairBuffer.enqueue(capture(reqSlot)) } returns null

        useCase.scanAndRepair()

        // AC#3 — directive enfilée, aucun envoi TCP.
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 1) { localRepairBuffer.enqueue(any()) }
        assertTrue(reqSlot.isCaptured)
        assertEquals(blockA, reqSlot.captured.blockId)
        assertEquals("10.0.0.3", reqSlot.captured.destinationIp)
        assertEquals(6003, reqSlot.captured.port)

        // AC#4 — insertEntry sur branche enqueue (DHT reflète l'intention de réparation).
        coVerify(exactly = 1) { dhtRepository.insertEntry(blockA, destIdentity.nodeId, "10.0.0.3", 6003) }
        // D2:B — purge SKIPPED quand circuit open (préserve info locator pendant churn).
        coVerify(exactly = 0) { dhtRepository.deleteByNodeId(any()) }
        // Mutation via insertEntry → gossip 1×.
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
    }

    @Test
    fun `test 12 - aucune destination libre - log et aucun plan emis`() = runTest {
        useCase.threshold = 2

        val blockA = "a".repeat(64)
        // Seul self + donor actifs ; destIdentity absent → pas de candidat destination
        // (la destination doit être non-hôte, non-self, non-donneur).
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId, donorIdentity.nodeId))

        useCase.scanAndRepair()

        coVerify { networkEventRepository.pushEvent(match { it.contains("aucune destination libre") }) }
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 0) { localRepairBuffer.enqueue(any()) }
        coVerify(exactly = 0) { dhtRepository.insertEntry(any(), any(), any(), any()) }
    }

    @Test
    fun `test 13 - threshold=2 send failure - insertEntry NON appele (retry scan suivant)`() = runTest {
        useCase.threshold = 2

        val blockA = "a".repeat(64)
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(destIdentity, ip = "10.0.0.3", port = 6003),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(inactiveIdentity.nodeId, donorIdentity.nodeId))
        coEvery { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) } returns
            Result.failure(RuntimeException("Connection refused"))

        useCase.scanAndRepair()

        // Send échoué → pas de pollution DHT par une fausse confirmation.
        // Constraint #11 : le prochain scan détectera que le bloc est toujours sous-répliqué.
        coVerify(exactly = 1) { tcpConnectionManager.sendReplicationPlan(any(), any(), any()) }
        coVerify(exactly = 0) { dhtRepository.insertEntry(any(), any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("Envoi plan") && it.contains("échoué") }) }
    }

    @Test
    fun `test 14 - donneur filtre contre self - pas d'auto-envoi si SP co-heberge`() = runTest {
        // Threshold=3 pour forcer proceed malgré 2 hôtes actifs (self + donor).
        useCase.threshold = 3

        val blockA = "a".repeat(64)
        // Self est co-hôte du bloc ET Super-Pair. Sans le filtre, activeHosts.first() == self.
        peersFlow.value = listOf(
            peer(selfIdentity, isSuperPair = true, ip = "10.0.0.1", port = 6001),
            peer(donorIdentity, ip = "10.0.0.2", port = 6002),
            peer(destIdentity, ip = "10.0.0.3", port = 6003),
            peer(inactiveIdentity, isActive = false)
        )
        coEvery { dhtRepository.findByNodeId(inactiveIdentity.nodeId) } returns
            Result.success(listOf(entry(blockA, inactiveIdentity.nodeId)))
        // hostNodeIds ordre : self d'abord, puis donor. Sans filtre, donor = self.
        // activeHosts = [self, donor] (size=2 < threshold=3 → proceed).
        coEvery { dhtRepository.findHostNodeIdsByBlockId(blockA) } returns
            Result.success(listOf(selfIdentity.nodeId, donorIdentity.nodeId, inactiveIdentity.nodeId))

        coEvery {
            tcpConnectionManager.sendReplicationPlan(any(), any(), any())
        } returns Result.success(Unit)

        useCase.scanAndRepair()

        // Le donneur choisi doit être donorIdentity (le self est exclu par filtre firstOrNull).
        coVerify(exactly = 1) { tcpConnectionManager.sendReplicationPlan(any(), "10.0.0.2", 6002) }
        coVerify(exactly = 0) { tcpConnectionManager.sendReplicationPlan(any(), "10.0.0.1", 6001) }
    }
}
