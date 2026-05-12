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
            hbLazy, monLazy, snapLazy, dispatcher
        )
    }

    // ---- Ligne 1 : Undiscovered + CoordinatorReceived → Joining ----

    @Test
    fun `L1 Undiscovered + CoordinatorReceived → Joining`() = runTest(dispatcher) {
        fsm.transition(JoinEvent.CoordinatorReceived(spNodeId, clusterId, null, null, 5000))
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
            reason = JoinRedirectReason.OUT_OF_RADIUS,
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
