package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarkSelfAsSuperPairUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var identityRepository: IdentityRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var memberRegistry: MemberRegistry
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var useCase: MarkSelfAsSuperPairUseCase

    private val identity = NodeIdentity("aabb", byteArrayOf(0xAA.toByte(), 0xBB.toByte()), 0.9f)

    @Before
    fun setup() {
        securityRepository = mockk()
        identityRepository = mockk()
        nodeSettingsRepository = mockk(relaxed = true)
        peerRepository = mockk(relaxed = true)
        memberRegistry = RamMemberRegistry()
        joinStateMachine = mockk(relaxed = true)
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.Undiscovered)

        coEvery { securityRepository.getIdentity() } returns Result.success(identity)
        coEvery { identityRepository.getIdentity() } returns Result.success(identity)

        val monLazy = dagger.Lazy<MonitorMemberLivenessUseCase> { mockk(relaxed = true) }
        val snapLazy = dagger.Lazy<MemberSnapshotCacheUseCase> { mockk(relaxed = true) }
        useCase = MarkSelfAsSuperPairUseCase(
            securityRepository, identityRepository, nodeSettingsRepository, peerRepository,
            memberRegistry, joinStateMachine, monLazy, snapLazy
        )
    }

    @Test
    fun `invoke initialise memberRegistry avec self comme SUPER_PAIR`() = runTest {
        useCase.invoke("cluster-x")
        assertEquals(1, memberRegistry.size())
        assertEquals(MemberRole.SUPER_PAIR, memberRegistry.list().first().role)
    }

    @Test
    fun `invoke persiste le clusterId`() = runTest {
        useCase.invoke("cluster-y")
        coVerify { nodeSettingsRepository.updateClusterId("cluster-y") }
    }

    @Test
    fun `invoke transite la state machine vers BullyVictory`() = runTest {
        useCase.invoke("cluster-z")
        coVerify { joinStateMachine.transition(JoinEvent.BullyVictory("cluster-z")) }
    }
}
