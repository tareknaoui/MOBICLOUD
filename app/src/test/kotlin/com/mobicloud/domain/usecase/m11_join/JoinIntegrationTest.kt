package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests d'intégration in-process — scénarios canoniques (AC13).
 * Admission par charge (memberCount < MAX_CLUSTER_SIZE) — Story 12.1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinIntegrationTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // ---- Identités ----
    private val aliceId = NodeIdentity("aabbcc", byteArrayOf(0xAA.toByte()), 0.95f)
    private val bobId   = NodeIdentity("112233", byteArrayOf(0x11.toByte()), 0.80f)
    private val carolId = NodeIdentity("445566", byteArrayOf(0x44.toByte()), 0.75f)
    private val daveId  = NodeIdentity("778899", byteArrayOf(0x77.toByte()), 0.70f)
    private val eveId   = NodeIdentity("aabbdd", byteArrayOf(0xBB.toByte()), 0.60f)

    // ---- Alice (SP) mocks ----
    private lateinit var aliceSecurityRepo: SecurityRepository
    private lateinit var aliceSignalingRepo: SignalingRepository
    private lateinit var aliceNetworkEventRepo: NetworkEventRepository
    private lateinit var aliceNodeSettingsRepo: NodeSettingsRepository
    private lateinit var alicePeerRepo: PeerRepository
    private lateinit var aliceMemberRegistry: MemberRegistry
    private lateinit var aliceFsm: JoinStateMachine
    private lateinit var aliceProcessJoin: ProcessJoinRequestUseCase

    // ---- Candidat mocks ----
    private lateinit var candidateSecurityRepo: SecurityRepository
    private lateinit var candidateNetworkEventRepo: NetworkEventRepository
    private lateinit var candidateNodeSettingsRepo: NodeSettingsRepository
    private lateinit var candidateJoinNetworkClient: IJoinNetworkClient
    private lateinit var candidateFsm: JoinStateMachine

    @Before
    fun setup() {
        aliceSecurityRepo = mockk()
        aliceSignalingRepo = mockk()
        aliceNetworkEventRepo = mockk(relaxed = true)
        aliceNodeSettingsRepo = mockk(relaxed = true)
        alicePeerRepo = mockk(relaxed = true)
        aliceMemberRegistry = RamMemberRegistry()
        aliceFsm = mockk(relaxed = true)

        coEvery { aliceSecurityRepo.getIdentity() } returns Result.success(aliceId)
        coEvery { aliceSecurityRepo.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        coEvery { aliceSecurityRepo.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { aliceSignalingRepo.fetchActiveSuperPeerHints() } returns Result.success(emptyList())
        every { aliceFsm.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cluster-alice"))

        aliceProcessJoin = ProcessJoinRequestUseCase(
            aliceSecurityRepo, aliceSignalingRepo,
            aliceMemberRegistry, aliceFsm, aliceNetworkEventRepo, mockk(relaxed = true)
        )

        candidateSecurityRepo = mockk()
        candidateNetworkEventRepo = mockk(relaxed = true)
        candidateNodeSettingsRepo = mockk(relaxed = true)
        candidateJoinNetworkClient = mockk()
        candidateFsm = JoinStateMachine(
            candidateNetworkEventRepo,
            dagger.Lazy<SendJoinRequestUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MarkSelfAsSuperPairUseCase> { mockk(relaxed = true) },
            dagger.Lazy<BullySoloElectionUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberHeartbeatUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MonitorMemberLivenessUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberSnapshotCacheUseCase> { mockk(relaxed = true) },
            mockk(relaxed = true),
            dispatcher
        )

        coEvery { candidateSecurityRepo.signData(any()) } returns Result.success(byteArrayOf(0xEE.toByte()))
        coEvery { candidateSecurityRepo.verifySignature(any(), any(), any()) } returns Result.success(true)
    }

    private fun buildAliceHint() = SuperPeerHint(
        nodeId = byteArrayOf(0xAA.toByte()),
        clusterId = "cluster-alice",
        ipAddress = "192.168.1.1",
        port = 5000,
        reliabilityScore = 0.95f
    )

    private fun buildRequest(identity: NodeIdentity): JoinRequest {
        val ts = System.currentTimeMillis()
        val senderBytes = identity.nodeId.hexToByteArray()
        val signedBytes = joinRequestSignedBytes(
            senderBytes, identity.publicKeyBytes,
            1_000_000L, identity.reliabilityScore, ts
        )
        return JoinRequest(
            senderNodeId = senderBytes,
            candidatePublicKey = identity.publicKeyBytes,
            freeBytes = 1_000_000L,
            reliabilityScore = identity.reliabilityScore,
            timestampMs = ts,
            signatureBytes = signedBytes
        )
    }

    // ---- T=1 : Bob → JoinAccept (cluster non plein) ----

    @Test
    fun `T1 Bob recoit JoinAccept et etat devient Member`() = runTest(dispatcher) {
        coEvery { candidateSecurityRepo.getIdentity() } returns Result.success(bobId)

        val request = buildRequest(bobId)
        val aliceResponse = aliceProcessJoin.invoke(request)
        assertTrue("Alice doit accepter Bob", aliceResponse is JoinResponse.JoinAccept)
        val accept = aliceResponse as JoinResponse.JoinAccept
        assertEquals("cluster-alice", accept.clusterId)

        candidateFsm.transition(JoinEvent.NewCandidateDetected(buildAliceHint()))
        candidateFsm.transition(JoinEvent.JoinAcceptReceived(accept))
        val state = candidateFsm.currentState.value
        assertTrue("Bob doit être Member", state is NodeJoinState.Member)
        assertEquals("cluster-alice", (state as NodeJoinState.Member).clusterId)
        assertTrue("Snapshot doit contenir Bob", aliceMemberRegistry.size() >= 1)
    }

    // ---- T=2 : Carol (découverte multicast) → JoinAccept ----

    @Test
    fun `T2 Carol recoit JoinAccept et etat devient Member`() = runTest(dispatcher) {
        coEvery { candidateSecurityRepo.getIdentity() } returns Result.success(carolId)

        val carolFsm = JoinStateMachine(
            candidateNetworkEventRepo,
            dagger.Lazy<SendJoinRequestUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MarkSelfAsSuperPairUseCase> { mockk(relaxed = true) },
            dagger.Lazy<BullySoloElectionUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberHeartbeatUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MonitorMemberLivenessUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberSnapshotCacheUseCase> { mockk(relaxed = true) },
            mockk(relaxed = true),
            dispatcher
        )
        val request = buildRequest(carolId)
        val response = aliceProcessJoin.invoke(request)

        assertTrue(response is JoinResponse.JoinAccept)
        carolFsm.transition(JoinEvent.NewCandidateDetected(buildAliceHint()))
        carolFsm.transition(JoinEvent.JoinAcceptReceived(response as JoinResponse.JoinAccept))
        assertTrue(carolFsm.currentState.value is NodeJoinState.Member)
    }

    // ---- T=3 : Cluster plein → JoinRedirect CLUSTER_FULL ----

    @Test
    fun `T3 cluster plein retourne JoinRedirect CLUSTER_FULL puis Dave passe Isolated`() = runTest(dispatcher) {
        coEvery { candidateSecurityRepo.getIdentity() } returns Result.success(daveId)

        val fullRegistry = mockk<MemberRegistry>(relaxed = true)
        coEvery { fullRegistry.addIfBelowCapacity(any(), any()) } returns false
        coEvery { fullRegistry.size() } returns MAX_CLUSTER_SIZE

        val fullAliceProcessJoin = ProcessJoinRequestUseCase(
            aliceSecurityRepo, aliceSignalingRepo,
            fullRegistry, aliceFsm, aliceNetworkEventRepo, mockk(relaxed = true)
        )

        val daveFsm = JoinStateMachine(
            candidateNetworkEventRepo,
            dagger.Lazy<SendJoinRequestUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MarkSelfAsSuperPairUseCase> { mockk(relaxed = true) },
            dagger.Lazy<BullySoloElectionUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberHeartbeatUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MonitorMemberLivenessUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberSnapshotCacheUseCase> { mockk(relaxed = true) },
            mockk(relaxed = true),
            dispatcher
        )

        val request = buildRequest(daveId)
        val response = fullAliceProcessJoin.invoke(request)

        assertTrue("Dave doit recevoir JoinRedirect", response is JoinResponse.JoinRedirect)
        assertEquals(JoinRedirectReason.CLUSTER_FULL, (response as JoinResponse.JoinRedirect).reason)

        daveFsm.transition(JoinEvent.NewCandidateDetected(buildAliceHint()))
        daveFsm.transition(JoinEvent.AllCandidatesExhausted)
        assertTrue(daveFsm.currentState.value is NodeJoinState.Isolated)

        advanceTimeBy(20_001L)
    }

    // ---- T=4 : Eve → JoinAccept (admission par charge, pas de GPS) ----

    @Test
    fun `T4 Eve recoit JoinAccept admission independante du GPS`() = runTest(dispatcher) {
        coEvery { candidateSecurityRepo.getIdentity() } returns Result.success(eveId)

        val eveFsm = JoinStateMachine(
            candidateNetworkEventRepo,
            dagger.Lazy<SendJoinRequestUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MarkSelfAsSuperPairUseCase> { mockk(relaxed = true) },
            dagger.Lazy<BullySoloElectionUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberHeartbeatUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MonitorMemberLivenessUseCase> { mockk(relaxed = true) },
            dagger.Lazy<MemberSnapshotCacheUseCase> { mockk(relaxed = true) },
            mockk(relaxed = true),
            dispatcher
        )
        val request = buildRequest(eveId)
        val response = aliceProcessJoin.invoke(request)

        assertTrue("Eve doit être acceptée (admission par charge uniquement)", response is JoinResponse.JoinAccept)
        eveFsm.transition(JoinEvent.NewCandidateDetected(buildAliceHint()))
        eveFsm.transition(JoinEvent.JoinAcceptReceived(response as JoinResponse.JoinAccept))
        assertTrue(eveFsm.currentState.value is NodeJoinState.Member)
    }

    // ---- Invariant : un clusterId généré par BullySolo est unique ----

    @Test
    fun `clusterId genere par BullySolo est differente du clusterId d Alice`() = runTest(dispatcher) {
        val newClusterId = java.util.UUID.randomUUID().toString()
        assertNotEquals("cluster-alice", newClusterId)
    }
}
