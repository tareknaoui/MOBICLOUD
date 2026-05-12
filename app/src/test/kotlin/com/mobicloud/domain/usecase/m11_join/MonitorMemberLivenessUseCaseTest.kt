package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.m11_join.LIVENESS_CHECK_INTERVAL_MS
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.SP_TIMEOUT_MS
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MonitorMemberLivenessUseCaseTest {

    private lateinit var memberDao: MemberDao
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var sendMemberUpdateUseCase: SendMemberUpdateUseCase
    private lateinit var networkEventRepository: NetworkEventRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private var virtualNow = 100_000L

    private fun entity(nodeId: String, lastSeen: Long) = MemberEntity(
        nodeId, "cid", byteArrayOf(), "", 0, 0L, lastSeen, "MEMBER", "ACTIVE"
    )

    @Before
    fun setUp() {
        memberDao = mockk(relaxed = true)
        nodeSettingsRepository = mockk()
        sendMemberUpdateUseCase = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        coEvery { nodeSettingsRepository.observeSettings() } returns flowOf(NodeSettings(0L, "cid"))
    }

    private fun makeUseCase(scope: TestScope): MonitorMemberLivenessUseCase =
        MonitorMemberLivenessUseCase(
            memberDao, nodeSettingsRepository, sendMemberUpdateUseCase,
            networkEventRepository, scope, clock = { virtualNow }
        )

    // 1. Aucun membre dépassé → 0 eviction
    @Test
    fun `aucun membre depasse zero eviction`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        val freshSeen = virtualNow - 5_000L
        coEvery { memberDao.listActiveSnapshot("cid") } returns listOf(entity("aa", freshSeen))

        useCase.start()
        advanceTimeBy(LIVENESS_CHECK_INTERVAL_MS + 1)

        coVerify(exactly = 0) { memberDao.markEvictedIfStale(any(), any()) }
        useCase.stop()
    }

    // 2. Membre A lastSeen = now - 91s → 1 eviction + 1 MEMBER_UPDATE LEFT
    @Test
    fun `membre depasse 91s provoque eviction et MEMBER_UPDATE`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        val deadSeen = virtualNow - (SP_TIMEOUT_MS + 1_000L)
        coEvery { memberDao.listActiveSnapshot("cid") } returns listOf(entity("dead", deadSeen))
        coEvery { memberDao.markEvictedIfStale(any(), any()) } returns 1

        useCase.start()
        advanceTimeBy(LIVENESS_CHECK_INTERVAL_MS + 1)

        coVerify(exactly = 1) { memberDao.markEvictedIfStale("dead", any()) }
        coVerify(exactly = 1) { sendMemberUpdateUseCase.invoke(any<MemberUpdate>()) }
        useCase.stop()
    }

    // 3. Membre A lastSeen = now - 89s → 0 eviction (juste sous le seuil)
    @Test
    fun `membre 89s sous le seuil aucune eviction`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        val almostDead = virtualNow - (SP_TIMEOUT_MS - 1_000L)
        coEvery { memberDao.listActiveSnapshot("cid") } returns listOf(entity("alive", almostDead))

        useCase.start()
        advanceTimeBy(LIVENESS_CHECK_INTERVAL_MS + 1)

        coVerify(exactly = 0) { memberDao.markEvictedIfStale(any(), any()) }
        useCase.stop()
    }

    // 4. start() × 2 → idempotent (un seul job)
    @Test
    fun `start deux fois idempotent`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        coEvery { memberDao.listActiveSnapshot("cid") } returns emptyList()

        useCase.start()
        val firstJob = useCase.monitorJob
        useCase.start()
        assertTrue(firstJob === useCase.monitorJob)
        useCase.stop()
    }

    // 5. stop() puis start() → nouveau job
    @Test
    fun `stop puis start cree un nouveau job`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        coEvery { memberDao.listActiveSnapshot("cid") } returns emptyList()

        useCase.start()
        val firstJob = useCase.monitorJob
        useCase.stop()
        useCase.start()
        assertTrue(firstJob !== useCase.monitorJob)
        useCase.stop()
    }
}
