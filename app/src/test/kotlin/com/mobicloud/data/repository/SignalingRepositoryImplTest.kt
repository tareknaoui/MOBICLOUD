package com.mobicloud.data.repository

import android.os.SystemClock
import android.util.Log
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignalingRepositoryImplTest {

    private lateinit var relayClient: RelayWebSocketClient
    private lateinit var peerRepository: PeerRepository
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var identityRepository: IdentityRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var hostedBlockRepository: HostedBlockRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L

        relayClient = mockk(relaxed = true)
        peerRepository = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        identityRepository = mockk(relaxed = true)
        nodeSettingsRepository = mockk(relaxed = true)
        hostedBlockRepository = mockk(relaxed = true)

        // Stub explicite : Result<NodeIdentity> est une inline class, mockk relaxed renvoie un Object
        // qui ne peut pas être cast → ClassCastException dès qu'on accède à .nodeId.
        coEvery { identityRepository.getIdentity() } returns Result.success(NodeIdentity("self-node-id", ByteArray(0)))
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 1_000_000L,
            clusterId = "test-cluster-id-0001"
        )
        coEvery { hostedBlockRepository.getTotalHostedBytes() } returns 0L

        every { relayClient.connect(any()) } returns emptyFlow()
    }

    private fun buildRepo() = SignalingRepositoryImpl(
        relayClient, peerRepository, networkEventRepository,
        identityRepository, nodeSettingsRepository, hostedBlockRepository
    )

    // -------------------------------------------------------------------------
    // Subtask 4.1 : registerAsSuperPeer retourne Result.success
    // -------------------------------------------------------------------------

    @Test
    fun `registerAsSuperPeer retourne Result_success quand sendRegisterPeer retourne true`() = runTest {
        every { relayClient.sendRegisterPeer(any(), any(), any(), any(), any(), any(), any()) } returns true

        val repo = buildRepo()
        val result = repo.registerAsSuperPeer("192.168.1.10", 48999, 0.87f, System.currentTimeMillis(), "test-node-id")

        assertTrue(result.isSuccess)
        verify { relayClient.sendRegisterPeer(any(), "192.168.1.10", 48999, 0.87f, any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Subtask 4.2 : registerAsSuperPeer retourne Result.failure
    // -------------------------------------------------------------------------

    @Test
    fun `registerAsSuperPeer retourne Result_failure quand sendRegisterPeer retourne false`() = runTest {
        every { relayClient.sendRegisterPeer(any(), any(), any(), any(), any(), any(), any()) } returns false

        val repo = buildRepo()
        val result = repo.registerAsSuperPeer("192.168.1.10", 48999, 0.87f, System.currentTimeMillis(), "test-node-id")

        assertTrue(result.isFailure)
        verify { networkEventRepository.pushEvent(match { it.contains("enregistrement Super-Pair échoué") }) }
    }

    // -------------------------------------------------------------------------
    // Story 9.2 — registerAsSuperPeer calcule et envoie freeBytes
    // -------------------------------------------------------------------------

    @Test
    fun `Story 9_2 registerAsSuperPeer envoie freeBytes = allocated - used`() = runTest {
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 1_000_000L,
            clusterId = "test-cluster-id-0001"
        )
        coEvery { hostedBlockRepository.getTotalHostedBytes() } returns 250_000L
        every { relayClient.sendRegisterPeer(any(), any(), any(), any(), any(), any(), any()) } returns true

        val repo = buildRepo()
        repo.registerAsSuperPeer("192.168.1.10", 48999, 0.87f, 1000L, "test-node-id")

        verify {
            relayClient.sendRegisterPeer(
                "test-node-id", "192.168.1.10", 48999, 0.87f, 1000L,
                "test-cluster-id-0001",
                750_000L  // 1_000_000 - 250_000
            )
        }
    }

    @Test
    fun `Story 9_2 registerAsSuperPeer clampe freeBytes a 0 quand used superieur a allocated`() = runTest {
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 100_000L,
            clusterId = "test-cluster-id-0001"
        )
        coEvery { hostedBlockRepository.getTotalHostedBytes() } returns 250_000L  // > allocated
        every { relayClient.sendRegisterPeer(any(), any(), any(), any(), any(), any(), any()) } returns true

        val repo = buildRepo()
        repo.registerAsSuperPeer("192.168.1.10", 48999, 0.87f, 1000L, "test-node-id")

        verify {
            relayClient.sendRegisterPeer(
                any(), any(), any(), any(), any(), any(),
                0L  // jamais négatif
            )
        }
    }

    // -------------------------------------------------------------------------
    // Subtask 4.3a : fetchActiveSuperPeers envoie GET_PEERS avec succès
    // -------------------------------------------------------------------------

    @Test
    fun `fetchActiveSuperPeers retourne Result_success quand sendGetPeers retourne true`() = runTest {
        every { relayClient.sendGetPeers() } returns true

        val repo = buildRepo()
        val result = repo.fetchActiveSuperPeers()

        assertTrue(result.isSuccess)
        verify { relayClient.sendGetPeers() }
    }

    // -------------------------------------------------------------------------
    // Subtask 4.3b : fetchActiveSuperPeers — comportement selon l'historique de connexion
    // -------------------------------------------------------------------------

    @Test
    fun `fetchActiveSuperPeers ne loggue pas erreur si jamais connecte au relay`() = runTest {
        // emptyFlow() → everConnected reste false → pas de pushEvent "injoignables"
        every { relayClient.sendGetPeers() } returns false

        val repo = buildRepo()
        val result = repo.fetchActiveSuperPeers()

        assertTrue(result.isFailure)
        verify(exactly = 0) { networkEventRepository.pushEvent("Signalisation HA : tous les serveurs injoignables") }
    }

    @Test
    fun `fetchActiveSuperPeers loggue erreur si connexion etait etablie puis perdue`() = runTest {
        every { relayClient.sendGetPeers() } returns false

        val repo = buildRepo()
        repo.everConnected = true // simule : relay était connecté, maintenant perdu

        val result = repo.fetchActiveSuperPeers()

        assertTrue(result.isFailure)
        verify { networkEventRepository.pushEvent("Signalisation HA : tous les serveurs injoignables") }
    }

    // -------------------------------------------------------------------------
    // Subtask 4.4 : processPeerList filtre les entrées TTL > 60s
    // -------------------------------------------------------------------------

    @Test
    fun `processPeerList filtre les entrees TTL superieur a 60s`() = runTest {
        val repo = buildRepo()

        val stalePeer = RelayPeer(
            nodeId = "stale-node",
            ip = "1.2.3.4",
            port = 9000,
            reliabilityScore = 0.5f,
            lastSeen = 1_000L // epoch + 1s, toujours > 60s en arrière
        )

        repo.processPeerList(listOf(stalePeer))

        coVerify(exactly = 0) { peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Subtask 4.5 : processPeerList insère les peers valides avec source RELAY_HA
    // -------------------------------------------------------------------------

    @Test
    fun `processPeerList insere les peers valides avec source RELAY_HA`() = runTest {
        val repo = buildRepo()
        val now = System.currentTimeMillis()

        val freshPeer = RelayPeer(
            nodeId = "fresh-node",
            ip = "5.6.7.8",
            port = 8888,
            reliabilityScore = 0.9f,
            lastSeen = now,
            isSuperPair = true   // Story Bully — le serveur signale ici un Super-Pair élu
        )

        repo.processPeerList(listOf(freshPeer))

        coVerify {
            peerRepository.registerOrUpdatePeer(
                identity    = match { it.nodeId == "fresh-node" && it.publicKeyBytes.isEmpty() },
                timestampMs = any(),
                source      = DiscoverySource.RELAY_HA,
                ipAddress   = "5.6.7.8",
                port        = 8888,
                isSuperPair = true
            )
        }
    }

    // -------------------------------------------------------------------------
    // Story Bully : le flag isSuperPair du serveur est respecté (un participant JOIN
    // n'est PAS marqué comme Super-Pair localement).
    // -------------------------------------------------------------------------

    @Test
    fun `processPeerList insere un participant JOIN avec isSuperPair=false`() = runTest {
        val repo = buildRepo()
        val now = System.currentTimeMillis()

        val joinPeer = RelayPeer(
            nodeId = "joined-node",
            ip = "9.10.11.12",
            port = 7777,
            reliabilityScore = 0.6f,
            lastSeen = now,
            isSuperPair = false   // simple participant côté serveur (JOIN, pas REGISTER_PEER)
        )

        repo.processPeerList(listOf(joinPeer))

        coVerify {
            peerRepository.registerOrUpdatePeer(
                identity    = match { it.nodeId == "joined-node" },
                timestampMs = any(),
                source      = DiscoverySource.RELAY_HA,
                ipAddress   = "9.10.11.12",
                port        = 7777,
                isSuperPair = false
            )
        }
    }

    // -------------------------------------------------------------------------
    // Story Bully : joinAsParticipant délègue à RelayWebSocketClient.sendJoin
    // -------------------------------------------------------------------------

    @Test
    fun `joinAsParticipant retourne success quand sendJoin retourne true`() = runTest {
        every { relayClient.sendJoin(any(), any(), any(), any()) } returns true

        val repo = buildRepo()
        val result = repo.joinAsParticipant("test-node-id", "1.2.3.4", 5555, 0.7f)

        assertTrue(result.isSuccess)
        verify { relayClient.sendJoin("test-node-id", "1.2.3.4", 5555, 0.7f) }
    }

    @Test
    fun `joinAsParticipant retourne failure quand sendJoin retourne false`() = runTest {
        every { relayClient.sendJoin(any(), any(), any(), any()) } returns false

        val repo = buildRepo()
        val result = repo.joinAsParticipant("test-node-id", null, null, 0.5f)

        assertTrue(result.isFailure)
    }
}
