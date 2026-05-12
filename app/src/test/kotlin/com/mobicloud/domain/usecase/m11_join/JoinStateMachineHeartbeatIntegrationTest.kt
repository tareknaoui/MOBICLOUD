package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.RejoinReason
import com.mobicloud.domain.repository.NetworkEventRepository
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinStateMachineHeartbeatIntegrationTest {

    private lateinit var memberHeartbeatUseCase: MemberHeartbeatUseCase
    private lateinit var monitorMemberLivenessUseCase: MonitorMemberLivenessUseCase
    private lateinit var memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase
    private lateinit var fsm: JoinStateMachine
    private val testDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
    private val spNodeId = byteArrayOf(0xAA.toByte())
    private val newSpNodeId = byteArrayOf(0xBB.toByte())
    private val clusterId = "cluster-1"

    @Before
    fun setUp() {
        memberHeartbeatUseCase = mockk(relaxed = true)
        monitorMemberLivenessUseCase = mockk(relaxed = true)
        memberSnapshotCacheUseCase = mockk(relaxed = true)
        val networkEventRepo = mockk<NetworkEventRepository>(relaxed = true)

        fsm = JoinStateMachine(
            networkEventRepository = networkEventRepo,
            sendJoinRequestUseCaseLazy = Lazy { mockk(relaxed = true) },
            markSelfAsSuperPairUseCaseLazy = Lazy { mockk(relaxed = true) },
            bullySoloElectionUseCaseLazy = Lazy { mockk(relaxed = true) },
            memberHeartbeatUseCaseLazy = Lazy { memberHeartbeatUseCase },
            monitorMemberLivenessUseCaseLazy = Lazy { monitorMemberLivenessUseCase },
            memberSnapshotCacheUseCaseLazy = Lazy { memberSnapshotCacheUseCase },
            defaultDispatcher = testDispatcher
        )
    }

    // 1. Joining → Member déclenche start()
    @Test
    fun `Joining vers Member declenche memberHeartbeatUseCase start`() = runTest {
        // FSM en état Joining
        val accept = JoinResponse.JoinAccept(
            clusterId = clusterId,
            superPairNodeId = spNodeId,
            memberSnapshot = emptyList(),
            timestampMs = 1000L,
            signatureBytes = byteArrayOf()
        )
        fsm.transition(JoinEvent.CoordinatorReceived(spNodeId, clusterId, null, null, 5000))
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))

        verify(timeout = 1000) { memberHeartbeatUseCase.start(spNodeId) }
    }

    // 2. Member → Rejoining (SP_TIMEOUT) est géré (pas de crash)
    @Test
    fun `Member vers Rejoining SpTimeout ne plante pas`() = runTest {
        fsm.transition(JoinEvent.CoordinatorReceived(spNodeId, clusterId, null, null, 5000))
        val accept = JoinResponse.JoinAccept(clusterId, spNodeId, emptyList(), 1000L, byteArrayOf())
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))
        fsm.transition(JoinEvent.SpTimeoutDetected(spNodeId))

        assertEquals(NodeJoinState.Rejoining(RejoinReason.SP_TIMEOUT), fsm.currentState.value)
    }

    // 3. Rejoining → Member (BullyLost) redémarre heartbeat vers nouveau SP
    @Test
    fun `Rejoining BullyLost redémarre heartbeat vers nouveau SP`() = runTest {
        fsm.transition(JoinEvent.CoordinatorReceived(spNodeId, clusterId, null, null, 5000))
        val accept = JoinResponse.JoinAccept(clusterId, spNodeId, emptyList(), 1000L, byteArrayOf())
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))
        fsm.transition(JoinEvent.SpTimeoutDetected(spNodeId))
        fsm.transition(JoinEvent.BullyLost(newSpNodeId))

        verify(timeout = 1000) { memberHeartbeatUseCase.stop() }
        verify(timeout = 1000) { memberHeartbeatUseCase.start(newSpNodeId) }
    }

    // 4. SuperPair → Undiscovered (AbdicationTriggered) arrête MonitorMemberLiveness
    @Test
    fun `SuperPair AbdicationTriggered stoppe monitorMemberLiveness`() = runTest {
        // Mettre la FSM en SuperPair en simulant la victoire Bully
        fsm.transition(JoinEvent.CoordinatorReceived(spNodeId, clusterId, null, null, 5000))
        val accept = JoinResponse.JoinAccept(clusterId, spNodeId, emptyList(), 1000L, byteArrayOf())
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))
        fsm.transition(JoinEvent.SpTimeoutDetected(spNodeId))
        fsm.transition(JoinEvent.BullyVictory(clusterId))
        fsm.transition(JoinEvent.AbdicationTriggered)

        verify(timeout = 1000) { monitorMemberLivenessUseCase.stop() }
        assertEquals(NodeJoinState.Undiscovered, fsm.currentState.value)
    }

    // C6 régression : Member + BullyVictory → SuperPair DOIT appeler memberHeartbeat.stop().
    // Sans le stop(), la coroutine sendOnce continue à HB l'ancien SP (capturé dans la lambda)
    // → le nouveau SP s'envoie des HB à lui-même.
    @Test
    fun `Member BullyVictory stoppe memberHeartbeat`() = runTest {
        // Atteindre l'état Member
        fsm.transition(JoinEvent.CoordinatorReceived(spNodeId, clusterId, null, null, 5000))
        val accept = JoinResponse.JoinAccept(clusterId, spNodeId, emptyList(), 1000L, byteArrayOf())
        fsm.transition(JoinEvent.JoinAcceptReceived(accept))
        assertTrue(fsm.currentState.value is NodeJoinState.Member)

        // Member gagne directement une élection (cas re-élection sans Rejoining)
        fsm.transition(JoinEvent.BullyVictory(clusterId))

        verify(timeout = 1000) { memberHeartbeatUseCase.stop() }
        assertEquals(NodeJoinState.SuperPair(clusterId), fsm.currentState.value)
    }
}
