package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.m11_join.Leave
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.toHexString
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

class ProcessLeaveUseCaseTest {

    private lateinit var memberDao: MemberDao
    private lateinit var securityRepository: SecurityRepository
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var sendMemberUpdateUseCase: SendMemberUpdateUseCase
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: ProcessLeaveUseCase

    private val nodeIdBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
    private val nodeIdHex = nodeIdBytes.toHexString()
    private val pubKey = byteArrayOf(0x01)
    private val sig = byteArrayOf(0x10)
    private val now = System.currentTimeMillis()

    private val entity = MemberEntity(nodeIdHex, "cid", pubKey, "1.2.3.4", 80, 100L, now - 5000L, "MEMBER", "ACTIVE")
    private val leave = Leave(nodeIdBytes, now, sig)

    @Before
    fun setUp() {
        memberDao = mockk(relaxed = true)
        securityRepository = mockk()
        joinStateMachine = mockk()
        sendMemberUpdateUseCase = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        useCase = ProcessLeaveUseCase(memberDao, securityRepository, joinStateMachine, sendMemberUpdateUseCase, networkEventRepository)
    }

    @Test
    fun `invoke supprime et diffuse MEMBER_UPDATE si signature valide`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns entity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.deleteByNodeId(nodeIdHex, "cid") } returns 1
        val result = useCase(leave)
        assertTrue(result.isSuccess)
        coVerify { memberDao.deleteByNodeId(nodeIdHex, "cid") }
        coVerify { sendMemberUpdateUseCase.invoke(any()) }
    }

    @Test
    fun `invoke no-op si etat non-SuperPair`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.Undiscovered)
        val result = useCase(leave)
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { memberDao.deleteByNodeId(any(), any()) }
    }

    @Test
    fun `invoke retourne failure si signature invalide`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns entity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)
        val result = useCase(leave)
        assertTrue(result.isFailure)
    }
}
