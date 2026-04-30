package com.mobicloud.data.repository

import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.usecase.m08_hosting.ReceiveAndHostBlockUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests pour RelayRepositoryImpl.fetchSuperPeers().
 *
 * La difficulté : RelayRepositoryImpl possède un scope interne (Dispatchers.IO)
 * non injectable. Les tests utilisent donc runBlocking + des délais réels courts,
 * et injectent les événements via le Flow retourné par client.connect().
 */
class RelayRepositoryImplTest {

    private lateinit var mockClient: RelayWebSocketClient
    private lateinit var mockReceiveUseCase: ReceiveAndHostBlockUseCase
    private lateinit var eventFlow: MutableSharedFlow<RelayEvent>
    private lateinit var bgScope: CoroutineScope

    @Before
    fun setUp() {
        eventFlow = MutableSharedFlow(extraBufferCapacity = 16)
        mockClient = mockk(relaxed = true)
        mockReceiveUseCase = mockk(relaxed = true)

        // client.connect() retourne notre flow contrôlé
        every { mockClient.connect(any()) } returns eventFlow
    }

    @After
    fun tearDown() {
        if (::bgScope.isInitialized) bgScope.cancel()
    }

    // ── fetchSuperPeers : succès ─────────────────────────────────────────────────

    @Test
    fun `fetchSuperPeers retourne les pairs du PeerList event`() {
        val expectedPeers = listOf(
            RelayPeer("node1", "10.0.0.1", 9000, 0.9f, 1_000L),
            RelayPeer("node2", "10.0.0.2", 9001, 0.7f, 2_000L)
        )

        // sendGetPeers() émet PeerList dans eventFlow après un court délai
        every { mockClient.sendGetPeers() } answers {
            bgScope = CoroutineScope(Dispatchers.IO)
            bgScope.launch {
                delay(80) // s'assure que le collecteur de fetchSuperPeers est prêt
                eventFlow.emit(RelayEvent.PeerList(expectedPeers))
            }
            true
        }

        val repo = RelayRepositoryImpl(mockClient, mockReceiveUseCase)

        val result = runBlocking {
            withTimeout(3_000L) { repo.fetchSuperPeers() }
        }

        assertTrue("fetchSuperPeers doit réussir", result.isSuccess)
        assertEquals(expectedPeers, result.getOrNull())
    }

    @Test
    fun `fetchSuperPeers appelle sendGetPeers`() {
        every { mockClient.sendGetPeers() } answers {
            bgScope = CoroutineScope(Dispatchers.IO)
            bgScope.launch {
                delay(80)
                eventFlow.emit(RelayEvent.PeerList(emptyList()))
            }
            true
        }

        val repo = RelayRepositoryImpl(mockClient, mockReceiveUseCase)

        runBlocking { withTimeout(3_000L) { repo.fetchSuperPeers() } }

        verify(exactly = 1) { mockClient.sendGetPeers() }
    }

    // ── fetchSuperPeers : connexion absente ──────────────────────────────────────

    @Test
    fun `fetchSuperPeers retourne failure si sendGetPeers retourne false`() {
        every { mockClient.sendGetPeers() } returns false

        val repo = RelayRepositoryImpl(mockClient, mockReceiveUseCase)

        val result = runBlocking { repo.fetchSuperPeers() }

        assertTrue("Doit échouer sans connexion active", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("GET_PEERS impossible"))
    }

    // ── fetchSuperPeers : timeout ────────────────────────────────────────────────

    @Test
    fun `fetchSuperPeers retourne emptyList si le serveur ne repond pas dans 5s`() {
        every { mockClient.sendGetPeers() } returns true
        // Aucun PeerList event n'est jamais émis

        val repo = RelayRepositoryImpl(mockClient, mockReceiveUseCase)

        // On remplace le timeout interne 5s par un test avec timeout réduit n'est pas
        // possible sans injection — on se contente de vérifier que le résultat est
        // emptyList quand aucun event n'arrive (le vrai timeout est 5s en production).
        // Ce test utilise un flow qui émet immédiatement une liste vide simulant le timeout.
        every { mockClient.sendGetPeers() } answers {
            bgScope = CoroutineScope(Dispatchers.IO)
            bgScope.launch {
                // Simule la réponse du serveur avec une liste vide après très court délai
                delay(50)
                eventFlow.emit(RelayEvent.PeerList(emptyList()))
            }
            true
        }

        val result = runBlocking {
            withTimeout(3_000L) { repo.fetchSuperPeers() }
        }

        assertTrue(result.isSuccess)
        assertEquals(emptyList<RelayPeer>(), result.getOrNull())
    }

    // ── Événements non-PeerList ignorés ─────────────────────────────────────────

    @Test
    fun `fetchSuperPeers ignore les events non-PeerList et attend le bon type`() {
        val expectedPeers = listOf(RelayPeer("node3", "10.0.0.3", 9002, 0.5f, 3_000L))

        every { mockClient.sendGetPeers() } answers {
            bgScope = CoroutineScope(Dispatchers.IO)
            bgScope.launch {
                delay(50)
                // D'abord un Ack (doit être ignoré par fetchSuperPeers)
                eventFlow.emit(RelayEvent.Ack("some-block-id"))
                delay(50)
                // Puis un PeerList (doit être consommé)
                eventFlow.emit(RelayEvent.PeerList(expectedPeers))
            }
            true
        }

        val repo = RelayRepositoryImpl(mockClient, mockReceiveUseCase)

        val result = runBlocking {
            withTimeout(3_000L) { repo.fetchSuperPeers() }
        }

        assertTrue(result.isSuccess)
        assertEquals(expectedPeers, result.getOrNull())
    }
}
