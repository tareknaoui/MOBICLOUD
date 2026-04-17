package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires pour [RunBullyElectionUseCase].
 *
 * F-07 : On injecte [UnconfinedTestDispatcher] comme [defaultDispatcher] pour que
 * [advanceTimeBy] contrôle les délais à l'intérieur du flow (qui utilise [flowOn(defaultDispatcher)]).
 * Sans cette injection, [Dispatchers.Default] serait hors de portée du scheduler de test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunBullyElectionUseCaseTest {

    private lateinit var peerRepository: PeerRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var networkClient: IElectionNetworkClient

    private lateinit var runBullyElectionUseCase: RunBullyElectionUseCase

    private val localIdentity = NodeIdentity("localNodeId", ByteArray(0), 1.0f)

    @Before
    fun setup() {
        peerRepository = mockk()
        securityRepository = mockk()
        trustScoreProvider = mockk()
        networkClient = mockk()

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(ByteArray(0))
        coEvery { trustScoreProvider.getTrustScore("localNodeId") } returns 1
    }

    private fun buildUseCase(testDispatcher: TestDispatcher) = RunBullyElectionUseCase(
        peerRepository = peerRepository,
        securityRepository = securityRepository,
        trustScoreProvider = trustScoreProvider,
        networkClient = networkClient,
        defaultDispatcher = testDispatcher          // F-07 : injection du dispatcher de test
    )

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Crée un StateFlow de pairs où aucun n'est Super-Pair — condition déclenchante de l'élection.
     */
    private fun noSuperPairFlow() = MutableStateFlow(emptyList<Peer>())

    /**
     * Crée un SharedFlow partagé simulant le canal réseau entrant.
     */
    private fun incomingFlow() = MutableSharedFlow<ElectionPayload>()

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `when no active superpair for 5s, and no ALIVE received, node wins election`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)

            val peersFlow = noSuperPairFlow()
            every { peerRepository.peers } returns peersFlow
            coEvery {
                peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any())
            } returns Result.success(Unit)

            val incomingMessagesFlow = incomingFlow()
            every { networkClient.incomingMessages } returns incomingMessagesFlow
            coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)

            val flowResult = buildUseCase(testDispatcher)()

            var finalResult: Result<*>? = null
            val job = launch(testDispatcher) {
                finalResult = flowResult.first()
            }

            // Avancer de 5s (monitoring réactif) puis 3s (timeout ALIVE) — aucun ALIVE émis
            advanceTimeBy(5_001L)
            advanceTimeBy(3_001L)

            job.join()

            assertTrue(finalResult?.isSuccess == true)
            val election = finalResult?.getOrNull() as com.mobicloud.domain.models.SuperPairElection
            assertEquals("localNodeId", election.electedNode.nodeId)

            coVerify(exactly = 1) {
                networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.ELECTION })
            }
            coVerify(exactly = 1) {
                networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.COORDINATOR })
            }
        }

    @Test
    fun `when higher scoring ALIVE received within 3s, election is lost`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val peersFlow = noSuperPairFlow()
        every { peerRepository.peers } returns peersFlow

        val incomingMessagesFlow = incomingFlow()
        every { networkClient.incomingMessages } returns incomingMessagesFlow
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)

        val flowResult = buildUseCase(testDispatcher)()

        var finalResult: Result<*>? = null
        val job = launch(testDispatcher) {
            finalResult = flowResult.first()
        }

        // Avancer de 5s pour dépasser le monitoring
        advanceTimeBy(5_001L)

        // Émettre un ALIVE de score supérieur dans la fenêtre des 3s
        incomingMessagesFlow.emit(
            ElectionPayload(
                senderNodeId = "strongerNode",
                type = ElectionMessageType.ALIVE,
                reliabilityScore = 2.0f,
                signatureBytes = ByteArray(0)
            )
        )

        job.join()

        assertTrue(finalResult?.isFailure == true)
        val message = finalResult?.exceptionOrNull()?.message
        assertTrue(message?.contains("lost to a higher scoring node") == true)

        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.ELECTION })
        }
        coVerify(exactly = 0) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.COORDINATOR })
        }
    }

    @Test
    fun `when superpair appears during monitoring window, election is aborted`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        // Commence sans Super-Pair, puis un Super-Pair apparaît après 2s (avant les 5s)
        val peersFlow = MutableStateFlow(emptyList<Peer>())
        every { peerRepository.peers } returns peersFlow

        val incomingMessagesFlow = incomingFlow()
        every { networkClient.incomingMessages } returns incomingMessagesFlow
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)

        val flowResult = buildUseCase(testDispatcher)()

        var finalResult: Result<*>? = null
        val job = launch(testDispatcher) {
            finalResult = flowResult.first()
        }

        // Après 2s : un Super-Pair apparaît → le timer de 5s est réinitialisé
        advanceTimeBy(2_000L)
        val superPeer = Peer(
            identity = NodeIdentity("superPeer", ByteArray(0), 5.0f),
            lastSeenTimestampMs = System.currentTimeMillis(),
            isActive = true,
            isSuperPair = true
        )
        peersFlow.value = listOf(superPeer)

        // Avancer encore 10s — l'élection ne doit jamais être déclenchée car un Super-Pair est présent
        advanceTimeBy(10_000L)

        // Flow bloqué (jamais d'émission) → le job tourne encore
        assertTrue(job.isActive)

        job.cancel()

        // Aucun broadcast ne doit avoir été envoyé
        coVerify(exactly = 0) {
            networkClient.broadcastElectionMessage(any())
        }
    }
}
