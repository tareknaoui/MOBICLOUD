package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BullySoloElectionUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var markSelfAsSuperPairUseCase: MarkSelfAsSuperPairUseCase
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var useCase: BullySoloElectionUseCase

    private val identity = NodeIdentity("0102", byteArrayOf(0x01, 0x02), 0.8f)

    @Before
    fun setup() {
        securityRepository = mockk()
        peerRepository = mockk(relaxed = true)
        markSelfAsSuperPairUseCase = mockk(relaxed = true)
        joinStateMachine = mockk(relaxed = true)

        coEvery { securityRepository.getIdentity() } returns Result.success(identity)

        useCase = BullySoloElectionUseCase(
            securityRepository, peerRepository, markSelfAsSuperPairUseCase, joinStateMachine
        )
    }

    @Test
    fun `invoke en etat Isolated declenche MarkSelfAsSuperPair avec nouveau clusterId`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(
            NodeJoinState.Isolated(0, System.currentTimeMillis())
        )

        useCase.invoke()

        coVerify { markSelfAsSuperPairUseCase.invoke(any()) }
        coVerify { peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), isSuperPair = true, any()) }
    }

    @Test
    fun `invoke en etat non Isolated est no-op`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(
            NodeJoinState.Member("c1", byteArrayOf(0x01))
        )

        useCase.invoke()

        coVerify(exactly = 0) { markSelfAsSuperPairUseCase.invoke(any()) }
    }

    @Test
    fun `invoke genere un clusterId unique a chaque appel`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(
            NodeJoinState.Isolated(0, System.currentTimeMillis())
        )

        val clusterIds = mutableListOf<String>()
        coEvery { markSelfAsSuperPairUseCase.invoke(any()) } answers {
            clusterIds.add(firstArg())
        }

        useCase.invoke()
        // Réinitialiser pour un second appel
        every { joinStateMachine.currentState } returns MutableStateFlow(
            NodeJoinState.Isolated(1, System.currentTimeMillis())
        )
        useCase.invoke()

        assert(clusterIds.size == 2) { "Attendu 2 clusterId" }
        assert(clusterIds[0] != clusterIds[1]) { "Les clusterId doivent être distincts" }
    }
}
