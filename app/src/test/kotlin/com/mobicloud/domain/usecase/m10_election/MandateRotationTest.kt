package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Mandat tournant borné (réponse à R2-3). */
class MandateRotationTest {

    private fun member(hexId: String, role: MemberRole) = MemberInfo(
        nodeId = hexId.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        publicKey = ByteArray(0),
        ipAddress = "10.0.0.1",
        port = 9090,
        freeBytes = 0L,
        role = role
    )

    // ── Politique ───────────────────────────────────────────────────────────

    @Test
    fun `no successor when the super-pair is alone in its cluster`() {
        val members = listOf(member("aa01", MemberRole.SUPER_PAIR))
        assertFalse(MandateRotationPolicy.hasEligibleSuccessor(members, setOf("aa01")))
    }

    @Test
    fun `no successor when every member is offline`() {
        val members = listOf(member("aa01", MemberRole.SUPER_PAIR), member("bb02", MemberRole.MEMBER))
        assertFalse(MandateRotationPolicy.hasEligibleSuccessor(members, setOf("aa01")))
    }

    @Test
    fun `an active member is an eligible successor, case-insensitive`() {
        val members = listOf(member("aa01", MemberRole.SUPER_PAIR), member("bb02", MemberRole.MEMBER))
        assertTrue(MandateRotationPolicy.hasEligibleSuccessor(members, setOf("BB02")))
    }

    @Test
    fun `yield outlasts a normal election and spans two mandates`() {
        // Fenêtre de surveillance (6 s) + attente ALIVE (8 s) < délai de candidature en retrait
        assertTrue(MandateRotationPolicy.ROTATION_CANDIDACY_DELAY_MS > RunBullyElectionUseCase.MONITORING_WINDOW_MS + 8_000L)
        assertEquals(2 * MandateRotationPolicy.MANDATE_DURATION_MS, MandateRotationPolicy.ROTATION_YIELD_MS)
    }

    // ── État de retrait ─────────────────────────────────────────────────────

    @Test
    fun `rotation yield expires and survives a restart`() {
        val store = InMemoryTermStore()
        var now = 1_000L
        val m = ElectionStateManager(InMemoryCooldownStore(), store).apply { clockMs = { now } }
        m.startRotationYield(500L)
        assertTrue(m.isInRotationYield())

        val restarted = ElectionStateManager(InMemoryCooldownStore(), store).apply { clockMs = { now } }
        assertTrue(restarted.isInRotationYield())

        now = 1_600L
        assertFalse(restarted.isInRotationYield())
    }

    @Test
    fun `rotation yield does not erase the term state`() {
        val m = ElectionStateManager()
        m.beginMandate("c", 4)
        m.startRotationYield(1_000L)
        m.endMandate()
        assertEquals(4L, m.highestTerm("c"))
        assertTrue(m.isInRotationYield())
    }

    // ── Abdication ──────────────────────────────────────────────────────────

    private lateinit var securityRepository: SecurityRepository
    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var networkClient: IElectionNetworkClient

    @Before
    fun setUp() {
        securityRepository = mockk()
        trustScoreProvider = mockk()
        networkClient = mockk()
        coEvery { securityRepository.getIdentity() } returns Result.success(NodeIdentity("local", ByteArray(0), 1f))
        coEvery { securityRepository.signData(any()) } returns Result.success(ByteArray(0))
        coEvery { trustScoreProvider.getTrustScore(any()) } returns 1
    }

    @Test
    fun `rotation abdication uses the soft yield instead of the hard cooldown`() = runTest {
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)
        val state = ElectionStateManager().apply { beginMandate("c", 2) }

        val result = AbdicateSuperPeerUseCase(securityRepository, trustScoreProvider, networkClient, state)(rotation = true)

        assertTrue(result.isSuccess)
        assertTrue(state.isInRotationYield())
        assertFalse("pas de cooldown strict : le nœud doit pouvoir reprendre le rôle s'il est seul", state.isInCooldown())
        assertEquals(0L, state.ownTerm("c"))
        coVerify(exactly = 1) {
            networkClient.broadcastElectionMessage(match { it.type == ElectionMessageType.ABDICATION })
        }
    }

    @Test
    fun `rotation yield applies even when the abdication broadcast fails`() = runTest {
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.failure(RuntimeException("relay down"))
        val state = ElectionStateManager()

        AbdicateSuperPeerUseCase(securityRepository, trustScoreProvider, networkClient, state)(rotation = true)

        assertTrue(state.isInRotationYield())
    }

    @Test
    fun `manual abdication keeps the hard cooldown`() = runTest {
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)
        val state = ElectionStateManager()

        AbdicateSuperPeerUseCase(securityRepository, trustScoreProvider, networkClient, state)()

        assertTrue(state.isInCooldown())
        assertFalse(state.isInRotationYield())
    }
}
