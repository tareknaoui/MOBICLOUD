package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionEvent
import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.LocalRepairBuffer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de propagation du clusterId lors de l'élection Bully.
 *
 * AC1 — COORDINATOR payload contient le clusterId de l'émetteur
 * AC2 — Nœud récepteur adopte le clusterId du super-pair élu
 * AC3 — updateClusterId appelé avec le clusterId reçu
 * AC4 — updateClusterId préserve allocatedStorageBytes (testé dans NodeSettingsRepositoryImplTest)
 * AC5 — clusterId blank (legacy) → updateClusterId NON appelé
 * AC6 — Nœud fondateur ne modifie pas son propre clusterId
 */
class ClusterIdPropagationTest {

    // ── Dépendances ProcessIncomingElectionEventUseCase ──────────────────────
    private lateinit var securityRepository: SecurityRepository
    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var peerRepository: PeerRepository
    private lateinit var networkClient: IElectionNetworkClient
    private lateinit var localRepairBuffer: LocalRepairBuffer
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var wifiNetworkRepository: WifiNetworkRepository
    private lateinit var signalingRepository: SignalingRepository

    private val localNodeId = "local-node-AAA"
    private val localIdentity = NodeIdentity(localNodeId, ByteArray(65))
    private val superPairNodeId = "super-pair-BBB"
    private val superPairIdentity = NodeIdentity(superPairNodeId, ByteArray(65))
    private val superPairClusterId = "cluster-BBB-1234"

    private val incomingFlow = MutableSharedFlow<ElectionPayload>()

    @Before
    fun setUp() {
        securityRepository = mockk()
        trustScoreProvider = mockk()
        peerRepository = mockk()
        networkClient = mockk()
        localRepairBuffer = mockk()
        networkEventRepository = mockk()
        nodeSettingsRepository = mockk()
        wifiNetworkRepository = mockk()
        signalingRepository = mockk()
        // Simule un nœud 4G (pas de WiFi) — préserve le comportement d'adoption du clusterId
        every { wifiNetworkRepository.getCurrentSsid() } returns null

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { trustScoreProvider.getTrustScore(any()) } returns 0
        every { networkClient.incomingMessages } returns incomingFlow
        coEvery { localRepairBuffer.drain() } returns emptyList()
        coEvery { networkEventRepository.pushEvent(any()) } returns Unit
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { nodeSettingsRepository.updateClusterId(any()) } returns Unit
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 2_000_000_000L,
            clusterId = "cluster-local-AAA"
        )
        coEvery {
            peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery { signalingRepository.fetchActiveSuperPeers() } returns Result.success(Unit)
    }

    private fun buildProcessUseCase() = ProcessIncomingElectionEventUseCase(
        securityRepository = securityRepository,
        trustScoreProvider = trustScoreProvider,
        peerRepository = peerRepository,
        networkClient = networkClient,
        electionStateManager = ElectionStateManager(),
        localRepairBuffer = localRepairBuffer,
        networkEventRepository = networkEventRepository,
        nodeSettingsRepository = nodeSettingsRepository,
        wifiNetworkRepository = wifiNetworkRepository,
        signalingRepository = signalingRepository
    )

    private fun knownSuperPeer() = Peer(
        identity = superPairIdentity,
        lastSeenTimestampMs = System.currentTimeMillis(),
        isActive = true,
        isSuperPair = false
    )

    private fun coordinatorPayload(clusterId: String = superPairClusterId) = ElectionPayload(
        senderNodeId = superPairNodeId,
        type = ElectionMessageType.COORDINATOR,
        reliabilityScore = 0.9f,
        signatureBytes = ByteArray(0),
        clusterId = clusterId
    )

    // ── AC1 — payload COORDINATOR contient clusterId ──────────────────────────

    @Test
    fun `AC1 - ElectionPayload COORDINATOR contient le clusterId de l emetteur`() {
        val payload = coordinatorPayload(superPairClusterId)
        assertEquals(superPairClusterId, payload.clusterId)
        assertEquals(ElectionMessageType.COORDINATOR, payload.type)
    }

    // ── AC2 + AC3 — récepteur adopte le clusterId du super-pair ──────────────

    @Test
    fun `AC2-AC3 - reception COORDINATOR adopte le clusterId du super-pair`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(knownSuperPeer()))

        val capturedId = slot<String>()
        coEvery { nodeSettingsRepository.updateClusterId(capture(capturedId)) } returns Unit

        val useCase = buildProcessUseCase()
        val resultFlow = useCase()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resultFlow.first { it.isSuccess && it.getOrNull() is ElectionEvent.CoordinatorRegistered }
        }
        incomingFlow.emit(coordinatorPayload(superPairClusterId))
        job.join()

        coVerify(exactly = 1) { nodeSettingsRepository.updateClusterId(superPairClusterId) }
        assertEquals(superPairClusterId, capturedId.captured)
    }

    // ── AC5 — clusterId blank (legacy) → updateClusterId NON appelé ──────────

    @Test
    fun `AC5 - COORDINATOR avec clusterId blank ne modifie pas le clusterId local`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(knownSuperPeer()))

        val useCase = buildProcessUseCase()
        val resultFlow = useCase()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resultFlow.first { it.isSuccess && it.getOrNull() is ElectionEvent.CoordinatorRegistered }
        }
        incomingFlow.emit(coordinatorPayload(clusterId = ""))
        job.join()

        coVerify(exactly = 0) { nodeSettingsRepository.updateClusterId(any()) }
    }

    // ── AC6 — fondateur ne modifie pas son propre clusterId ──────────────────

    @Test
    fun `AC6 - COORDINATOR du noeud local lui-meme ne modifie pas le clusterId`() = runTest {
        val localSuperPeerPayload = ElectionPayload(
            senderNodeId = localNodeId,
            type = ElectionMessageType.COORDINATOR,
            reliabilityScore = 1.0f,
            signatureBytes = ByteArray(0),
            clusterId = "cluster-local-AAA"
        )
        val localPeerInRegistry = Peer(
            identity = localIdentity,
            lastSeenTimestampMs = System.currentTimeMillis(),
            isActive = true,
            isSuperPair = false
        )
        every { peerRepository.peers } returns MutableStateFlow(listOf(localPeerInRegistry))

        val useCase = buildProcessUseCase()
        val resultFlow = useCase()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resultFlow.first { it.isSuccess && it.getOrNull() is ElectionEvent.CoordinatorRegistered }
        }
        incomingFlow.emit(localSuperPeerPayload)
        job.join()

        coVerify(exactly = 0) { nodeSettingsRepository.updateClusterId(any()) }
    }

    // ── AC3 — updateClusterId appelé avec la bonne valeur ────────────────────

    @Test
    fun `AC3 - updateClusterId recoit exactement le clusterId du payload`() = runTest {
        val specificClusterId = "cluster-specific-ZZZZ-9999"
        every { peerRepository.peers } returns MutableStateFlow(listOf(knownSuperPeer()))

        val useCase = buildProcessUseCase()
        val resultFlow = useCase()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resultFlow.first { it.isSuccess && it.getOrNull() is ElectionEvent.CoordinatorRegistered }
        }
        incomingFlow.emit(coordinatorPayload(specificClusterId))
        job.join()

        coVerify(exactly = 1) { nodeSettingsRepository.updateClusterId(specificClusterId) }
    }

    // ── Vérification CoordinatorRegistered émis correctement ─────────────────

    @Test
    fun `COORDINATOR valide emet CoordinatorRegistered apres propagation clusterId`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(knownSuperPeer()))

        val useCase = buildProcessUseCase()
        val resultFlow = useCase()

        var event: ElectionEvent? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            val result = resultFlow.first { it.isSuccess && it.getOrNull() is ElectionEvent.CoordinatorRegistered }
            event = result.getOrNull()
        }
        incomingFlow.emit(coordinatorPayload(superPairClusterId))
        job.join()

        assertTrue(event is ElectionEvent.CoordinatorRegistered)
        assertEquals(superPairNodeId, (event as ElectionEvent.CoordinatorRegistered).coordinatorNodeId)
    }
}
