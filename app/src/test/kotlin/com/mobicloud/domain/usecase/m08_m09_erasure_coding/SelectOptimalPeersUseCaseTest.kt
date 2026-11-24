package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.PeerRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelectOptimalPeersUseCaseTest {

    private lateinit var peerRepository: PeerRepository
    private lateinit var useCase: SelectOptimalPeersUseCase

    // Fichier de 10 Mo → fragmentSize ≈ 2.5 Mo (baseK=4)
    private val FILE_10_MB = 10L * 1024 * 1024
    private val MARGIN_100_MB = 100L * 1024 * 1024

    @Before
    fun setUp() {
        peerRepository = mockk()
        useCase = SelectOptimalPeersUseCase(peerRepository)
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /** Crée un pair avec un score et un espace libre donnés. */
    private fun makePeer(
        id: String,
        score: Float = 0.9f,
        freeBytes: Long = MARGIN_100_MB + 10_000_000L,  // bien au-dessus du seuil par défaut
        active: Boolean = true
    ) = Peer(
        identity = NodeIdentity(id, ByteArray(65), reliabilityScore = score),
        lastSeenTimestampMs = System.currentTimeMillis(),
        source = DiscoverySource.LAN_MULTICAST,
        ipAddress = "192.168.1.1",
        port = 9000,
        isActive = active,
        freeStorageBytes = freeBytes
    )

    // ─── D3 : guard baseK <= 0 ───────────────────────────────────────────────

    @Test
    fun `baseK = 0 retourne InvalidBaseK`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        val result = useCase(FILE_10_MB, baseK = 0)
        assertTrue(result.isFailure)
        assertTrue(
            "Attendu InvalidBaseK, reçu ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is PeerSelectionException.InvalidBaseK
        )
    }

    @Test
    fun `baseK négatif retourne InvalidBaseK`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        val result = useCase(FILE_10_MB, baseK = -1)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeerSelectionException.InvalidBaseK)
    }

    // ─── D1 : timeout sur peers.first() ─────────────────────────────────────

    @Test
    fun `flow qui n émet jamais retourne PeerFlowTimeout`() = runTest {
        // StateFlow mocké dont collect() suspend indéfiniment → withTimeout(5s) expire
        val neverEmitting = mockk<kotlinx.coroutines.flow.StateFlow<List<Peer>>>()
        every { neverEmitting.replayCache } returns emptyList()
        every { neverEmitting.value } returns emptyList()
        io.mockk.coEvery { neverEmitting.collect(any()) } coAnswers {
            kotlinx.coroutines.suspendCancellableCoroutine<Nothing> { }  // suspend pour toujours
        }
        every { peerRepository.peers } returns neverEmitting
        val result = useCase(FILE_10_MB)
        assertTrue(result.isFailure)
        assertTrue(
            "Attendu PeerFlowTimeout, reçu ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is PeerSelectionException.PeerFlowTimeout
        )
    }

    // ─── T2 : anciens peers (freeStorageBytes=0) inclus ─────────────────────

    @Test
    fun `pairs avec freeStorageBytes = 0 sont inclus comme capacite inconnue`() = runTest {
        // 4 pairs anciens (freeStorageBytes=0) + baseK=4 → doit fonctionner
        val legacyPeers = (1..5).map { makePeer("legacy_$it", freeBytes = 0L) }
        every { peerRepository.peers } returns MutableStateFlow(legacyPeers)

        val result = useCase(FILE_10_MB, baseK = 4)
        assertTrue("Les anciens pairs avec freeStorageBytes=0 doivent être inclus", result.isSuccess)
        val selected = result.getOrThrow().selectedPeers
        assertTrue("Au moins 4 pairs sélectionnés", selected.size >= 4)
    }

    // ─── T3 : finalN = 0 interdit ───────────────────────────────────────────

    @Test
    fun `exactement baseK pairs capables retourne InsufficientRedundancyNodes`() = runTest {
        // 4 pairs capables, baseK=4 → availableNodes == baseK → aucune redondance possible
        val fourPeers = (1..4).map { makePeer("peer_$it") }
        every { peerRepository.peers } returns MutableStateFlow(fourPeers)

        val result = useCase(FILE_10_MB, baseK = 4)
        assertTrue(result.isFailure)
        assertTrue(
            "Attendu InsufficientRedundancyNodes, reçu ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is PeerSelectionException.InsufficientRedundancyNodes
        )
    }

    // ─── T4 (D4) : exceptions typées InsufficientCapableNodes ───────────────

    @Test
    fun `moins de baseK pairs capables retourne InsufficientCapableNodes`() = runTest {
        // 2 pairs avec suffisamment d'espace, baseK=4 → pas assez de nœuds capables
        val twoPeers = (1..2).map { makePeer("peer_$it") }
        every { peerRepository.peers } returns MutableStateFlow(twoPeers)

        val result = useCase(FILE_10_MB, baseK = 4)
        assertTrue(result.isFailure)
        assertTrue(
            "Attendu InsufficientCapableNodes, reçu ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is PeerSelectionException.InsufficientCapableNodes
        )
    }

    @Test
    fun `pairs avec espace insuffisant sont exclus du comptage`() = runTest {
        // 3 pairs avec espace ok, 3 pairs avec espace insuffisant (trop petit) → seuls 3 qualifient < 4 requis
        val goodPeers = (1..3).map { makePeer("good_$it", freeBytes = MARGIN_100_MB + 5_000_000L) }
        val badPeers = (1..3).map { makePeer("bad_$it", freeBytes = 1024L) }  // 1 Ko → insuffisant
        every { peerRepository.peers } returns MutableStateFlow(goodPeers + badPeers)

        val result = useCase(FILE_10_MB, baseK = 4)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeerSelectionException.InsufficientCapableNodes)
    }

    // ─── Happy paths ────────────────────────────────────────────────────────

    @Test
    fun `happy path - assez de pairs fiables atteint la cible avec le plus petit N`() = runTest {
        // p = 0.9, K = 4 : A(N=3) = 0.99727 < 0.999 <= A(N=4) = 0.99957 → N = 4
        val peers = (1..20).map { makePeer("peer_$it", score = 0.9f) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val r = useCase(FILE_10_MB, baseK = 4).getOrThrow()
        assertEquals("K doit être 4", 4, r.params.k)
        assertEquals("plus petit N tel que A >= 0.999", 4, r.params.n)
        assertEquals("8 pairs sélectionnés (K=4 + N=4)", 8, r.selectedPeers.size)
        assertTrue(r.availabilityTargetMet)
        assertTrue(r.estimatedAvailability!! >= 0.999)
    }

    @Test
    fun `l ancien seuil N=1 pour score 0_9 ne donnait que 92 pourcent`() = runTest {
        // 5 pairs à 0.9 : seul N = 1 est possible → meilleur effort, cible non atteinte
        val peers = (1..5).map { makePeer("peer_$it", score = 0.9f) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val r = useCase(FILE_10_MB, baseK = 4).getOrThrow()
        assertEquals(1, r.params.n)
        assertFalse(r.availabilityTargetMet)
        assertEquals(0.91854, r.estimatedAvailability!!, 1e-5)
    }

    @Test
    fun `réseau moyen - redondance au maximum des pairs présents`() = runTest {
        // 7 pairs à 0.6 : la cible exigerait bien plus de 3 parités → N = 7 - 4 = 3
        val peers = (1..7).map { makePeer("peer_$it", score = 0.6f) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val r = useCase(FILE_10_MB, baseK = 4).getOrThrow()
        assertEquals(3, r.params.n)
        assertEquals(7, r.selectedPeers.size)
        assertFalse(r.availabilityTargetMet)
    }

    @Test
    fun `réseau instable - surcoût plafonné à une réplication x3`() = runTest {
        // 30 pairs à 0.3 : la cible est hors d'atteinte → N plafonné à 2K = 8
        val peers = (1..30).map { makePeer("peer_$it", score = 0.3f) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val r = useCase(FILE_10_MB, baseK = 4).getOrThrow()
        assertEquals(8, r.params.n)
        assertEquals(12, r.selectedPeers.size)
        assertFalse(r.availabilityTargetMet)
    }

    @Test
    fun `profil démo 3 téléphones - RS(2,1)`() = runTest {
        val peers = (1..3).map { makePeer("peer_$it", score = 0.9f) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val r = useCase(FILE_10_MB, baseK = 2).getOrThrow()
        assertEquals(2, r.params.k)
        assertEquals(1, r.params.n)
        assertEquals(3, r.selectedPeers.size)
    }

    @Test
    fun `mode duplication - parité calculée sur la répartition round-robin`() = runTest {
        val peers = (1..2).map { makePeer("peer_$it", score = 0.9f) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val r = useCase(FILE_10_MB, baseK = 2, allowDuplicatePeers = true).getOrThrow()
        // p = 0.9, K = 2 : A(1) = 0.972, A(2) = 0.9963, A(3) = 0.99954 → N = 3
        assertEquals(3, r.params.n)
        assertEquals(5, r.selectedPeers.size)
    }

    @Test
    fun `les pairs inactifs sont exclus du pool`() = runTest {
        val activePeers = (1..5).map { makePeer("active_$it", active = true) }
        val inactivePeers = (1..3).map { makePeer("inactive_$it", active = false) }
        every { peerRepository.peers } returns MutableStateFlow(activePeers + inactivePeers)

        val result = useCase(FILE_10_MB, baseK = 4)
        assertTrue(result.isSuccess)
        // Aucun pair inactif dans la sélection finale
        val selectedIds = result.getOrThrow().selectedPeers.map { it.identity.nodeId }
        assertTrue(
            "Aucun pair inactif ne doit être sélectionné",
            selectedIds.none { it.startsWith("inactive_") }
        )
    }

    @Test
    fun `redondance réduite si pas assez de pairs pour dynamicN complet`() = runTest {
        // 5 pairs, baseK=4 → availableNodes=5, totalRequired=4+1=5 → finalN=1 (pile juste)
        val peers = (1..5).map { makePeer("peer_$it", score = 0.9f) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val result = useCase(FILE_10_MB, baseK = 4)
        assertTrue(result.isSuccess)
        // Doit réussir avec la redondance minimale (N=1 car dynamicN=1 exactement disponible)
        assertTrue("finalN doit être >= 1", result.getOrThrow().params.n >= 1)
    }

    @Test
    fun `sélection triée par score de fiabilité décroissant`() = runTest {
        // Peers avec des scores variés → les meilleurs doivent être sélectionnés en premier
        val peers = listOf(
            makePeer("low_score", score = 0.1f),
            makePeer("high_score_1", score = 0.99f),
            makePeer("mid_score", score = 0.5f),
            makePeer("high_score_2", score = 0.95f),
            makePeer("very_low", score = 0.05f),
            makePeer("high_score_3", score = 0.98f),
        )
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val result = useCase(FILE_10_MB, baseK = 4)
        assertTrue(result.isSuccess)
        val selected = result.getOrThrow().selectedPeers
        // Les pairs sélectionnés doivent être triés par score décroissant
        val scores = selected.map { it.identity.reliabilityScore }
        assertEquals("Les scores doivent être triés par ordre décroissant", scores.sortedDescending(), scores)
        // Les 3 "high_score" doivent être dans la sélection
        assertTrue(selected.any { it.identity.nodeId == "high_score_1" })
        assertTrue(selected.any { it.identity.nodeId == "high_score_2" })
        assertTrue(selected.any { it.identity.nodeId == "high_score_3" })
    }

    @Test
    fun `contrainte GF256 respectée - k + n doit être inférieur à 255`() = runTest {
        // Test sur tous les profils de redondance
        val peersForStable = (1..20).map { makePeer("peer_$it", score = 0.9f) }
        val peersForNormal = (1..20).map { makePeer("peer_$it", score = 0.6f) }
        val peersForUnstable = (1..20).map { makePeer("peer_$it", score = 0.3f) }

        for (peerList in listOf(peersForStable, peersForNormal, peersForUnstable)) {
            every { peerRepository.peers } returns MutableStateFlow(peerList)
            val result = useCase(FILE_10_MB, baseK = 4)
            assertTrue(result.isSuccess)
            val params = result.getOrThrow().params
            assertTrue("K+N=${params.k + params.n} doit être <= 255", params.k + params.n <= 255)
            assertNotNull("params ne doit pas être null", params)
        }
    }
}
