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
import com.mobicloud.data.repository.NodeSettingsRepositoryImpl
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
 * Tests du garde WiFi-cluster dans [ProcessIncomingElectionEventUseCase].
 *
 * WG1 — Nœud WiFi rejette un COORDINATOR d'un cluster différent
 * WG2 — Nœud WiFi accepte un COORDINATOR du même cluster (pas d'updateClusterId)
 * WG3 — Nœud 4G adopte le clusterId du COORDINATOR (comportement hérité)
 * WG4 — ssidToClusterId est déterministe : même SSID → même clusterId
 * WG5 — ssidToClusterId respecte le format UUID v4
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

    private val localNodeId = "local-node-AAA"
    private val localIdentity = NodeIdentity(localNodeId, ByteArray(65))
    private val superPairNodeId = "super-pair-BBB"
    private val superPairIdentity = NodeIdentity(superPairNodeId, ByteArray(65))

    private val localWifiClusterId = NodeSettingsRepositoryImpl.ssidToClusterId("HomeWifi")
    private val otherClusterId = NodeSettingsRepositoryImpl.ssidToClusterId("NeighborWifi")

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
        wifiNetworkRepository = wifiNetworkRepository
    )

    private fun superPeer() = Peer(
        identity = superPairIdentity,
        lastSeenTimestampMs = System.currentTimeMillis(),
        isActive = true,
        isSuperPair = false
    )

    // ── WG1 — nœud WiFi rejette un COORDINATOR d'un autre cluster ─────────────

    @Test
    fun `WG1 - noeud WiFi rejette COORDINATOR d un cluster different`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(superPeer()))
        every { wifiNetworkRepository.getCurrentSsid() } returns "HomeWifi"
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 2_000_000_000L,
            clusterId = localWifiClusterId
        )

        val useCase = buildUseCase()
        val resultFlow = useCase()

        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            result = resultFlow.first()
        }
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = superPairNodeId,
                type = ElectionMessageType.COORDINATOR,
                reliabilityScore = 0.9f,
                signatureBytes = ByteArray(0),
                clusterId = otherClusterId
            )
        )
        job.join()

        assertTrue("Expected failure for cross-cluster COORDINATOR", result?.isFailure == true)
        assertTrue(result?.exceptionOrNull()?.message?.contains("rejected") == true)
        coVerify(exactly = 0) { nodeSettingsRepository.updateClusterId(any()) }
    }

    // ── WG2 — nœud WiFi accepte un COORDINATOR du même cluster ───────────────

    @Test
    fun `WG2 - noeud WiFi accepte COORDINATOR du meme cluster sans updateClusterId`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(superPeer()))
        every { wifiNetworkRepository.getCurrentSsid() } returns "HomeWifi"
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 2_000_000_000L,
            clusterId = localWifiClusterId
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
                clusterId = localWifiClusterId
            )
        )
        job.join()

        assertTrue(result?.isSuccess == true)
        assertTrue(result?.getOrNull() is ElectionEvent.CoordinatorRegistered)
        // WiFi node does not call updateClusterId — its SSID-derived clusterId is authoritative
        coVerify(exactly = 0) { nodeSettingsRepository.updateClusterId(any()) }
    }

    // ── WG3 — nœud 4G adopte le clusterId du COORDINATOR ────────────────────

    @Test
    fun `WG3 - noeud 4G adopte clusterId du COORDINATOR`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(listOf(superPeer()))
        every { wifiNetworkRepository.getCurrentSsid() } returns null  // 4G
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 2_000_000_000L,
            clusterId = "some-random-uuid-1234"
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
                clusterId = localWifiClusterId
            )
        )
        job.join()

        coVerify(exactly = 1) { nodeSettingsRepository.updateClusterId(localWifiClusterId) }
    }

    // ── WG4 — ssidToClusterId est déterministe ────────────────────────────────

    @Test
    fun `WG4 - ssidToClusterId est deterministe - meme SSID produit toujours le meme clusterId`() {
        val id1 = NodeSettingsRepositoryImpl.ssidToClusterId("Livebox-5G-Office")
        val id2 = NodeSettingsRepositoryImpl.ssidToClusterId("Livebox-5G-Office")
        val idDiff = NodeSettingsRepositoryImpl.ssidToClusterId("Livebox-5G-HomeB")

        assertTrue("Même SSID doit produire le même clusterId", id1 == id2)
        assertTrue("SSIDs différents doivent produire des clusterIds différents", id1 != idDiff)
    }

    // ── WG5 — ssidToClusterId respecte le format UUID v4 ─────────────────────

    @Test
    fun `WG5 - ssidToClusterId respecte le format UUID v4`() {
        val clusterId = NodeSettingsRepositoryImpl.ssidToClusterId("TestNetwork")
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        assertTrue(
            "Expected UUID v4 format but got: $clusterId",
            uuidRegex.matches(clusterId)
        )
    }
}
