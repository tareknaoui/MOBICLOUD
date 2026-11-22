package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Split-brain signalé par le relais : l'annuaire détecte, le term décide.
 * Avant, le Super-Pair de plus petit nodeId abdiquait, même titulaire du mandat le plus récent.
 */
class ResolveRelaySuperPeerConflictUseCaseTest {

    private val cluster = "cluster-1"
    private val localId = "aaaa0001"
    private val higherRival = "ffff0002"
    private val lowerRival = "0000ffff"

    private lateinit var securityRepository: SecurityRepository
    private lateinit var networkClient: IElectionNetworkClient
    private lateinit var state: ElectionStateManager
    private lateinit var useCase: ResolveRelaySuperPeerConflictUseCase
    private var now = 1_000_000L

    @Before
    fun setUp() {
        securityRepository = mockk()
        networkClient = mockk()
        val trust = mockk<ITrustScoreProvider>()
        val events = mockk<NetworkEventRepository>()
        coEvery { securityRepository.getIdentity() } returns Result.success(NodeIdentity(localId, ByteArray(65)))
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(1, 2, 3))
        coEvery { trust.getTrustScore(any()) } returns 1
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.success(Unit)
        coEvery { events.pushEvent(any()) } returns Unit
        state = ElectionStateManager()
        useCase = ResolveRelaySuperPeerConflictUseCase(securityRepository, trust, state, networkClient, events)
        useCase.clockMs = { now }
    }

    @Test
    fun `with a mandate the relay never makes the super-pair yield, it reasserts the signed term`() = runTest {
        state.beginMandate(cluster, 5)
        val sent = slot<ElectionPayload>()
        coEvery { networkClient.broadcastElectionMessage(capture(sent)) } returns Result.success(Unit)

        // Rival de nodeId supérieur : l'ancienne règle faisait abdiquer le nœud local.
        val decision = useCase(cluster, higherRival, localId)

        assertEquals(ResolveRelaySuperPeerConflictUseCase.Decision.MANDATE_ASSERTED, decision)
        assertEquals(ElectionMessageType.COORDINATOR, sent.captured.type)
        assertEquals(5L, sent.captured.term)
        assertEquals(cluster, sent.captured.clusterId)
        assertEquals(localId, sent.captured.senderNodeId)
    }

    @Test
    fun `reassertion is rate limited per rival`() = runTest {
        state.beginMandate(cluster, 5)
        useCase(cluster, higherRival, localId)
        now += 2_000
        useCase(cluster, higherRival, localId)
        coVerify(exactly = 1) { networkClient.broadcastElectionMessage(any()) }

        now += ResolveRelaySuperPeerConflictUseCase.REASSERT_INTERVAL_MS
        useCase(cluster, higherRival, localId)
        coVerify(exactly = 2) { networkClient.broadcastElectionMessage(any()) }
    }

    @Test
    fun `a failed broadcast is retried at the next report`() = runTest {
        state.beginMandate(cluster, 5)
        coEvery { networkClient.broadcastElectionMessage(any()) } returns Result.failure(IllegalStateException("relais déconnecté"))
        useCase(cluster, higherRival, localId)
        now += 1_000
        useCase(cluster, higherRival, localId)
        coVerify(exactly = 2) { networkClient.broadcastElectionMessage(any()) }
    }

    @Test
    fun `without a numbered mandate the legacy nodeId rule applies`() = runTest {
        assertEquals(ResolveRelaySuperPeerConflictUseCase.Decision.YIELD, useCase(cluster, higherRival, localId))
        assertEquals(ResolveRelaySuperPeerConflictUseCase.Decision.KEEP, useCase(cluster, lowerRival, localId))
        coVerify(exactly = 0) { networkClient.broadcastElectionMessage(any()) }
    }

    @Test
    fun `a mandate held in another cluster does not count`() = runTest {
        state.beginMandate("other-cluster", 9)
        assertEquals(ResolveRelaySuperPeerConflictUseCase.Decision.YIELD, useCase(cluster, higherRival, localId))
    }
}
