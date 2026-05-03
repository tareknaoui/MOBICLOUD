package com.mobicloud.data.repository

import android.os.SystemClock
import android.util.Log
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
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

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L

        relayClient = mockk(relaxed = true)
        peerRepository = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        identityRepository = mockk(relaxed = true)

        every { relayClient.connect(any()) } returns emptyFlow()
    }

    private fun buildRepo() = SignalingRepositoryImpl(relayClient, peerRepository, networkEventRepository, identityRepository)

    // -------------------------------------------------------------------------
    // Subtask 4.1 : registerAsSuperPeer retourne Result.success
    // -------------------------------------------------------------------------

    @Test
    fun `registerAsSuperPeer retourne Result_success quand sendRegisterPeer retourne true`() = runTest {
        every { relayClient.sendRegisterPeer(any(), any(), any(), any(), any()) } returns true

        val repo = buildRepo()
        val result = repo.registerAsSuperPeer("192.168.1.10", 48999, 0.87f, System.currentTimeMillis(), "test-node-id")

        assertTrue(result.isSuccess)
        verify { relayClient.sendRegisterPeer(any(), "192.168.1.10", 48999, 0.87f, any()) }
    }

    // -------------------------------------------------------------------------
    // Subtask 4.2 : registerAsSuperPeer retourne Result.failure
    // -------------------------------------------------------------------------

    @Test
    fun `registerAsSuperPeer retourne Result_failure quand sendRegisterPeer retourne false`() = runTest {
        every { relayClient.sendRegisterPeer(any(), any(), any(), any(), any()) } returns false

        val repo = buildRepo()
        val result = repo.registerAsSuperPeer("192.168.1.10", 48999, 0.87f, System.currentTimeMillis(), "test-node-id")

        assertTrue(result.isFailure)
        verify { networkEventRepository.pushEvent(match { it.contains("enregistrement Super-Pair échoué") }) }
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
            lastSeen = now
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
}
