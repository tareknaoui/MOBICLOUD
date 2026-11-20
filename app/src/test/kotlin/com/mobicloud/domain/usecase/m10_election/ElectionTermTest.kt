package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionEvent
import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.electionSignedBytes
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.LocalRepairBuffer
import com.mobicloud.domain.usecase.m11_join.JoinStateMachine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Numéros de mandat (terms) : réponse aux reviewers R1-1 et R2-2 (sûreté de l'élection
 * sous délais non bornés, élections concurrentes, scores périmés).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElectionTermTest {

    private val cluster = "cluster-local-AAA"

    // ── ElectionStateManager ────────────────────────────────────────────────

    @Test
    fun `candidate term is highest observed term plus one, per cluster`() {
        val m = ElectionStateManager()
        assertEquals(1L, m.nextCandidateTerm(cluster))
        m.observeTerm(cluster, 4)
        assertEquals(5L, m.nextCandidateTerm(cluster))
        // Un autre cluster ne partage pas le compteur
        assertEquals(1L, m.nextCandidateTerm("other-cluster"))
        // Un term plus ancien ne fait pas reculer le compteur
        m.observeTerm(cluster, 2)
        assertEquals(4L, m.highestTerm(cluster))
    }

    @Test
    fun `stale detection ignores legacy term zero and other clusters`() {
        val m = ElectionStateManager()
        m.observeTerm(cluster, 5)
        assertTrue(m.isStale(cluster, 4))
        assertFalse(m.isStale(cluster, 5))
        assertFalse(m.isStale(cluster, 6))
        assertFalse("term 0 = ancienne version, jamais périmé", m.isStale(cluster, 0))
        assertFalse(m.isStale("other-cluster", 1))
    }

    @Test
    fun `mandate lifecycle records own term and survives through the store`() {
        val store = InMemoryTermStore()
        val m = ElectionStateManager(InMemoryCooldownStore(), store)
        m.beginMandate(cluster, 3)
        assertEquals(3L, m.ownTerm(cluster))
        assertEquals(3L, m.highestTerm(cluster))

        // Redémarrage : un nouvel ElectionStateManager relit l'état persisté
        val restarted = ElectionStateManager(InMemoryCooldownStore(), store)
        assertEquals(3L, restarted.ownTerm(cluster))
        assertEquals(3L, restarted.highestTerm(cluster))

        restarted.endMandate()
        assertEquals(0L, restarted.ownTerm(cluster))
        assertEquals("le plus haut term reste connu après la fin du mandat", 3L, restarted.highestTerm(cluster))
    }

    @Test
    fun `signed bytes keep v2 format without term and sign the term in v3`() {
        val v2 = String(electionSignedBytes(ElectionMessageType.COORDINATOR, "n", 0.5f, cluster, 10L), Charsets.UTF_8)
        val v3 = String(electionSignedBytes(ElectionMessageType.COORDINATOR, "n", 0.5f, cluster, 10L, 7L), Charsets.UTF_8)
        assertTrue(v2.startsWith("v2|"))
        assertTrue(v3.startsWith("v3|"))
        assertTrue(v3.endsWith("|7"))
        assertFalse(
            electionSignedBytes(ElectionMessageType.COORDINATOR, "n", 0.5f, cluster, 10L, 7L)
                .contentEquals(electionSignedBytes(ElectionMessageType.COORDINATOR, "n", 0.5f, cluster, 10L, 8L))
        )
    }

    // ── ProcessIncomingElectionEventUseCase ─────────────────────────────────

    private lateinit var securityRepository: SecurityRepository
    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var peerRepository: PeerRepository
    private lateinit var networkClient: IElectionNetworkClient
    private lateinit var localRepairBuffer: LocalRepairBuffer
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var wifiNetworkRepository: WifiNetworkRepository
    private lateinit var signalingRepository: SignalingRepository
    private val incoming = MutableSharedFlow<ElectionPayload>()

    private val localId = "aabbccddeeff00112233"
    private val remoteId = "aabbccddeeff00112244"
    private val localIdentity = NodeIdentity(localId, ByteArray(65))
    private val remoteIdentity = NodeIdentity(remoteId, ByteArray(65))

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
        every { wifiNetworkRepository.getCurrentSsid() } returns null
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(ByteArray(0))
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { trustScoreProvider.getTrustScore(any()) } returns 1 // score local = 1.0
        every { networkClient.incomingMessages } returns incoming
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)
        coEvery { localRepairBuffer.drain() } returns emptyList()
        coEvery { networkEventRepository.pushEvent(any()) } returns Unit
        coEvery { nodeSettingsRepository.updateClusterId(any()) } returns Unit
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 1_000_000L,
            clusterId = cluster
        )
        coEvery {
            peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        every { peerRepository.peers } returns MutableStateFlow(
            listOf(Peer(identity = remoteIdentity, lastSeenTimestampMs = System.currentTimeMillis(), isActive = true))
        )
        coEvery { signalingRepository.fetchActiveSuperPeers() } returns Result.success(Unit)
    }

    private fun processUseCase(state: ElectionStateManager, localIsSuperPair: Boolean): ProcessIncomingElectionEventUseCase {
        val fsm = mockk<JoinStateMachine>(relaxed = true)
        every { fsm.currentState } returns MutableStateFlow(
            if (localIsSuperPair) NodeJoinState.SuperPair(cluster) else NodeJoinState.Undiscovered
        )
        return ProcessIncomingElectionEventUseCase(
            securityRepository = securityRepository,
            trustScoreProvider = trustScoreProvider,
            peerRepository = peerRepository,
            networkClient = networkClient,
            electionStateManager = state,
            localRepairBuffer = localRepairBuffer,
            networkEventRepository = networkEventRepository,
            nodeSettingsRepository = nodeSettingsRepository,
            wifiNetworkRepository = wifiNetworkRepository,
            signalingRepository = signalingRepository,
            joinStateMachine = fsm
        )
    }

    private fun coordinator(term: Long, score: Float = 0.5f, type: ElectionMessageType = ElectionMessageType.COORDINATOR) =
        ElectionPayload(
            senderNodeId = remoteId,
            type = type,
            reliabilityScore = score,
            signatureBytes = ByteArray(0),
            clusterId = cluster,
            term = term
        )

    /** Émet [payload] et retourne le premier événement produit. */
    private suspend fun kotlinx.coroutines.test.TestScope.process(
        useCase: ProcessIncomingElectionEventUseCase,
        payload: ElectionPayload
    ): Result<ElectionEvent> {
        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { result = useCase().first() }
        incoming.emit(payload)
        job.join()
        return result!!
    }

    @Test
    fun `member rejects a coordinator whose term was superseded`() = runTest {
        val state = ElectionStateManager().apply { observeTerm(cluster, 5) }
        val event = process(processUseCase(state, localIsSuperPair = false), coordinator(term = 3)).getOrThrow()

        assertTrue(event is ElectionEvent.StaleCoordinatorRejected)
        coVerify(exactly = 0) { peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `member follows a newer coordinator and records its term`() = runTest {
        val state = ElectionStateManager().apply { observeTerm(cluster, 5) }
        val event = process(processUseCase(state, localIsSuperPair = false), coordinator(term = 6)).getOrThrow()

        assertTrue(event is ElectionEvent.CoordinatorRegistered)
        assertEquals(6L, state.highestTerm(cluster))
    }

    @Test
    fun `legacy coordinator without term keeps the previous behaviour`() = runTest {
        val state = ElectionStateManager().apply { observeTerm(cluster, 5) }
        val event = process(processUseCase(state, localIsSuperPair = false), coordinator(term = 0)).getOrThrow()

        assertTrue(event is ElectionEvent.CoordinatorRegistered)
    }

    @Test
    fun `super-pair steps down when a newer term is announced`() = runTest {
        val state = ElectionStateManager().apply { beginMandate(cluster, 2) }
        val event = process(processUseCase(state, localIsSuperPair = true), coordinator(term = 3)).getOrThrow()

        assertTrue(event is ElectionEvent.StepDownRequired)
        assertEquals(0L, state.ownTerm(cluster))
        assertEquals(3L, state.highestTerm(cluster))
    }

    @Test
    fun `super-pair rejects an older concurrent coordinator as stale`() = runTest {
        val state = ElectionStateManager().apply { beginMandate(cluster, 4) }
        val event = process(processUseCase(state, localIsSuperPair = true), coordinator(term = 3)).getOrThrow()

        assertTrue(event is ElectionEvent.StaleCoordinatorRejected)
        assertEquals(4L, state.ownTerm(cluster))
    }

    @Test
    fun `same term - higher priority super-pair keeps the role and reasserts it`() = runTest {
        val state = ElectionStateManager().apply { beginMandate(cluster, 3) }
        // score distant 0.5 < score local 1.0
        val event = process(processUseCase(state, localIsSuperPair = true), coordinator(term = 3, score = 0.5f)).getOrThrow()

        assertEquals(ElectionEvent.Ignored, event)
        assertEquals(3L, state.ownTerm(cluster))
        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.COORDINATOR && it.term == 3L })
        }
    }

    @Test
    fun `same term - lower priority super-pair steps down`() = runTest {
        val state = ElectionStateManager().apply { beginMandate(cluster, 3) }
        // score distant 2.0 > score local 1.0
        val event = process(processUseCase(state, localIsSuperPair = true), coordinator(term = 3, score = 2.0f)).getOrThrow()

        assertTrue(event is ElectionEvent.StepDownRequired)
        assertEquals(0L, state.ownTerm(cluster))
    }

    @Test
    fun `super-pair answers an ELECTION with its own term`() = runTest {
        val state = ElectionStateManager().apply { beginMandate(cluster, 7) }
        process(
            processUseCase(state, localIsSuperPair = true),
            coordinator(term = 8, type = ElectionMessageType.ELECTION)
        )

        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.COORDINATOR && it.term == 7L })
        }
    }

    // ── RunBullyElectionUseCase ─────────────────────────────────────────────

    private fun runBully(state: ElectionStateManager, dispatcher: kotlinx.coroutines.CoroutineDispatcher): RunBullyElectionUseCase {
        coEvery { nodeSettingsRepository.getClusterIdOnce() } returns cluster
        every { peerRepository.peers } returns MutableStateFlow(
            listOf(Peer(identity = NodeIdentity("witness", ByteArray(0), 0.5f), lastSeenTimestampMs = 0L, isActive = true))
        )
        return RunBullyElectionUseCase(
            peerRepository = peerRepository,
            securityRepository = securityRepository,
            trustScoreProvider = trustScoreProvider,
            networkClient = networkClient,
            electionStateManager = state,
            nodeSettingsRepository = nodeSettingsRepository,
            markSelfAsSuperPairUseCase = mockk(relaxed = true),
            defaultDispatcher = dispatcher
        )
    }

    @Test
    fun `candidate proposes highest term plus one and starts that mandate on victory`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val state = ElectionStateManager().apply { observeTerm(cluster, 4) }
        var result: Result<*>? = null
        val job = launch(dispatcher) { result = runBully(state, dispatcher)().first() }

        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS + 1L)
        job.join()

        assertTrue(result!!.isSuccess)
        assertEquals(5L, state.ownTerm(cluster))
        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.ELECTION && it.term == 5L })
        }
        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.COORDINATOR && it.term == 5L })
        }
    }

    @Test
    fun `candidate does not yield to a stale coordinator`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val state = ElectionStateManager().apply { observeTerm(cluster, 4) }
        var result: Result<*>? = null
        val job = launch(dispatcher) { result = runBully(state, dispatcher)().first() }

        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS + 1L)
        incoming.emit(coordinator(term = 2)) // ancien Super-Pair remplacé
        job.join()

        assertTrue("le candidat ne doit pas céder à un mandat périmé", result!!.isSuccess)
        assertEquals(5L, state.ownTerm(cluster))
    }

    @Test
    fun `candidate yields to a current coordinator`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val state = ElectionStateManager().apply { observeTerm(cluster, 4) }
        var result: Result<*>? = null
        val job = launch(dispatcher) { result = runBully(state, dispatcher)().first() }

        advanceTimeBy(RunBullyElectionUseCase.MONITORING_WINDOW_MS + 1L)
        incoming.emit(coordinator(term = 4)) // Super-Pair en place
        job.join()

        assertTrue(result!!.isFailure)
        assertEquals(0L, state.ownTerm(cluster))
        assertEquals("un candidat battu ne garde pas le term proposé", 4L, state.highestTerm(cluster))
    }
}
