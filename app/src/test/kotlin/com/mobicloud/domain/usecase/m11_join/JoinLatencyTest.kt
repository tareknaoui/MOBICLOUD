package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de latence NFR-08 (AC14).
 *
 * LAN simulé : réponse instantanée (stub direct) → p95 ≤ 2 000 ms
 * Relais HA simulé : délai 100 ms RTT → p95 ≤ 5 000 ms
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinLatencyTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val spHint = SuperPeerHint(
        nodeId = byteArrayOf(0xAA.toByte()),
        ipAddress = "1.2.3.4", port = 5000, reliabilityScore = 0.9f
    )
    private val identity = NodeIdentity("aabb", byteArrayOf(0xAA.toByte(), 0xBB.toByte()), 0.8f)
    private val acceptResponse = JoinResponse.JoinAccept(
        clusterId = "cluster-nfr08",
        superPairNodeId = byteArrayOf(0xAA.toByte()),
        memberSnapshot = emptyList(),
        timestampMs = 1L,
        signatureBytes = byteArrayOf(1)
    )

    private fun buildUseCase(delayMs: Long): SendJoinRequestUseCase {
        val secRepo = mockk<SecurityRepository>()
        val settingsRepo = mockk<NodeSettingsRepository>(relaxed = true)
        val eventRepo = mockk<NetworkEventRepository>(relaxed = true)
        val fsm = mockk<JoinStateMachine>(relaxed = true)
        val networkClient = mockk<IJoinNetworkClient>()

        coEvery { secRepo.getIdentity() } returns Result.success(identity)
        coEvery { secRepo.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        every { settingsRepo.observeFreeSpaceBytes() } returns emptyFlow()
        coEvery { settingsRepo.getClusterIdOnce() } returns ""
        every { fsm.currentState } returns MutableStateFlow(NodeJoinState.Undiscovered)

        coEvery { networkClient.sendJoinRequest(any(), any()) } coAnswers {
            delay(delayMs)
            Result.success(acceptResponse)
        }

        return SendJoinRequestUseCase(networkClient, secRepo, settingsRepo, eventRepo, fsm)
    }

    @Test
    fun `LAN stub direct - p95 joinLatencyMs inferieur a 2000 ms sur 20 iterations`() = runTest(dispatcher) {
        val useCase = buildUseCase(delayMs = 0L)
        val latencies = mutableListOf<Long>()

        repeat(20) {
            val result = useCase.invoke(listOf(spHint)).first()
            assertTrue("Itération $it doit réussir", result.isSuccess)
            latencies.add(result.getOrNull()!!.joinLatencyMs)
        }

        val p95 = latencies.sorted()[18]
        assertTrue("LAN p95=$p95 ms doit être ≤ 2000 ms", p95 <= 2_000L)
    }

    @Test
    fun `Relais HA stub avec delai 100ms RTT - p95 joinLatencyMs inferieur a 5000 ms`() = runTest(dispatcher) {
        val useCase = buildUseCase(delayMs = 100L)
        val latencies = mutableListOf<Long>()

        repeat(20) {
            val result = useCase.invoke(listOf(spHint)).first()
            assertTrue("Itération $it doit réussir", result.isSuccess)
            latencies.add(result.getOrNull()!!.joinLatencyMs)
        }

        val p95 = latencies.sorted()[18]
        assertTrue("HA Relay p95=$p95 ms doit être ≤ 5000 ms", p95 <= 5_000L)
    }
}
