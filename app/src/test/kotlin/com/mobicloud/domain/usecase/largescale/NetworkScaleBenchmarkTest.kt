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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Benchmark de scalabilité réseau MobiCloud.
 *
 * Mesure le temps de distribution de fichiers selon :
 *   - Le nombre de nœuds dans le cluster (scalabilité horizontale)
 *   - Le taux de panne des nœuds (résilience)
 *   - Le nombre de clusters disponibles (fédération inter-cluster)
 *
 * Résultats exportés en CSV dans app/build/reports/benchmark/
 * pour génération de graphes avec le script plot_benchmark.py
 */
class NetworkScaleBenchmarkTest {

    private lateinit var peerRepository: PeerRepository
    private lateinit var blockSender: BlockSender
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var gossipSyncUseCase: GossipSyncUseCase
    private lateinit var securityRepository: SecurityRepository
    private lateinit var insertDhtEntryUseCase: InsertDhtEntryUseCase
    private lateinit var signalingRepository: SignalingRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var useCase: DistributeEncryptedBlocksUseCase

    private val peersFlow = MutableStateFlow<List<RelayPeer>>(emptyList())
    private val outputDir = File("build/reports/benchmark").also { it.mkdirs() }

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

        useCase = DistributeEncryptedBlocksUseCase(
            peerRepository = peerRepository,
            blockSender = blockSender,
            catalogRepository = catalogRepository,
            gossipSyncUseCase = gossipSyncUseCase,
            securityRepository = securityRepository,
            insertDhtEntryUseCase = insertDhtEntryUseCase,
            requestInterClusterHostingUseCase = RequestInterClusterHostingUseCase(signalingRepository),
            nodeSettingsRepository = nodeSettingsRepository
        )

        coEvery { securityRepository.getIdentity() } returns
            Result.success(NodeIdentity("local-node", Random.nextBytes(65)))
        coEvery { catalogRepository.insertOwnerEntry(any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)
        coEvery { insertDhtEntryUseCase(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 100_000_000_000L,
            clusterId = "cluster-local-AAA"
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeNodes(count: Int): List<Peer> = (1..count).map { i ->
        Peer(
            identity = NodeIdentity("node-$i", Random.nextBytes(65)),
            lastSeenTimestampMs = System.currentTimeMillis(),
            source = DiscoverySource.REMOTE_FIREBASE,
            ipAddress = "10.0.${i / 256}.${i % 256}",
            port = 9000 + i,
            isActive = true
        )
    }

    private fun makeBundle(k: Int = 4, n: Int = 2): EncryptedBundle {
        val frags = (0 until k).map { i ->
            EncryptedFragment(i, false, Random.nextBytes(512), Random.nextBytes(12), 4096L)
        } + (k until k + n).map { i ->
            EncryptedFragment(i, true, Random.nextBytes(512), Random.nextBytes(12), 4096L)
        }
        return EncryptedBundle(
            encryptedFragments = frags,
            wrappedFileMasterKey = WrappedFileMasterKey(
                ephemeralPublicKeyBytes = Random.nextBytes(65),
                iv = Random.nextBytes(12),
                encryptedKey = Random.nextBytes(48)
            )
        )
    }

    private fun makeRemoteClusters(count: Int): List<RelayPeer> = (1..count).map { i ->
        RelayPeer(
            nodeId = "sp-cluster-$i",
            ip = "172.16.$i.1",
            port = 8000 + i,
            reliabilityScore = 0.9f,
            lastSeen = System.currentTimeMillis(),
            isSuperPair = true,
            clusterId = "cluster-remote-$i",
            freeBytes = 1_000_000_000L
        )
    }

    private fun writeCsv(filename: String, header: String, rows: List<String>) {
        File(outputDir, filename).writeText(buildString {
            appendLine(header)
            rows.forEach { appendLine(it) }
        })
        println("[BENCHMARK] CSV exporté : ${outputDir.absolutePath}/$filename")
    }

    // -------------------------------------------------------------------------
    // Benchmark 1 — Temps de distribution vs nombre de nœuds
    // -------------------------------------------------------------------------

    @Test
    fun `Benchmark 1 - temps distribution vs nombre de noeuds`() = runTest {
        val nodeCounts = listOf(1, 2, 5, 10, 20, 50, 100)
        val repetitions = 5
        val rows = mutableListOf<String>()

        println("\n=== BENCHMARK 1 : Temps de distribution vs Nombre de nœuds ===")
        println("%-12s %-12s %-12s %-12s".format("Nœuds", "Min(ms)", "Moy(ms)", "Max(ms)"))
        println("-".repeat(50))

        for (nodeCount in nodeCounts) {
            val nodes = makeNodes(nodeCount)
            every { peerRepository.peers } returns MutableStateFlow(nodes)
            coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
                val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
                Result.success(BlockAckMessage(msg.blockId, msg.blockId, "receiver", ByteArray(0)))
            }

            val times = mutableListOf<Long>()
            repeat(repetitions) { run ->
                val ms = measureTimeMillis {
                    useCase.distribute(makeBundle(), "hash-b1-$nodeCount-$run", ErasureParameters(4, 2))
                }
                times.add(ms)
            }

            val min = times.min()
            val avg = times.average().toLong()
            val max = times.max()
            rows.add("$nodeCount,$min,$avg,$max")
            println("%-12d %-12d %-12d %-12d".format(nodeCount, min, avg, max))
        }

        writeCsv(
            "benchmark1_nodes_vs_time.csv",
            "noeuds,min_ms,avg_ms,max_ms",
            rows
        )
    }

    // -------------------------------------------------------------------------
    // Benchmark 2 — Taux de succès vs taux de panne
    // -------------------------------------------------------------------------

    @Test
    fun `Benchmark 2 - taux de succes vs taux de panne`() = runTest {
        val failureRates = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90)
        val nodeCount = 20
        val repetitions = 10
        val rows = mutableListOf<String>()

        println("\n=== BENCHMARK 2 : Taux de succès vs Taux de panne ===")
        println("%-15s %-15s %-15s".format("Panne(%)", "Succès(%)", "Essais"))
        println("-".repeat(50))

        val nodes = makeNodes(nodeCount)
        every { peerRepository.peers } returns MutableStateFlow(nodes)

        for (failRate in failureRates) {
            var successCount = 0

            repeat(repetitions) { run ->
                var callIndex = 0
                coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
                    val idx = callIndex++
                    val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
                    if (Random.nextInt(100) < failRate) {
                        Result.failure(SocketTimeoutException("panne simulée $failRate%"))
                    } else {
                        Result.success(BlockAckMessage(msg.blockId, msg.blockId, "receiver", ByteArray(0)))
                    }
                }

                val result = useCase.distribute(makeBundle(), "hash-b2-$failRate-$run", ErasureParameters(4, 2))
                if (result.isSuccess) successCount++
            }

            val successPct = (successCount * 100) / repetitions
            rows.add("$failRate,$successPct,$repetitions")
            println("%-15d %-15d %-15d".format(failRate, successPct, repetitions))
        }

        writeCsv(
            "benchmark2_failure_vs_success.csv",
            "failure_rate_pct,success_rate_pct,repetitions",
            rows
        )
    }

    // -------------------------------------------------------------------------
    // Benchmark 3 — Distribution inter-cluster : impact du nombre de clusters
    // -------------------------------------------------------------------------

    @Test
    fun `Benchmark 3 - temps distribution vs nombre de clusters distants`() = runTest {
        val clusterCounts = listOf(0, 1, 2, 5, 10, 20)
        val repetitions = 5
        val rows = mutableListOf<String>()

        println("\n=== BENCHMARK 3 : Distribution inter-cluster vs Nombre de clusters ===")
        println("%-15s %-12s %-12s %-12s".format("Clusters", "Min(ms)", "Moy(ms)", "Max(ms)"))
        println("-".repeat(55))

        // Cluster local vide → tout passe par inter-cluster
        every { peerRepository.peers } returns MutableStateFlow(emptyList())

        for (clusterCount in clusterCounts) {
            peersFlow.value = makeRemoteClusters(clusterCount)

            coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
                val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
                Result.success(BlockAckMessage(msg.blockId, msg.blockId, "remote-sp", ByteArray(0)))
            }

            val times = mutableListOf<Long>()
            repeat(repetitions) { run ->
                val ms = measureTimeMillis {
                    useCase.distribute(makeBundle(), "hash-b3-$clusterCount-$run", ErasureParameters(4, 2))
                }
                times.add(ms)
            }

            val min = if (times.isEmpty()) 0L else times.min()
            val avg = if (times.isEmpty()) 0L else times.average().toLong()
            val max = if (times.isEmpty()) 0L else times.max()
            rows.add("$clusterCount,$min,$avg,$max")
            println("%-15d %-12d %-12d %-12d".format(clusterCount, min, avg, max))
        }

        writeCsv(
            "benchmark3_clusters_vs_time.csv",
            "clusters_distants,min_ms,avg_ms,max_ms",
            rows
        )
    }
}
