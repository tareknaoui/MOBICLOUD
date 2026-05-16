package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.m11_join.HEARTBEAT_INTERVAL_MS
import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SP_TIMEOUT_MS
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemberHeartbeatUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var identityRepository: IdentityRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var sender: IMemberHeartbeatSender
    private lateinit var wifiRepository: WifiNetworkRepository
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var networkEventRepository: NetworkEventRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private var virtualNow = 0L
    private val spNodeId = byteArrayOf(0x11.toByte())
    private val identity = NodeIdentity("aabb", byteArrayOf(1, 2), 0.9f)

    @Before
    fun setUp() {
        securityRepository = mockk()
        identityRepository = mockk()
        nodeSettingsRepository = mockk()
        sender = mockk(relaxed = true)
        wifiRepository = mockk()
        joinStateMachine = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)

        coEvery { identityRepository.getIdentity() } returns Result.success(identity)
        coEvery { securityRepository.getIdentity() } returns Result.success(identity)
        coEvery { nodeSettingsRepository.observeFreeSpaceBytes() } returns flowOf(1_000_000L)
        every { wifiRepository.getLocalIpAddress() } returns "192.168.1.1"
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0x01))
        coEvery { sender.send(any(), any()) } returns Result.success(Unit)
        every { joinStateMachine.currentState } returns MutableStateFlow(
            NodeJoinState.Member("cid", spNodeId)
        )
    }

    private fun makeUseCase(scope: TestScope): MemberHeartbeatUseCase =
        MemberHeartbeatUseCase(
            securityRepository, identityRepository, nodeSettingsRepository,
            sender, wifiRepository, joinStateMachine, networkEventRepository,
            scope, clock = { virtualNow }
        )

    // 1. Cycle nominal : start() → 1 send après 30s → markSpSeen → continue
    @Test
    fun `cycle nominal envoie heartbeat apres HEARTBEAT_INTERVAL_MS`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        useCase.start(spNodeId)
        advanceTimeBy(HEARTBEAT_INTERVAL_MS + 1)
        coVerify(atLeast = 1) { sender.send(any(), any<Heartbeat>()) }
        useCase.stop()
    }

    // 2. SP timeout : start() → 91s sans markSpSeen → SpTimeoutDetected + stop()
    @Test
    fun `sp timeout apres 91s declenche SpTimeoutDetected`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        useCase.start(spNodeId)
        virtualNow = SP_TIMEOUT_MS + 1_000L
        advanceTimeBy(HEARTBEAT_INTERVAL_MS + 1)
        coVerify { joinStateMachine.transition(any<JoinEvent.SpTimeoutDetected>()) }
        useCase.stop()
    }

    // 3. signData failure → skip ce cycle
    @Test
    fun `signData failure skip le cycle sans crash`() = runTest(testDispatcher) {
        coEvery { securityRepository.signData(any()) } returns Result.failure(Exception("sign error"))
        val useCase = makeUseCase(this)
        useCase.start(spNodeId)
        advanceTimeBy(HEARTBEAT_INTERVAL_MS + 1)
        coVerify(exactly = 0) { sender.send(any(), any()) }
        useCase.stop()
    }

    // 4. start() × 2 → P1 (review R2) : nouvelle sémantique, le deuxième start() remplace
    // proprement l'ancien Job via cancelAndJoin (idempotent vis-à-vis "un seul Job actif",
    // mais le Job lui-même est neuf après le 2e appel).
    @Test
    fun `start deux fois ne fuit pas de Job actif`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        useCase.start(spNodeId)
        val firstJob = useCase.heartbeatJob
        useCase.start(spNodeId)
        // Le précédent Job a été cancelAndJoin → il n'est plus actif.
        assertTrue("Le premier Job doit être annulé", firstJob?.isActive == false || firstJob?.isCancelled == true)
        // Un nouveau Job actif est en place.
        assertTrue("Un Job actif doit être en place après le 2e start", useCase.heartbeatJob?.isActive == true)
        useCase.stop()
    }

    // 5. stop() → aucun envoi après
    @Test
    fun `stop interrompt les heartbeats`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        useCase.start(spNodeId)
        useCase.stop()
        advanceTimeBy(HEARTBEAT_INTERVAL_MS * 3)
        coVerify(exactly = 0) { sender.send(any(), any()) }
    }
}

