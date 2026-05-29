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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de propagation de clusterId dans [ProcessIncomingElectionEventUseCase].
 *
 * Story 12.1 : le garde WiFi-cluster a été supprimé — tous les nœuds (WiFi ou 4G)
 * adoptent le clusterId du COORDINATOR via le même chemin.
 *
 * WG3 — Nœud 4G adopte le clusterId du COORDINATOR
 * WG6 — Nœud WiFi adopte aussi le clusterId (pas de rejet, plus de garde WiFi)
 */
class WifiClusterGuardTest {

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

    private val incomingClusterId = "cluster-incoming-1234"

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

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { trustScoreProvider.getTrustScore(any()) } returns 0
        every { networkClient.incomingMessages } returns incomingFlow
        coEvery { localRepairBuffer.drain() } returns emptyList()
        coEvery { networkEventRepository.pushEvent(any()) } returns Unit
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { nodeSettingsRepository.updateClusterId(any()) } returns Unit
        coEvery {
            peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery { signalingRepository.fetchActiveSuperPeers() } returns Result.success(Unit)
    }

    private fun buildUseCase() = ProcessIncomingElectionEventUseCase(
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

    private fun superPeer() = Peer(
        identity = superPairIdentity,
        lastSeenTimestampMs = System.currentTimeMillis(),
        isActive = true,
        isSuperPair = false
    )

    // ── WG3 — nœud 4G adopte le clusterId du COORDINATOR ────────────────────

    @Test
    fun `WG3 - noeud 4G adopte clusterId du COORDINATOR`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(superPeer()))
        every { wifiNetworkRepository.getCurrentSsid() } returns null  // 4G
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 2_000_000_000L,
            clusterId = "some-old-cluster-uuid"
        )

        val useCase = buildUseCase()
        val resultFlow = useCase()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resultFlow.first { it.isSuccess && it.getOrNull() is ElectionEvent.CoordinatorRegistered }
        }
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = superPairNodeId,
                type = ElectionMessageType.COORDINATOR,
                reliabilityScore = 0.9f,
                signatureBytes = ByteArray(0),
                clusterId = incomingClusterId
            )
        )
        job.join()

        coVerify(exactly = 1) { nodeSettingsRepository.updateClusterId(incomingClusterId) }
    }

    // ── WG6 — nœud WiFi adopte aussi le clusterId (garde WiFi supprimé en 12.1) ─

    @Test
    fun `WG6 - noeud WiFi adopte aussi le clusterId du COORDINATOR (pas de rejet)`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(superPeer()))
        every { wifiNetworkRepository.getCurrentSsid() } returns "HomeWifi"
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 2_000_000_000L,
            clusterId = "old-local-cluster-id"
        )

        val useCase = buildUseCase()
        val resultFlow = useCase()

        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            result = resultFlow.first { it.isSuccess && it.getOrNull() is ElectionEvent.CoordinatorRegistered }
        }
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = superPairNodeId,
                type = ElectionMessageType.COORDINATOR,
                reliabilityScore = 0.9f,
                signatureBytes = ByteArray(0),
                clusterId = incomingClusterId
            )
        )
        job.join()

        assertTrue(result?.isSuccess == true)
        assertTrue(result?.getOrNull() is ElectionEvent.CoordinatorRegistered)
        coVerify(exactly = 1) { nodeSettingsRepository.updateClusterId(incomingClusterId) }
    }
}
