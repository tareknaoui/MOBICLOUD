package com.mobicloud.data.repository

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.usecase.m08_hosting.ReceiveAndHostBlockUseCase
import com.mobicloud.domain.usecase.m08_hosting.RespondToBlockRequestUseCase
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Tests pour RelayRepositoryImpl :
 *   - fetchSuperPeers (Story 8.x)
 *   - requestBlock (Story 9.4) : success path, timeout, ws absent, double-request,
 *     dispatch dichotomique BlockReceived (pending vs receive-and-host).
 *
 * La difficulté : RelayRepositoryImpl possède un scope interne (Dispatchers.IO)
 * non injectable. Les tests utilisent runBlocking + des délais réels courts,
 * et injectent les événements via le Flow retourné par client.connect().
 */
@OptIn(ExperimentalSerializationApi::class)
class RelayRepositoryImplTest {

    private lateinit var mockClient: RelayWebSocketClient
    private lateinit var mockReceiveUseCase: ReceiveAndHostBlockUseCase
    private lateinit var mockRespondUseCase: RespondToBlockRequestUseCase
    private lateinit var respondProvider: Provider<RespondToBlockRequestUseCase>
    private lateinit var eventFlow: MutableSharedFlow<RelayEvent>
    private lateinit var bgScope: CoroutineScope

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        eventFlow = MutableSharedFlow(extraBufferCapacity = 16)
        mockClient = mockk(relaxed = true)
        mockReceiveUseCase = mockk(relaxed = true)
        mockRespondUseCase = mockk(relaxed = true)
        respondProvider = Provider { mockRespondUseCase }

        // client.eventBus est le bus interne que le repo collecte dans son init block
        every { mockClient.eventBus } returns eventFlow
    }

    @After
    fun tearDown() {
        if (::bgScope.isInitialized) bgScope.cancel()
        unmockkStatic(Log::class)
    }

    private fun newRepo() = RelayRepositoryImpl(mockClient, mockReceiveUseCase, respondProvider)

    // ── fetchSuperPeers : succès ─────────────────────────────────────────────────

    @Test
    fun `fetchSuperPeers retourne les pairs du PeerList event`() {
        val expectedPeers = listOf(
            RelayPeer("node1", "10.0.0.1", 9000, 0.9f, 1_000L),
            RelayPeer("node2", "10.0.0.2", 9001, 0.7f, 2_000L)
        )

        every { mockClient.sendGetPeers() } answers {
            bgScope = CoroutineScope(Dispatchers.IO)
            bgScope.launch {
                delay(80)
                eventFlow.emit(RelayEvent.PeerList(expectedPeers))
            }
            true
        }

        val repo = newRepo()
        val result = runBlocking { withTimeout(3_000L) { repo.fetchSuperPeers() } }

        assertTrue(result.isSuccess)
        assertEquals(expectedPeers, result.getOrNull())
    }

    @Test
    fun `fetchSuperPeers retourne failure si sendGetPeers retourne false`() {
        every { mockClient.sendGetPeers() } returns false

        val repo = newRepo()
        val result = runBlocking { repo.fetchSuperPeers() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("GET_PEERS impossible"))
    }

    // ── requestBlock : success path ─────────────────────────────────────────────

    @Test
    fun `requestBlock success — BlockReceived avec blockId pending fulfill le deferred`() = runBlocking {
        val blockId = "a".repeat(64)
        val remoteNodeId = "remote0000000001"
        val expectedMsg = BlockTransferMessage(
            blockId = blockId,
            ownerId = "owner0000000001",
            fragmentIndex = 3,
            isParity = false,
            ciphertext = ByteArray(64) { it.toByte() },
            iv = ByteArray(12) { (it + 1).toByte() },
            originalFileSize = 0L
        )
        val encoded = MobiCloudProtoBuf.encodeToByteArray(BlockTransferMessage.serializer(), expectedMsg)

        every { mockClient.sendRequestBlock(remoteNodeId, blockId) } answers {
            bgScope = CoroutineScope(Dispatchers.IO)
            bgScope.launch {
                delay(80)
                eventFlow.emit(RelayEvent.BlockReceived(remoteNodeId, blockId, encoded))
            }
            true
        }

        val repo = newRepo()
        val result = withTimeout(3_000L) { repo.requestBlock(remoteNodeId, blockId, 2_000L) }

        assertTrue("requestBlock doit réussir", result.isSuccess)
        assertEquals(expectedMsg, result.getOrNull())
        // Crucial : un BlockReceived consommé par pendingBlockRequests NE doit PAS aller à receiveAndHost.
        coVerify(exactly = 0) { mockReceiveUseCase.receive(any()) }
    }

    // ── requestBlock : ws absente (sendRequestBlock retourne false) ─────────────

    @Test
    fun `requestBlock retourne IllegalStateException si sendRequestBlock retourne false`() = runBlocking {
        val blockId = "b".repeat(64)
        every { mockClient.sendRequestBlock(any(), any()) } returns false

        val repo = newRepo()
        val result = repo.requestBlock("remote0000000001", blockId, 2_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Aucune connexion"))
    }

    // ── requestBlock : timeout ──────────────────────────────────────────────────

    @Test
    fun `requestBlock retourne SocketTimeoutException si aucune reponse en timeoutMs`() = runBlocking {
        val blockId = "c".repeat(64)
        every { mockClient.sendRequestBlock(any(), any()) } returns true
        // Aucun BlockReceived event jamais émis → timeout

        val repo = newRepo()
        val result = repo.requestBlock("remote0000000001", blockId, 200L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SocketTimeoutException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Timeout"))
    }

    // ── requestBlock : double request sur même blockId ──────────────────────────

    @Test
    fun `requestBlock concurrent same blockId — second appel echoue avec deja en cours`() = runBlocking {
        val blockId = "d".repeat(64)
        every { mockClient.sendRequestBlock(any(), any()) } returns true

        val repo = newRepo()

        // Lance le 1er appel en parallèle (il va attendre indéfiniment, on cancel ensuite)
        bgScope = CoroutineScope(Dispatchers.IO)
        val firstJob = bgScope.launch {
            repo.requestBlock("remoteA0000000001", blockId, 5_000L)
        }
        delay(100)  // s'assure que le 1er appel a posé son deferred

        val secondResult = repo.requestBlock("remoteB0000000001", blockId, 5_000L)

        assertTrue(secondResult.isFailure)
        assertTrue(secondResult.exceptionOrNull() is IllegalStateException)
        assertTrue(secondResult.exceptionOrNull()!!.message!!.contains("déjà en cours"))

        firstJob.cancel()
    }

    // ── Dispatch dichotomique : BlockReceived non-pending → receiveAndHost ──────

    @Test
    fun `BlockReceived sans pendingRequest est route vers receiveAndHostBlockUseCase`() = runBlocking {
        val blockId = "e".repeat(64)
        val msg = BlockTransferMessage(
            blockId = blockId,
            ownerId = "owner",
            fragmentIndex = 0,
            isParity = false,
            ciphertext = ByteArray(32) { it.toByte() },
            iv = ByteArray(12),
            originalFileSize = 0L
        )
        val encoded = MobiCloudProtoBuf.encodeToByteArray(BlockTransferMessage.serializer(), msg)

        val repo = newRepo()
        delay(100) // laisse le repo collecter

        eventFlow.emit(RelayEvent.BlockReceived("from0000000001", blockId, encoded))
        delay(200) // laisse le dispatch passer

        coVerify(exactly = 1) { mockReceiveUseCase.receive(msg) }
    }

    // ── BlockRequestForwarded → RespondToBlockRequestUseCase ────────────────────

    @Test
    fun `BlockRequestForwarded est route vers RespondToBlockRequestUseCase`() = runBlocking {
        val blockId = "f".repeat(64)
        val fromNodeId = "requester0000001"

        val repo = newRepo()
        delay(100)

        eventFlow.emit(RelayEvent.BlockRequestForwarded(fromNodeId, blockId))
        delay(200)

        coVerify(exactly = 1) { mockRespondUseCase.respond(fromNodeId, blockId) }
    }

    // ── Désérialisation échouée côté pending → completeExceptionally ───────────

    @Test
    fun `requestBlock retourne failure si BlockReceived data est malforme`() = runBlocking {
        val blockId = "1".repeat(64)
        val remoteNodeId = "remote0000000001"

        every { mockClient.sendRequestBlock(remoteNodeId, blockId) } answers {
            bgScope = CoroutineScope(Dispatchers.IO)
            bgScope.launch {
                delay(80)
                // Données corrompues — pas un Protobuf valide
                eventFlow.emit(RelayEvent.BlockReceived(remoteNodeId, blockId, byteArrayOf(0x01, 0x02, 0x03)))
            }
            true
        }

        val repo = newRepo()
        val result = withTimeout(3_000L) { repo.requestBlock(remoteNodeId, blockId, 2_000L) }

        assertTrue("Désérialisation échouée doit remonter en failure", result.isFailure)
        // Spec ligne 665 : Result.failure(SerializationException) attendu.
        assertTrue(
            "Désérialisation échouée doit retourner SerializationException, got ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is SerializationException
        )
        // receiveAndHost JAMAIS appelé (le pending a consommé l'event).
        coVerify(exactly = 0) { mockReceiveUseCase.receive(any()) }
    }

    // ── CancellationException propagation (W-9.3-7 / spec ligne 667) ───────────

    @Test
    fun `requestBlock propage CancellationException sans la convertir en Result_failure`() = runBlocking {
        val blockId = "c".repeat(64)
        val remoteNodeId = "remote00cancel01"

        // Le mock laisse l'envoi réussir mais ne fournit jamais de réponse :
        // requestBlock va se bloquer dans deferred.await() — et c'est là qu'on cancel.
        every { mockClient.sendRequestBlock(remoteNodeId, blockId) } returns true

        val repo = newRepo()
        bgScope = CoroutineScope(Dispatchers.IO)
        var caughtCancellation = false
        var caughtOther: Throwable? = null

        val job: Job = bgScope.launch {
            try {
                repo.requestBlock(remoteNodeId, blockId, 30_000L)
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            } catch (e: Throwable) {
                caughtOther = e
            }
        }
        // Laisser putIfAbsent + sendRequestBlock + deferred.await() s'enclencher.
        delay(150)
        job.cancelAndJoin()

        assertTrue(
            "CancellationException doit être propagée (W-9.3-7), pas convertie en Result.failure ; otherCaught=${caughtOther?.javaClass?.simpleName}",
            caughtCancellation
        )
        assertNull("Aucune autre exception ne doit être levée", caughtOther)
    }
}
