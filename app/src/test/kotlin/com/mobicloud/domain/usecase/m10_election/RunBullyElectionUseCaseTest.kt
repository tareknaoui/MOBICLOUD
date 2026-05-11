package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.models.NodeSettings
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
    private lateinit var nodeSettingsRepository: NodeSettingsRepository

    private lateinit var runBullyElectionUseCase: RunBullyElectionUseCase

    private val localIdentity = NodeIdentity("localNodeId", ByteArray(0), 1.0f)

    @Before
    fun setup() {
        peerRepository = mockk()
        securityRepository = mockk()
        trustScoreProvider = mockk()
        networkClient = mockk()
        nodeSettingsRepository = mockk()

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(ByteArray(0))
        coEvery { trustScoreProvider.getTrustScore("localNodeId") } returns 1
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 1_000_000_000L,
            clusterId = "cluster-local-test"
        )
        coEvery { nodeSettingsRepository.getCurrentWifiClusterId() } returns "cluster-local-test"
    }

    private fun buildUseCase(testDispatcher: TestDispatcher) = RunBullyElectionUseCase(
        peerRepository = peerRepository,
        securityRepository = securityRepository,
        trustScoreProvider = trustScoreProvider,
        networkClient = networkClient,
        electionStateManager = ElectionStateManager(),
        nodeSettingsRepository = nodeSettingsRepository,
        defaultDispatcher = testDispatcher
    )

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Crée un StateFlow de pairs où aucun n'est Super-Pair — mais au moins un autre pair actif
     * est présent (Garde-fou bootstrap : Bully ne démarre que si on a découvert un voisin).
     */
    private fun noSuperPairFlow() = MutableStateFlow(
        listOf(
            Peer(
                identity = NodeIdentity("witnessPeer", ByteArray(0), 0.5f),
                lastSeenTimestampMs = System.currentTimeMillis(),
                isActive = true,
                isSuperPair = false
            )
        )
    )

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

            // Avancer de 20s (fenêtre de monitoring) puis 3s (timeout ALIVE) — aucun ALIVE émis
            advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS + 1L)
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

        // Avancer de 20s pour dépasser le monitoring
        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS + 1L)

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

        // Commence avec 1 pair non-super (pour passer le garde-fou bootstrap),
        // puis un Super-Pair apparaît avant la fin de la fenêtre de monitoring.
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

        // Au milieu de la fenêtre : un Super-Pair apparaît → la condition redevient false → timer reset
        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS / 2)
        val superPeer = Peer(
            identity = NodeIdentity("superPeer", ByteArray(0), 5.0f),
            lastSeenTimestampMs = System.currentTimeMillis(),
            isActive = true,
            isSuperPair = true
        )
        peersFlow.value = peersFlow.value + superPeer

        // Avancer encore une fenêtre complète — l'élection ne doit jamais être déclenchée
        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS + 1L)

        // Flow bloqué (jamais d'émission) → le job tourne encore
        assertTrue(job.isActive)

        job.cancel()

        // Aucun broadcast ne doit avoir été envoyé
        coVerify(exactly = 0) {
            networkClient.broadcastElectionMessage(any())
        }
    }

    // Garde-fou bootstrap : si peerRepository ne contient AUCUN autre pair actif,
    // l'élection ne doit jamais démarrer (évite la course de bootstrap où plusieurs
    // nœuds simultanés s'élisent en parallèle car chacun se croit seul).
    @Test
    fun `does not start election when no other active peer in registry`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

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

        // Avancer largement au-delà de la fenêtre de monitoring sans peupler peerRepository
        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS * 3)

        // Le flow doit toujours attendre — aucun broadcast envoyé
        assertTrue(job.isActive)
        job.cancel()

        coVerify(exactly = 0) {
            networkClient.broadcastElectionMessage(any())
        }
    }

    // Garde-fou bootstrap (suite) : si un pair apparaît tardivement dans peerRepository,
    // la fenêtre de monitoring démarre à ce moment-là — pas avant.
    @Test
    fun `election starts only after first active peer appears`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        val peersFlow = MutableStateFlow(emptyList<Peer>())
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

        // Pendant 10s, peerRepository est vide → garde-fou actif, pas d'élection
        advanceTimeBy(10_000L)
        coVerify(exactly = 0) { networkClient.broadcastElectionMessage(any()) }

        // Un voisin apparaît → la fenêtre de monitoring peut commencer
        peersFlow.value = listOf(
            Peer(
                identity = NodeIdentity("lateNeighbor", ByteArray(0), 0.5f),
                lastSeenTimestampMs = System.currentTimeMillis(),
                isActive = true,
                isSuperPair = false
            )
        )

        // 20s + 3s pour victoire complète
        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS + 1L)
        advanceTimeBy(3_001L)

        job.join()

        assertTrue(finalResult?.isSuccess == true)
        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.ELECTION })
        }
    }
}
