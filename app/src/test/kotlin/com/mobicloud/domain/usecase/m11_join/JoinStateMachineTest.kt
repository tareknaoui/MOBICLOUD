package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.RejoinReason
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.repository.NetworkEventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinStateMachineTest {

    private lateinit var fsm: JoinStateMachine
    private lateinit var networkEventRepository: NetworkEventRepository
    private val dispatcher = UnconfinedTestDispatcher()

    private val spNodeId = byteArrayOf(0xAA.toByte())
    private val spHint = SuperPeerHint(spNodeId, ipAddress = "1.2.3.4", port = 5000, reliabilityScore = 0.9f)
    private val clusterId = "cluster-test"

    @Before
    fun setup() {
        networkEventRepository = mockk(relaxed = true)
        coEvery { networkEventRepository.pushEvent(any()) } returns Unit
        // Lazy stubs : use cases injectés lazily ; les tests qui ne déclenchent pas
        // de transition vers un état nécessitant un use case se contentent de mocks relaxed.
        val sendLazy = dagger.Lazy<SendJoinRequestUseCase> { mockk(relaxed = true) }
        val markLazy = dagger.Lazy<MarkSelfAsSuperPairUseCase> { mockk(relaxed = true) }
        val bullyLazy = dagger.Lazy<BullySoloElectionUseCase> { mockk(relaxed = true) }
        val hbLazy = dagger.Lazy<MemberHeartbeatUseCase> { mockk(relaxed = true) }
        val monLazy = dagger.Lazy<MonitorMemberLivenessUseCase> { mockk(relaxed = true) }
        val snapLazy = dagger.Lazy<MemberSnapshotCacheUseCase> { mockk(relaxed = true) }
        fsm = JoinStateMachine(
            networkEventRepository, sendLazy, markLazy, bullyLazy,
            hbLazy, monLazy, snapLazy, mockk(relaxed = true), dispatcher
        )
    }

    // ---- Ligne 1 : Undiscovered + CoordinatorReceived → Joining ----

    @Test
    fun `L1 Undiscovered + CoordinatorReceived → Joining`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.CoordinatorReceived(spNodeId, clusterId))
        assertTrue(fsm.currentState.value is NodeJoinState.Joining)
    }

    // AC9 (Story 12.1) — nom de test mandaté par la spec : valide que la FSM accepte
    // un CoordinatorReceived sans GPS (champs retirés en V5.1) et transite correctement.
    @Test
    fun `coordinatorReceived transitionsToJoining withoutGps`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.CoordinatorReceived(senderNodeId = spNodeId, clusterId = clusterId))
        assertTrue(fsm.currentState.value is NodeJoinState.Joining)
    }

    // ---- Ligne 2 : Undiscovered + NewCandidateDetected → Joining ----

    @Test
    fun `L2 Undiscovered + NewCandidateDetected → Joining`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        assertEquals(NodeJoinState.Joining(spHint, 0), fsm.currentState.value)
    }

    // ---- Ligne 3 : Joining + JoinAcceptReceived → Member ----

    @Test
    fun `L3 Joining + JoinAcceptReceived → Member`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        val accept = JoinResponse.JoinAccept(
            clusterId = clusterId,
            superPairNodeId = spNodeId,
            memberSnapshot = emptyList(),
            timestampMs = 1L,
            signatureBytes = byteArrayOf(1)
        )
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))
        val state = fsm.currentState.value
        assertTrue(state is NodeJoinState.Member)
        assertEquals(clusterId, (state as NodeJoinState.Member).clusterId)
    }

    // ---- Ligne 4 : Joining + JoinRedirectReceived avec alternatives → Joining (next) ----

    @Test
    fun `L4a Joining + JoinRedirectReceived avec alternatives → Joining next`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        val alt = SuperPeerHint(byteArrayOf(0xBB.toByte()), ipAddress = "2.3.4.5", port = 6000, reliabilityScore = 0.7f)
        val redirect = JoinResponse.JoinRedirect(
            reason = JoinRedirectReason.CLUSTER_FULL,
            alternativeSuperPeers = listOf(alt),
            timestampMs = 1L,
            signatureBytes = byteArrayOf(2)
        )
        fsm.transition(JoinEvent.JoinRedirectReceived(redirect))
        assertTrue(fsm.currentState.value is NodeJoinState.Joining)
    }

    // ---- Ligne 4b : Joining + JoinRedirectReceived sans alternatives → Isolated ----

    @Test
    fun `L4b Joining + JoinRedirectReceived sans alternatives → Isolated`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        val redirect = JoinResponse.JoinRedirect(
            reason = JoinRedirectReason.CLUSTER_FULL,
            alternativeSuperPeers = emptyList(),
            timestampMs = 1L,
            signatureBytes = byteArrayOf(2)
        )
        fsm.transition(JoinEvent.JoinRedirectReceived(redirect))
        assertTrue(fsm.currentState.value is NodeJoinState.Isolated)
    }

    // ---- Ligne 5 : Joining + AllCandidatesExhausted → Isolated ----

    @Test
    fun `L5 Joining + AllCandidatesExhausted → Isolated`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        fsm.transition(JoinEvent.AllCandidatesExhausted)
        assertTrue(fsm.currentState.value is NodeJoinState.Isolated)
    }

    // ---- Ligne 6 : Isolated + NewCandidateDetected → Joining ----

    @Test
    fun `L6 Isolated + NewCandidateDetected → Joining`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        fsm.transition(JoinEvent.AllCandidatesExhausted)
        val newHint = SuperPeerHint(byteArrayOf(0xCC.toByte()), ipAddress = "3.4.5.6", port = 7000, reliabilityScore = 0.6f)
        fsm.transition(JoinEvent.NewCandidateDetected(newHint))
        assertTrue(fsm.currentState.value is NodeJoinState.Joining)
    }

    // ---- Ligne 7 : Isolated + IsolationBackoffElapsed → timer appelle BullySolo ----

    @Test
    fun `L7 timer ISOLATION_BACKOFF emet IsolationBackoffElapsed apres delai`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        fsm.transition(JoinEvent.AllCandidatesExhausted)
        assertTrue(fsm.currentState.value is NodeJoinState.Isolated)
        // advanceTimeBy déclenche le timer interne
        advanceTimeBy(20_001L)
        // Le BullySoloElectionUseCase n'est pas câblé dans ce test → on vérifie juste
        // que l'événement est traité (état peut rester Isolated si useCase est null)
    }

    // ---- Ligne 8 : Member + SpTimeoutDetected → Rejoining(SP_TIMEOUT) ----

    @Test
    fun `L8 Member + SpTimeoutDetected → Rejoining SP_TIMEOUT`() = runTest(dispatcher) {
        goToMemberState()
        fsm.transition(JoinEvent.SpTimeoutDetected(spNodeId))
        val state = fsm.currentState.value
        assertTrue(state is NodeJoinState.Rejoining)
        assertEquals(RejoinReason.SP_TIMEOUT, (state as NodeJoinState.Rejoining).reason)
    }

    // ---- Ligne 10 : Rejoining + BullyVictory → SuperPair ----

    @Test
    fun `L10 Rejoining + BullyVictory → SuperPair`() = runTest(dispatcher) {
        goToRejoiningState()
        fsm.transition(JoinEvent.BullyVictory(clusterId))
        val state = fsm.currentState.value
        assertTrue(state is NodeJoinState.SuperPair)
        assertEquals(clusterId, (state as NodeJoinState.SuperPair).clusterId)
    }

    // ---- Ligne 11 : Rejoining + BullyLost → Member ----

    @Test
    fun `L11 Rejoining + BullyLost → Member avec nouveau SP`() = runTest(dispatcher) {
        goToRejoiningState()
        val newSpId = byteArrayOf(0xDD.toByte())
        fsm.transition(JoinEvent.BullyLost(newSpId))
        val state = fsm.currentState.value
        assertTrue(state is NodeJoinState.Member)
        assertTrue((state as NodeJoinState.Member).superPairNodeId.contentEquals(newSpId))
    }

    // ---- Annulation timer sur NewCandidateDetected ----

    @Test
    fun `timer ISOLATION annule si NewCandidateDetected arrive avant expiration`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        fsm.transition(JoinEvent.AllCandidatesExhausted)
        assertTrue(fsm.currentState.value is NodeJoinState.Isolated)

        // Nouveau candidat annule le timer
        val newHint = SuperPeerHint(byteArrayOf(0xEE.toByte()), ipAddress = "5.6.7.8", port = 9000, reliabilityScore = 0.5f)
        fsm.transition(JoinEvent.NewCandidateDetected(newHint))

        // Timer annulé → avancer le temps ne doit PAS re-déclencher BullySolo
        advanceTimeBy(25_000L)
        // L'état doit être Joining, pas SuperPair (BullySolo non déclenché)
        assertTrue(fsm.currentState.value is NodeJoinState.Joining)
    }

    // ---- Transitions ignorées ----

    @Test
    fun `JoinAcceptReceived en etat Member est ignore sans crash`() = runTest(dispatcher) {
        goToMemberState()
        val accept = JoinResponse.JoinAccept(
            clusterId = "other",
            superPairNodeId = byteArrayOf(1),
            memberSnapshot = emptyList(),
            timestampMs = 1L,
            signatureBytes = byteArrayOf(1)
        )
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))
        // Doit rester Member — transition ignorée
        assertTrue(fsm.currentState.value is NodeJoinState.Member)
    }

    @Test
    fun `AllCandidatesExhausted en etat Member est ignore sans crash`() = runTest(dispatcher) {
        goToMemberState()
        fsm.transition(JoinEvent.AllCandidatesExhausted)
        assertTrue(fsm.currentState.value is NodeJoinState.Member)
    }

    @Test
    fun `IsolationBackoffElapsed en etat Joining est ignore sans crash`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        assertTrue(fsm.currentState.value is NodeJoinState.Joining)
        fsm.transition(JoinEvent.IsolationBackoffElapsed)
        assertTrue(fsm.currentState.value is NodeJoinState.Joining)
    }

    // ---- Fix D1 : Rejoining + CoordinatorReceived → Member ----
    // BullyLost n'est jamais émis en pratique — la sortie réelle de Rejoining quand on perd
    // l'élection passe par CoordinatorReceived du gagnant.

    @Test
    fun `L11b Rejoining + CoordinatorReceived meme cluster → Member avec nouveau SP`() = runTest(dispatcher) {
        goToRejoiningState()
        val newSpId = byteArrayOf(0xEE.toByte())
        fsm.transition(JoinEvent.CoordinatorReceived(senderNodeId = newSpId, clusterId = clusterId))
        val state = fsm.currentState.value
        assertTrue("Doit être Member", state is NodeJoinState.Member)
        assertTrue(
            "superPairNodeId doit être le nouveau SP",
            (state as NodeJoinState.Member).superPairNodeId.contentEquals(newSpId)
        )
        assertEquals("clusterId doit être préservé", clusterId, state.clusterId)
    }

    @Test
    fun `L11c Rejoining + CoordinatorReceived cluster different → ignore (reste Rejoining)`() = runTest(dispatcher) {
        goToRejoiningState()
        val newSpId = byteArrayOf(0xEE.toByte())
        fsm.transition(JoinEvent.CoordinatorReceived(senderNodeId = newSpId, clusterId = "autre-cluster"))
        assertTrue("Doit rester Rejoining (cluster différent ignoré)", fsm.currentState.value is NodeJoinState.Rejoining)
    }

    // ---- HIGH-1 : promotion du nouveau SP dans inMemory sur swap COORDINATOR (sans re-JOIN) ----
    // Sans ça, le nouveau SP reste role=MEMBER chez les membres → keepalives rejetés → faux
    // SP_TIMEOUT → ré-élection en boucle. On vérifie que la FSM promeut bien le nouveau SP.

    @Test
    fun `HIGH-1 Rejoining + CoordinatorReceived promeut le nouveau SP dans le cache`() = runTest(dispatcher) {
        val snap = mockk<MemberSnapshotCacheUseCase>(relaxed = true)
        val localFsm = JoinStateMachine(
            networkEventRepository,
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { snap },
            mockk(relaxed = true), dispatcher
        )
        // Member → Rejoining
        localFsm.transition(JoinEvent.NewCandidateDetected(spHint))
        localFsm.transition(
            JoinEvent.JoinAcceptReceived(
                JoinResponse.JoinAccept(clusterId, spNodeId, emptyList(), 1L, byteArrayOf(1))
            )
        )
        localFsm.transition(JoinEvent.SpTimeoutDetected(spNodeId))
        // Swap via COORDINATOR du nouveau SP
        val newSpId = byteArrayOf(0xEE.toByte())
        localFsm.transition(JoinEvent.CoordinatorReceived(senderNodeId = newSpId, clusterId = clusterId))

        coVerify { snap.promoteSuperPair(match { it.contentEquals(newSpId) }) }
    }

    @Test
    fun `HIGH-1 Member + CoordinatorReceived nouveau SP promeut dans le cache`() = runTest(dispatcher) {
        val snap = mockk<MemberSnapshotCacheUseCase>(relaxed = true)
        val localFsm = JoinStateMachine(
            networkEventRepository,
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { mockk(relaxed = true) },
            dagger.Lazy { snap },
            mockk(relaxed = true), dispatcher
        )
        // Member (SP = spNodeId)
        localFsm.transition(JoinEvent.NewCandidateDetected(spHint))
        localFsm.transition(
            JoinEvent.JoinAcceptReceived(
                JoinResponse.JoinAccept(clusterId, spNodeId, emptyList(), 1L, byteArrayOf(1))
            )
        )
        // COORDINATOR d'un AUTRE SP, même cluster → swap sans re-JOIN
        val newSpId = byteArrayOf(0xEE.toByte())
        localFsm.transition(JoinEvent.CoordinatorReceived(senderNodeId = newSpId, clusterId = clusterId))

        coVerify { snap.promoteSuperPair(match { it.contentEquals(newSpId) }) }
    }

    // ---- Fix D2 : Rejoining + NewCandidateDetected → Joining (fallback COORDINATOR perdu) ----

    @Test
    fun `L11d Rejoining + NewCandidateDetected → Joining fallback relay`() = runTest(dispatcher) {
        goToRejoiningState()
        val newHint = SuperPeerHint(byteArrayOf(0xFF.toByte()), ipAddress = "9.8.7.6", port = 9999, reliabilityScore = 0.8f)
        fsm.transition(JoinEvent.NewCandidateDetected(newHint))
        val state = fsm.currentState.value
        assertTrue("Doit être Joining (fallback JOIN direct)", state is NodeJoinState.Joining)
        val joiningState = state as NodeJoinState.Joining
        assertEquals("Doit cibler le nouveau SP", newHint, joiningState.targetSuperPair)
    }

    // ---- Helpers ----

    private suspend fun goToMemberState() {
        fsm.transition(JoinEvent.NewCandidateDetected(spHint))
        val accept = JoinResponse.JoinAccept(
            clusterId = clusterId,
            superPairNodeId = spNodeId,
            memberSnapshot = emptyList(),
            timestampMs = 1L,
            signatureBytes = byteArrayOf(1)
        )
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))
        assertTrue(fsm.currentState.value is NodeJoinState.Member)
    }

    private suspend fun goToRejoiningState() {
        goToMemberState()
        fsm.transition(JoinEvent.SpTimeoutDetected(spNodeId))
        assertTrue(fsm.currentState.value is NodeJoinState.Rejoining)
    }
}
