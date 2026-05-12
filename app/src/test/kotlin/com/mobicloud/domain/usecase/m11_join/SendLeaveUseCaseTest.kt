package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendLeaveUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var identityRepository: IdentityRepository
    private lateinit var sender: IMemberHeartbeatSender
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: SendLeaveUseCase

    private val spNodeId = byteArrayOf(0x11.toByte())
    private val identity = NodeIdentity("aabb", byteArrayOf(1, 2), 0.9f)

    @Before
    fun setUp() {
        securityRepository = mockk()
        identityRepository = mockk()
        sender = mockk(relaxed = true)
        joinStateMachine = mockk()
        networkEventRepository = mockk(relaxed = true)
        useCase = SendLeaveUseCase(securityRepository, identityRepository, sender, joinStateMachine, networkEventRepository)
    }

    @Test
    fun `invoke envoie LEAVE si etat Member`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.Member("cid", spNodeId))
        coEvery { identityRepository.getIdentity() } returns Result.success(identity)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0x01))
        coEvery { sender.sendLeave(any(), any()) } returns Result.success(Unit)
        useCase()
        coVerify { sender.sendLeave(any(), any()) }
    }

    @Test
    fun `invoke no-op si etat non-Member`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.Undiscovered)
        useCase()
        coVerify(exactly = 0) { sender.sendLeave(any(), any()) }
    }
}
