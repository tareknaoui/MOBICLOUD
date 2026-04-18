package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AbdicateSuperPeerUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var networkClient: IElectionNetworkClient
    private lateinit var electionStateManager: ElectionStateManager

    private lateinit var abdicateSuperPeerUseCase: AbdicateSuperPeerUseCase

    private val localIdentity = NodeIdentity("localNode", ByteArray(0), 10.0f)

    @Before
    fun setup() {
        securityRepository = mockk()
        trustScoreProvider = mockk()
        networkClient = mockk()
        electionStateManager = ElectionStateManager()

        abdicateSuperPeerUseCase = AbdicateSuperPeerUseCase(
            securityRepository,
            trustScoreProvider,
            networkClient,
            electionStateManager
        )
    }

    @Test
    fun `abdicate successfully broadcasts message and sets cooldown`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(ByteArray(0))
        coEvery { trustScoreProvider.getTrustScore("localNode") } returns 10
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)

        val result = abdicateSuperPeerUseCase()

        assertTrue(result.isSuccess)
        assertTrue(electionStateManager.isInCooldown())

        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.ABDICATION })
        }
    }
}
