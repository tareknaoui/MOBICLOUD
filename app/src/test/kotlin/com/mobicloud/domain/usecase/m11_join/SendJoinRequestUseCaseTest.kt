package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinMetrics
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendJoinRequestUseCaseTest {

    private lateinit var networkClient: IJoinNetworkClient
    private lateinit var securityRepository: SecurityRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var useCase: SendJoinRequestUseCase
    private val dispatcher = UnconfinedTestDispatcher()

    private val localIdentity = NodeIdentity("0102", byteArrayOf(0x01, 0x02), 0.8f)
    private val spHint = SuperPeerHint(byteArrayOf(0xAA.toByte()), clusterId = "alpha", ipAddress = "1.2.3.4", port = 5000, reliabilityScore = 0.9f, currentMemberCount = 1)

    private val acceptResponse = JoinResponse.JoinAccept(
        clusterId = "cluster-1",
        superPairNodeId = byteArrayOf(0xAA.toByte()),
        memberSnapshot = emptyList(),
        timestampMs = 100L,
        signatureBytes = byteArrayOf(0x01)
    )

    @Before
    fun setup() {
        networkClient = mockk()
        securityRepository = mockk()
        nodeSettingsRepository = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        joinStateMachine = mockk(relaxed = true)

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        coEvery { nodeSettingsRepository.getClusterIdOnce() } returns ""
        every { nodeSettingsRepository.observeFreeSpaceBytes() } returns emptyFlow()

        useCase = SendJoinRequestUseCase(
            networkClient, securityRepository,
            nodeSettingsRepository, networkEventRepository, joinStateMachine
        )
    }

    // Scénario 1 : JoinAccept → émet success avec JoinMetrics
    @Test
    fun `JoinAccept recu - emet success JoinMetrics et notifie stateMachine`() = runTest(dispatcher) {
        coEvery { networkClient.sendJoinRequest(any(), any()) } returns Result.success(acceptResponse)

        val result = useCase.invoke(listOf(spHint)).first()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!! is JoinMetrics)
        coVerify { joinStateMachine.transition(any<JoinEvent.JoinAcceptReceived>()) }
    }

    // Scénario 2 : JoinRedirect puis JoinAccept sur candidat alternatif
    @Test
    fun `JoinRedirect avec alternative puis Accept - converge en 2 tentatives`() = runTest(dispatcher) {
        val altHint = SuperPeerHint(byteArrayOf(0xBB.toByte()), clusterId = "beta", ipAddress = "2.3.4.5", port = 6000, reliabilityScore = 0.7f, currentMemberCount = 2)
        val redirect = JoinResponse.JoinRedirect(
            reason = JoinRedirectReason.CLUSTER_FULL,
            alternativeSuperPeers = listOf(altHint),
            timestampMs = 1L,
            signatureBytes = byteArrayOf(2)
        )

        coEvery { networkClient.sendJoinRequest(spHint, any()) } returns Result.success(redirect)
        coEvery { networkClient.sendJoinRequest(altHint, any()) } returns Result.success(acceptResponse)

        val result = useCase.invoke(listOf(spHint)).first()
        assertTrue(result.isSuccess)
    }

    // Scénario 3 : Tous les candidats épuisés → AllCandidatesExhausted
    @Test
    fun `tous les candidats timeout - emet failure et transition AllCandidatesExhausted`() = runTest(dispatcher) {
        coEvery { networkClient.sendJoinRequest(any(), any()) } returns Result.failure(Exception("timeout"))

        val result = useCase.invoke(listOf(spHint)).first()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is JoinExhaustedException)
        coVerify { joinStateMachine.transition(JoinEvent.AllCandidatesExhausted) }
    }

    // AC6 — sticky cluster : le candidat du dernier cluster connu est tenté en premier
    @Test
    fun `prefersStickyClusterFirst - sticky alpha tente avant les autres`() = runTest(dispatcher) {
        coEvery { nodeSettingsRepository.getClusterIdOnce() } returns "alpha"

        val beta  = SuperPeerHint(byteArrayOf(0xBB.toByte()), clusterId = "beta",  ipAddress = "2.2.2.2", port = 5000, currentMemberCount = 2)
        val alpha = SuperPeerHint(byteArrayOf(0xCC.toByte()), clusterId = "alpha", ipAddress = "3.3.3.3", port = 5000, currentMemberCount = 40)
        val gamma = SuperPeerHint(byteArrayOf(0xDD.toByte()), clusterId = "gamma", ipAddress = "4.4.4.4", port = 5000, currentMemberCount = 1)

        // ordre d'appel attendu : [alpha(sticky), gamma(count=1), beta(count=2)]
        val order = mutableListOf<ByteArray>()
        coEvery { networkClient.sendJoinRequest(any(), any()) } answers {
            order += firstArg<SuperPeerHint>().nodeId.copyOf()
            Result.failure(Exception("rejected"))
        }

        useCase.invoke(listOf(beta, alpha, gamma)).first()

        assertTrue("sticky alpha doit être premier", order.first().contentEquals(alpha.nodeId))
        assertTrue("gamma(count=1) doit être second", order.getOrNull(1)?.contentEquals(gamma.nodeId) == true)
        assertTrue("beta(count=2) doit être troisième", order.getOrNull(2)?.contentEquals(beta.nodeId) == true)
    }

    // AC6 — fallback load-based quand sticky unavailable
    @Test
    fun `fallsBackToLoadBased_whenStickyUnavailable - tri par memberCount si lastKnown absent`() = runTest(dispatcher) {
        coEvery { nodeSettingsRepository.getClusterIdOnce() } returns ""

        val high = SuperPeerHint(byteArrayOf(0xEE.toByte()), clusterId = "x", ipAddress = "1.1.1.1", port = 5000, currentMemberCount = 30)
        val low  = SuperPeerHint(byteArrayOf(0xFF.toByte()), clusterId = "y", ipAddress = "2.2.2.2", port = 5000, currentMemberCount = 5)

        var firstCalled: ByteArray? = null
        coEvery { networkClient.sendJoinRequest(any(), any()) } answers {
            if (firstCalled == null) firstCalled = firstArg<SuperPeerHint>().nodeId.copyOf()
            Result.failure(Exception("rejected"))
        }

        useCase.invoke(listOf(high, low)).first()

        assertTrue("le SP le moins chargé doit être tenté en premier", firstCalled?.contentEquals(low.nodeId) == true)
    }
}
