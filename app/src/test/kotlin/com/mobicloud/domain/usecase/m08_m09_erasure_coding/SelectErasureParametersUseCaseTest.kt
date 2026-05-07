package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelectErasureParametersUseCaseTest {

    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var peerRepository: PeerRepository
    private lateinit var useCase: SelectErasureParametersUseCase

    @Before
    fun setUp() {
        trustScoreProvider = mockk()
        peerRepository = mockk()
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        useCase = SelectErasureParametersUseCase(trustScoreProvider, peerRepository)
    }

    // --- Profil fiable (score ≥ 0.7), sans pairs → score local seul ---

    @Test
    fun `score 1_0 retourne K=4 N=2`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 1.0f
        val params = useCase()
        assertEquals(4, params.k)
        assertEquals(2, params.n)
    }

    @Test
    fun `score 0_7 exactement retourne K=4 N=2`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.7f
        val params = useCase()
        assertEquals(4, params.k)
        assertEquals(2, params.n)
    }

    @Test
    fun `score 0_85 retourne K=4 N=2`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.85f
        val params = useCase()
        assertEquals(4, params.k)
        assertEquals(2, params.n)
    }

    // --- Profil moyen (0.4 ≤ score < 0.7), sans pairs ---

    @Test
    fun `score 0_5 retourne K=3 N=3`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.5f
        val params = useCase()
        assertEquals(3, params.k)
        assertEquals(3, params.n)
    }

    @Test
    fun `score 0_4 exactement retourne K=3 N=3`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.4f
        val params = useCase()
        assertEquals(3, params.k)
        assertEquals(3, params.n)
    }

    @Test
    fun `score juste sous 0_7 retourne K=3 N=3`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.69f
        val params = useCase()
        assertEquals(3, params.k)
        assertEquals(3, params.n)
    }

    // --- Profil faible (score < 0.4), sans pairs ---

    @Test
    fun `score 0_2 retourne K=2 N=4`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.2f
        val params = useCase()
        assertEquals(2, params.k)
        assertEquals(4, params.n)
    }

    @Test
    fun `score 0_0 retourne K=2 N=4`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.0f
        val params = useCase()
        assertEquals(2, params.k)
        assertEquals(4, params.n)
    }

    @Test
    fun `score juste sous 0_4 retourne K=2 N=4`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.39f
        val params = useCase()
        assertEquals(2, params.k)
        assertEquals(4, params.n)
    }

    // --- Contrainte GF(256) : K+N ≤ 255 pour tous les profils ---

    @Test
    fun `tous les profils respectent la contrainte GF256`() = runTest {
        val scores = listOf(1.0f, 0.7f, 0.5f, 0.4f, 0.2f, 0.0f)
        for (score in scores) {
            coEvery { trustScoreProvider.getScore() } returns score
            val params = useCase()
            assertTrue("score=$score → k+n=${params.k + params.n} doit être ≤ 255", params.k + params.n <= 255)
            assertTrue("k >= 1", params.k >= 1)
            assertTrue("n >= 1", params.n >= 1)
        }
    }

    // --- Agrégation réseau : moyenne nœud local + pairs actifs ---

    @Test
    fun `peers fiables élèvent le score réseau vers K=4 N=2`() = runTest {
        // local=0.2, 2 pairs à 1.0 → networkScore = (0.2 + 1.0 + 1.0) / 3 ≈ 0.733 → K=4
        coEvery { trustScoreProvider.getScore() } returns 0.2f
        every { peerRepository.peers } returns MutableStateFlow(
            makePeers(1.0f, 1.0f)
        )
        val params = useCase()
        assertEquals("K=4 avec pairs fiables", 4, params.k)
        assertEquals("N=2 avec pairs fiables", 2, params.n)
    }

    @Test
    fun `peers peu fiables abaissent le score réseau vers K=2 N=4`() = runTest {
        // local=0.8, 2 pairs à 0.0 → networkScore = (0.8 + 0.0 + 0.0) / 3 ≈ 0.267 → K=2
        coEvery { trustScoreProvider.getScore() } returns 0.8f
        every { peerRepository.peers } returns MutableStateFlow(
            makePeers(0.0f, 0.0f)
        )
        val params = useCase()
        assertEquals("K=2 avec pairs peu fiables", 2, params.k)
        assertEquals("N=4 avec pairs peu fiables", 4, params.n)
    }

    @Test
    fun `sans pairs actifs le score local est utilisé seul`() = runTest {
        coEvery { trustScoreProvider.getScore() } returns 0.8f
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        val params = useCase()
        assertEquals(4, params.k)
        assertEquals(2, params.n)
    }

    @Test
    fun `les pairs inactifs sont exclus du calcul réseau`() = runTest {
        // local=0.8, 1 pair inactif à 0.0 → isActive=false donc ignoré → networkScore=0.8 → K=4
        coEvery { trustScoreProvider.getScore() } returns 0.8f
        val inactivePeer = Peer(
            identity = NodeIdentity("n0", ByteArray(65), reliabilityScore = 0.0f),
            lastSeenTimestampMs = System.currentTimeMillis(),
            isActive = false
        )
        every { peerRepository.peers } returns MutableStateFlow(listOf(inactivePeer))
        val params = useCase()
        assertEquals("pair inactif ignoré → K=4", 4, params.k)
        assertEquals("pair inactif ignoré → N=2", 2, params.n)
    }

    // --- Helpers ---

    private fun makePeers(vararg scores: Float): List<Peer> =
        scores.mapIndexed { idx, score ->
            Peer(
                identity = NodeIdentity(
                    nodeId = "node$idx",
                    publicKeyBytes = ByteArray(65),
                    reliabilityScore = score
                ),
                lastSeenTimestampMs = System.currentTimeMillis(),
                isActive = true
            )
        }
}
