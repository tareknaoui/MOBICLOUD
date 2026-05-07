package com.mobicloud.domain.usecase.largescale

import android.util.Log
import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.models.WrappedFileMasterKey
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.InsertDhtEntryUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.RequestInterClusterHostingUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Tests à grande échelle de la simulation réseau MobiCloud.
 *
 * Ces tests simulent des topologies avec plusieurs clusters et de nombreux nœuds
 * en utilisant uniquement des fakes/mocks JVM — aucun appareil physique requis.
 *
 * Scénarios couverts :
 *   Scénario 1 — 20 nœuds, 1 cluster : distribution intra-cluster
 *   Scénario 2 — Cluster saturé (tous les pairs locaux échouent) → fallback inter-cluster
 *   Scénario 3 — 3 clusters distincts, fragments répartis entre clusters
 *   Scénario 4 — 10 fichiers stockés en séquence, vérification du catalogue
 *   Scénario 5 — Nœuds qui tombent pendant la distribution (churn)
 *   Scénario 6 — Relay HA down → inter-cluster impossible, dégradation gracieuse
 *   Scénario 7 — 1 seul nœud local + 2 clusters distants disponibles → meilleur cluster choisi
 *   Scénario 8 — k=2 n=1 (3 fragments) sur 3 nœuds : seuil minimum viable
 */
class LargeScaleNetworkSimulationTest {

    // -------------------------------------------------------------------------
    // Dépendances mockées
    // -------------------------------------------------------------------------

    private lateinit var peerRepository: PeerRepository
    private lateinit var blockSender: BlockSender
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var gossipSyncUseCase: GossipSyncUseCase
    private lateinit var securityRepository: SecurityRepository
    private lateinit var insertDhtEntryUseCase: InsertDhtEntryUseCase
    private lateinit var signalingRepository: SignalingRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var requestInterClusterHostingUseCase: RequestInterClusterHostingUseCase
    private lateinit var distributeUseCase: DistributeEncryptedBlocksUseCase

    private val localNodeId = "node-local-000"
    private val localClusterId = "cluster-AAA"
    private val peersFlow = MutableStateFlow<List<RelayPeer>>(emptyList())

    // -------------------------------------------------------------------------
    // Setup / Teardown
    // -------------------------------------------------------------------------

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        peerRepository = mockk()
        blockSender = mockk()
        catalogRepository = mockk()
        gossipSyncUseCase = mockk()
        securityRepository = mockk()
        insertDhtEntryUseCase = mockk()
        signalingRepository = mockk()
        nodeSettingsRepository = mockk()

        every { signalingRepository.latestPeers } returns peersFlow
        requestInterClusterHostingUseCase = RequestInterClusterHostingUseCase(signalingRepository)

        distributeUseCase = DistributeEncryptedBlocksUseCase(
            peerRepository = peerRepository,
            blockSender = blockSender,
            catalogRepository = catalogRepository,
            gossipSyncUseCase = gossipSyncUseCase,
            securityRepository = securityRepository,
            insertDhtEntryUseCase = insertDhtEntryUseCase,
            requestInterClusterHostingUseCase = requestInterClusterHostingUseCase,
            nodeSettingsRepository = nodeSettingsRepository
        )

        coEvery { securityRepository.getIdentity() } returns Result.success(
            NodeIdentity(localNodeId, Random.nextBytes(65))
        )
        coEvery { catalogRepository.insertOwnerEntry(any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)
        coEvery { insertDhtEntryUseCase(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 10_000_000_000L,
            clusterId = localClusterId
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // -------------------------------------------------------------------------
    // Builders de données de test
    // -------------------------------------------------------------------------

    /** Crée un nœud local actif avec une IP dans le sous-réseau 192.168.x.y */
    private fun localNode(index: Int, clusterId: String = localClusterId): Peer = Peer(
        identity = NodeIdentity("node-$clusterId-$index", Random.nextBytes(65)),
        lastSeenTimestampMs = System.currentTimeMillis(),
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = "192.168.1.$index",
        port = 9000 + index,
        isActive = true
    )

    /** Crée un super-pair distant (autre cluster) enregistré au Relay HA */
    private fun remoteClusterSuperPeer(
        clusterId: String,
        nodeId: String,
        ip: String,
        port: Int,
        freeBytes: Long = 500_000_000L
    ): RelayPeer = RelayPeer(
        nodeId = nodeId,
        ip = ip,
        port = port,
        reliabilityScore = 0.9f,
        lastSeen = System.currentTimeMillis(),
        isSuperPair = true,
        clusterId = clusterId,
        freeBytes = freeBytes
    )

    /** Crée un bundle erasure-coded avec k blocs données + n blocs parité */
    private fun bundle(k: Int = 4, n: Int = 2): EncryptedBundle {
        val fragments = (0 until k).map { i ->
            EncryptedFragment(i, false, Random.nextBytes(256), Random.nextBytes(12), 1024L)
        } + (k until k + n).map { i ->
            EncryptedFragment(i, true, Random.nextBytes(256), Random.nextBytes(12), 1024L)
        }
        return EncryptedBundle(
            encryptedFragments = fragments,
            wrappedFileMasterKey = WrappedFileMasterKey(
                ephemeralPublicKeyBytes = Random.nextBytes(65),
                iv = Random.nextBytes(12),
                encryptedKey = Random.nextBytes(48)
            )
        )
    }

    /** Simule un ACK réussi pour n'importe quel bloc */
    private fun ackFor(msg: com.mobicloud.domain.models.BlockTransferMessage, nodeId: String) =
        BlockAckMessage(msg.blockId, msg.blockId, nodeId, ByteArray(0))

    // -------------------------------------------------------------------------
    // Scénario 1 — 20 nœuds, 1 cluster : distribution intra-cluster
    // -------------------------------------------------------------------------

    /**
     * 20 nœuds actifs dans le cluster local.
     * Un fichier k=4 n=2 doit être distribué entièrement en intra-cluster.
     * Aucun appel inter-cluster ne doit être fait.
     */
    @Test
    fun `Scenario 1 - 20 noeuds un seul cluster distribution complete intra-cluster`() = runTest {
        val nodes = (1..20).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(ackFor(msg, "receiver"))
        }

        val result = distributeUseCase.distribute(bundle(k = 4, n = 2), "hash-scenario1", ErasureParameters(4, 2))

        assertTrue("Distribution doit réussir avec 20 nœuds", result.isSuccess)
        assertEquals("6 fragments doivent être placés", 6, result.getOrThrow().fragmentLocations.size)
        coVerify(exactly = 6) { blockSender.sendBlock(any(), any(), any()) }
        coVerify(exactly = 1) { catalogRepository.insertOwnerEntry(any()) }
    }

    // -------------------------------------------------------------------------
    // Scénario 2 — Cluster local saturé → fallback inter-cluster
    // -------------------------------------------------------------------------

    /**
     * 5 nœuds locaux qui échouent tous (disque plein simulé).
     * 1 super-pair distant disponible au Relay HA.
     * Tous les fragments doivent être placés sur le cluster distant.
     */
    @Test
    fun `Scenario 2 - cluster local sature fallback inter-cluster total`() = runTest {
        val nodes = (1..5).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        val remoteSP = remoteClusterSuperPeer("cluster-BBB", "sp-BBB", "10.0.1.1", 9100)
        peersFlow.value = listOf(remoteSP)

        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val targetPeer = secondArg<Peer>()
            if (targetPeer.ipAddress == "10.0.1.1") {
                val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
                Result.success(ackFor(msg, "sp-BBB"))
            } else {
                Result.failure(SocketTimeoutException("disque plein"))
            }
        }

        val result = distributeUseCase.distribute(bundle(k = 4, n = 2), "hash-scenario2", ErasureParameters(4, 2))

        assertTrue("Distribution doit réussir via inter-cluster", result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals("6 fragments placés sur cluster distant", 6, entry.fragmentLocations.size)
    }

    // -------------------------------------------------------------------------
    // Scénario 3 — 3 clusters, fragments répartis
    // -------------------------------------------------------------------------

    /**
     * Cluster local avec 2 nœuds opérationnels.
     * 2 clusters distants (BBB et CCC) disponibles au Relay.
     * Les fragments locaux vont en intra-cluster, les parity vont en inter-cluster.
     */
    @Test
    fun `Scenario 3 - 3 clusters fragments repartis local et distant`() = runTest {
        val nodes = (1..2).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        peersFlow.value = listOf(
            remoteClusterSuperPeer("cluster-BBB", "sp-BBB", "10.0.1.1", 9100, freeBytes = 1_000_000_000L),
            remoteClusterSuperPeer("cluster-CCC", "sp-CCC", "10.0.2.1", 9200, freeBytes = 500_000_000L)
        )

        var localCallCount = 0
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val targetPeer = secondArg<Peer>()
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            when (targetPeer.ipAddress) {
                "192.168.1.1", "192.168.1.2" -> {
                    localCallCount++
                    Result.success(ackFor(msg, targetPeer.identity.nodeId))
                }
                "10.0.1.1" -> Result.success(ackFor(msg, "sp-BBB"))
                else -> Result.failure(SocketTimeoutException("unreachable"))
            }
        }

        val result = distributeUseCase.distribute(bundle(k = 4, n = 2), "hash-scenario3", ErasureParameters(4, 2))

        assertTrue("Distribution doit réussir sur 3 clusters", result.isSuccess)
        // Les 2 nœuds locaux prennent des fragments, inter-cluster prend le reste
        assertTrue("Au moins 2 fragments placés localement", localCallCount >= 2)
    }

    // -------------------------------------------------------------------------
    // Scénario 4 — 10 fichiers stockés en séquence
    // -------------------------------------------------------------------------

    /**
     * 10 fichiers différents stockés l'un après l'autre sur le même cluster de 8 nœuds.
     * Tous doivent réussir indépendamment.
     * Vérifie que le catalogue reçoit bien 10 entrées distinctes.
     */
    @Test
    fun `Scenario 4 - 10 fichiers stockes en sequence sur 8 noeuds`() = runTest {
        val nodes = (1..8).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(ackFor(msg, "receiver"))
        }

        val hashes = (1..10).map { "file-hash-$it" }
        val results = hashes.map { hash ->
            distributeUseCase.distribute(bundle(k = 4, n = 2), hash, ErasureParameters(4, 2))
        }

        val successes = results.count { it.isSuccess }
        assertEquals("Les 10 fichiers doivent être stockés avec succès", 10, successes)
        coVerify(exactly = 10) { catalogRepository.insertOwnerEntry(any()) }
    }

    // -------------------------------------------------------------------------
    // Scénario 5 — Churn : nœuds qui tombent pendant la distribution
    // -------------------------------------------------------------------------

    /**
     * 6 nœuds locaux, mais 2 tombent pendant la distribution.
     * Les 2 nœuds qui tombent sont aux indices 4 et 5 (primaires pour les fragments de parité).
     * Le fallback niveau 2 (index 0) prend le relais pour les fragments de parité.
     * Les 4 blocs de données (fragments 0-3) ne sont pas affectés → k=4 confirmés → succès.
     */
    @Test
    fun `Scenario 5 - churn 2 noeuds tombent pendant la distribution`() = runTest {
        val nodes = (1..6).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        // Indices 4 et 5 dans activePeers → IPs 192.168.1.5 et 192.168.1.6
        // Ils sont primaires uniquement pour les fragments de parité (index 4 et 5 avec 6 nœuds)
        // Le fallback niveau 2 pointe vers index 0 (192.168.1.1) qui est opérationnel
        val failingIps = setOf("192.168.1.5", "192.168.1.6")

        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val targetPeer = secondArg<Peer>()
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            if (targetPeer.ipAddress in failingIps) {
                Result.failure(SocketTimeoutException("nœud tombé"))
            } else {
                Result.success(ackFor(msg, targetPeer.identity.nodeId))
            }
        }

        val result = distributeUseCase.distribute(bundle(k = 4, n = 2), "hash-scenario5", ErasureParameters(4, 2))

        assertTrue("Distribution doit réussir malgré 2 nœuds tombés (fallback niveau 2 actif)", result.isSuccess)
    }

    // -------------------------------------------------------------------------
    // Scénario 6 — Relay HA down : inter-cluster impossible, dégradation gracieuse
    // -------------------------------------------------------------------------

    /**
     * 0 nœud local + Relay HA down (latestPeers vide, pas de candidat inter-cluster).
     * La distribution doit échouer proprement avec IllegalStateException.
     * Aucune entrée catalogue ne doit être créée.
     */
    @Test
    fun `Scenario 6 - relay HA down cluster local vide degradation gracieuse`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        peersFlow.value = emptyList() // Relay down = aucun peer distant connu

        val result = distributeUseCase.distribute(bundle(k = 4, n = 2), "hash-scenario6", ErasureParameters(4, 2))

        assertTrue("Distribution doit échouer gracieusement", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { catalogRepository.insertOwnerEntry(any()) }
        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
    }

    /**
     * Relay HA était UP (super-pairs connus), puis tombe.
     * Cluster local de 3 nœuds : la distribution intra-cluster fonctionne encore.
     * L'inter-cluster est impossible mais l'intra-cluster suffit.
     */
    @Test
    fun `Scenario 6b - relay HA down mais cluster local suffisant`() = runTest {
        val nodes = (1..3).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)
        peersFlow.value = emptyList() // Relay down

        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(ackFor(msg, "local-receiver"))
        }

        val result = distributeUseCase.distribute(bundle(k = 2, n = 1), "hash-scenario6b", ErasureParameters(2, 1))

        assertTrue("Distribution locale doit réussir même si relay est down", result.isSuccess)
    }

    // -------------------------------------------------------------------------
    // Scénario 7 — 2 clusters distants, le plus libre est choisi
    // -------------------------------------------------------------------------

    /**
     * 1 seul nœud local qui échoue.
     * 2 super-pairs distants disponibles : BBB (100 MB libre) et CCC (900 MB libre).
     * CCC doit être sélectionné car il a plus d'espace.
     */
    @Test
    fun `Scenario 7 - 2 clusters distants le plus libre est selectionne`() = runTest {
        val nodes = listOf(localNode(1))
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        val spBBB = remoteClusterSuperPeer("cluster-BBB", "sp-BBB", "10.0.1.1", 9100, freeBytes = 100_000_000L)
        val spCCC = remoteClusterSuperPeer("cluster-CCC", "sp-CCC", "10.0.2.1", 9200, freeBytes = 900_000_000L)
        peersFlow.value = listOf(spBBB, spCCC)

        val usedIps = mutableListOf<String>()
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val targetPeer = secondArg<Peer>()
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            usedIps.add(targetPeer.ipAddress ?: "")
            if (targetPeer.ipAddress == "192.168.1.1") {
                Result.failure(SocketTimeoutException("local down"))
            } else {
                Result.success(ackFor(msg, targetPeer.identity.nodeId))
            }
        }

        val result = distributeUseCase.distribute(bundle(k = 4, n = 2), "hash-scenario7", ErasureParameters(4, 2))

        assertTrue("Distribution doit réussir via le cluster le plus libre", result.isSuccess)
        assertTrue(
            "sp-CCC (900MB) doit être préféré à sp-BBB (100MB)",
            usedIps.any { it == "10.0.2.1" }
        )
    }

    // -------------------------------------------------------------------------
    // Scénario 8 — k=2 n=1 : configuration minimale viable sur 3 nœuds
    // -------------------------------------------------------------------------

    /**
     * Configuration minimale : k=2 données + n=1 parité = 3 fragments.
     * 3 nœuds disponibles. Tous les fragments doivent être placés.
     */
    @Test
    fun `Scenario 8 - configuration minimale k2 n1 sur 3 noeuds`() = runTest {
        val nodes = (1..3).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(ackFor(msg, "receiver"))
        }

        val result = distributeUseCase.distribute(bundle(k = 2, n = 1), "hash-scenario8", ErasureParameters(2, 1))

        assertTrue("Distribution minimale k=2 n=1 doit réussir", result.isSuccess)
        assertEquals("3 fragments doivent être placés", 3, result.getOrThrow().fragmentLocations.size)
        coVerify(exactly = 3) { blockSender.sendBlock(any(), any(), any()) }
    }

    /**
     * Configuration minimale k=2 n=1, mais TOUS les nœuds locaux refusent les blocs
     * (simulation stockage plein) et pas de cluster distant disponible.
     * 0 blocs de données confirmés < k=2 → échec avec IllegalStateException.
     */
    @Test
    fun `Scenario 8b - k2 tous les noeuds echouent distribution partielle echec`() = runTest {
        val nodes = (1..2).map { localNode(it) }
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        // Tous les envois échouent — stockage plein sur les 2 nœuds
        coEvery { blockSender.sendBlock(any(), any(), any()) } returns
            Result.failure(SocketTimeoutException("stockage plein"))

        // Pas de cluster distant non plus
        peersFlow.value = emptyList()

        val result = distributeUseCase.distribute(bundle(k = 2, n = 1), "hash-scenario8b", ErasureParameters(2, 1))

        assertTrue("Distribution doit échouer si < k blocs de données confirmés", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { catalogRepository.insertOwnerEntry(any()) }
    }
}
