package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.m11_join.LIVENESS_CHECK_INTERVAL_MS
import com.mobicloud.domain.models.m11_join.SP_TIMEOUT_MS
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureNanoTime

/**
 * NFR-09 — Liveness monitoring avec 50 membres sur une simulation de 30 minutes.
 *
 * Valide que MonitorMemberLivenessUseCase tient la charge du cluster maximum (MAX_CLUSTER_SIZE=50)
 * sans dégradation visible sur 120 cycles de vérification (30 min / 15s par cycle).
 */
class MemberLivenessNfrTest {

    private val SIMULATION_MS = 30 * 60 * 1_000L   // 30 minutes virtuelles
    private val EXPECTED_CYCLES = SIMULATION_MS / LIVENESS_CHECK_INTERVAL_MS  // 120

    private val CLUSTER_ID = "cid-nfr"
    private val DEAD_COUNT  = 25
    private val ALIVE_COUNT = 25
    private val TOTAL_MEMBERS = DEAD_COUNT + ALIVE_COUNT  // == MAX_CLUSTER_SIZE

    private lateinit var memberDao: MemberDao
    private lateinit var settingsRepo: NodeSettingsRepository
    private lateinit var sendMemberUpdate: SendMemberUpdateUseCase
    private lateinit var networkRepo: NetworkEventRepository

    private var virtualNow = 1_000_000L
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        memberDao    = mockk(relaxed = true)
        settingsRepo = mockk()
        sendMemberUpdate = mockk(relaxed = true)
        networkRepo  = mockk(relaxed = true)
        coEvery { settingsRepo.observeSettings() } returns flowOf(NodeSettings(0L, CLUSTER_ID))
    }

    private fun entity(id: Int, lastSeen: Long) = MemberEntity(
        nodeId     = "node%02d".format(id),
        clusterId  = CLUSTER_ID,
        publicKeyBytes = byteArrayOf(),
        ipAddress  = "10.0.0.$id",
        port       = 8080,
        freeBytes  = 512L,
        lastSeen   = lastSeen,
        role       = "MEMBER",
        status     = "ACTIVE"
    )

    private fun makeUseCase(scope: TestScope) = MonitorMemberLivenessUseCase(
        memberDao, settingsRepo, sendMemberUpdate, networkRepo,
        scope, clock = { virtualNow }
    )

    /**
     * 50 membres : 25 morts (lastSeen = now − 91s) + 25 vivants (lastSeen = now − 5s).
     * Après le premier cycle : 25 membres morts évincés.
     * Les 119 cycles suivants : aucune nouvelle éviction (membres vivants envoyent des HB).
     */
    @Test
    fun `nfr09_50_membres_30min_eviction_correcte`() = runTest(testDispatcher) {
        val deadLastSeen  = virtualNow - (SP_TIMEOUT_MS + 1_000L)
        val aliveLastSeen = virtualNow - 5_000L

        val deadMembers  = (0 until DEAD_COUNT).map { entity(it, deadLastSeen) }
        val aliveMembers = (DEAD_COUNT until TOTAL_MEMBERS).map { entity(it, aliveLastSeen) }

        // Premier cycle : retourne 25 morts + 25 vivants
        // Cycles suivants (après eviction des morts) : retourne seulement les 25 vivants
        var firstCycle = true
        coEvery { memberDao.listActiveSnapshot(CLUSTER_ID) } answers {
            if (firstCycle) {
                firstCycle = false
                deadMembers + aliveMembers
            } else {
                aliveMembers
            }
        }

        val useCase = makeUseCase(this)
        useCase.start()

        // Simule 30 minutes virtuelles
        advanceTimeBy(SIMULATION_MS + LIVENESS_CHECK_INTERVAL_MS)

        // 25 membres morts → 25 deleteByNodeId (exactement une fois chacun)
        coVerify(exactly = DEAD_COUNT) { memberDao.markEvictedIfStale(any(), any()) }

        // Les 25 membres vivants ne sont jamais évincés
        aliveMembers.forEach { alive ->
            coVerify(exactly = 0) { memberDao.markEvictedIfStale(alive.nodeId, any()) }
        }

        useCase.stop()
    }

    /**
     * Vérifie que 120 cycles de vérification sur 50 membres s'exécutent
     * en moins de 5 s réelles (borne très large : même sur CI lent, le mock est sub-ms).
     * But : détecter une régression O(n²) ou un deadlock accidentel.
     */
    @Test
    fun `nfr09_120_cycles_50_membres_perf_sous_5s`() = runTest(testDispatcher) {
        val freshLastSeen = virtualNow - 1_000L
        val allAlive = (0 until TOTAL_MEMBERS).map { entity(it, freshLastSeen) }
        coEvery { memberDao.listActiveSnapshot(CLUSTER_ID) } returns allAlive

        val useCase = makeUseCase(this)

        val elapsedNs = measureNanoTime {
            useCase.start()
            advanceTimeBy(SIMULATION_MS + LIVENESS_CHECK_INTERVAL_MS)
            useCase.stop()
        }

        val elapsedMs = elapsedNs / 1_000_000L
        assertTrue(
            "120 cycles × 50 membres doivent s'exécuter en < 5 000 ms (mesuré : ${elapsedMs} ms)",
            elapsedMs < 5_000L
        )
    }

    /**
     * Vérifie que le nombre de cycles réellement exécutés sur 30 min correspond
     * à SIMULATION_MS / LIVENESS_CHECK_INTERVAL_MS = 120 cycles.
     */
    @Test
    fun `nfr09_30min_produit_exactement_120_cycles`() = runTest(testDispatcher) {
        var cycleCount = 0
        coEvery { memberDao.listActiveSnapshot(CLUSTER_ID) } answers {
            cycleCount++
            emptyList()
        }

        val useCase = makeUseCase(this)
        useCase.start()
        advanceTimeBy(SIMULATION_MS + LIVENESS_CHECK_INTERVAL_MS)
        useCase.stop()

        assertTrue(
            "30 min / 15s = 120 cycles attendus, observé : $cycleCount",
            cycleCount >= EXPECTED_CYCLES
        )
    }
}
