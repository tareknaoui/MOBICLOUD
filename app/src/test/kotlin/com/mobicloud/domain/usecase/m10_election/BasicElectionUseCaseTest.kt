package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BasicElectionUseCaseTest {

    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var securityRepository: SecurityRepository
    private lateinit var useCase: BasicElectionUseCase

    private val localIdentity = NodeIdentity("local_node_id_001", byteArrayOf(1))
    
    @Before
    fun setUp() {
        trustScoreProvider = mockk()
        securityRepository = mockk()

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)

        useCase = BasicElectionUseCase(
            trustScoreProvider = trustScoreProvider,
            securityRepository = securityRepository
        )
    }

    @Test
    fun `election selects node with highest trust score`() = runTest {
        // Arrange — IDs de même longueur que localIdentity (16 chars) pour passer le filtre BH-06
        val node1 = NodeIdentity("local_node_id_aaa", byteArrayOf(2))
        val node2 = NodeIdentity("local_node_id_bbb", byteArrayOf(3))

        val peers = listOf(
            Peer(node1, System.currentTimeMillis()),
            Peer(node2, System.currentTimeMillis())
        )

        coEvery { trustScoreProvider.getTrustScore("local_node_id_aaa") } returns 50
        coEvery { trustScoreProvider.getTrustScore("local_node_id_bbb") } returns 80
        coEvery { trustScoreProvider.getTrustScore("local_node_id_001") } returns 30

        // Act
        val result = useCase(peers).first()

        // Assert
        assertTrue(result.isSuccess)
        val election = result.getOrNull()!!
        assertEquals(node2, election.electedNode)
    }

    @Test
    fun `election tie breaker uses explicit lexicographical order on same length IDs`() = runTest {
        // Arrange
        val node1 = NodeIdentity("aaaa_node_id", byteArrayOf(2))
        val node2 = NodeIdentity("zzzz_node_id", byteArrayOf(3))
        
        val peers = listOf(
            Peer(node1, System.currentTimeMillis()),
            Peer(node2, System.currentTimeMillis())
        )

        // All have score 50 (tie)
        coEvery { trustScoreProvider.getTrustScore("aaaa_node_id") } returns 50
        coEvery { trustScoreProvider.getTrustScore("zzzz_node_id") } returns 50
        // Important: local ID is shorter/longer so let's make it the same length as the tie breakers to avoid BH-06 requirement throws
        val localIdentityMatch = NodeIdentity("bbbb_node_id", byteArrayOf(1))
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentityMatch)
        coEvery { trustScoreProvider.getTrustScore("bbbb_node_id") } returns 50

        // Act
        val result = useCase(peers).first()

        // Assert
        assertTrue(result.isSuccess)
        val election = result.getOrNull()!!
        assertEquals(node2, election.electedNode) // "zzzz" wins lexicographically over "bbbb" and "aaaa"
    }

    @Test
    fun `election ignores nodes with invalid ID lengths during tie break`() = runTest {
        // Arrange (BH-06 vulnerability fix testing)
        val node1 = NodeIdentity("short", byteArrayOf(2))
        val localIdentityMatch = NodeIdentity("very_long_id", byteArrayOf(1))
        
        val peers = listOf(Peer(node1, System.currentTimeMillis()))

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentityMatch)
        coEvery { trustScoreProvider.getTrustScore("short") } returns 50
        coEvery { trustScoreProvider.getTrustScore("very_long_id") } returns 50

        // Act
        val result = useCase(peers).first()

        // Assert
        assertTrue(result.isSuccess)
        val election = result.getOrNull()!!
        assertEquals(localIdentityMatch, election.electedNode)
    }

    @Test
    fun `election selects local node when peers list is empty`() = runTest {
        // Arrange
        val peers = emptyList<Peer>()
        coEvery { trustScoreProvider.getTrustScore("local_node_id_001") } returns 30
        
        // Act
        val result = useCase(peers).first()
        
        // Assert
        assertTrue(result.isSuccess)
        val election = result.getOrNull()!!
        assertEquals(localIdentity, election.electedNode)
    }
}
