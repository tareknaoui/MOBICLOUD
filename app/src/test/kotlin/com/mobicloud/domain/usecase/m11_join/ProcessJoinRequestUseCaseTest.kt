package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessJoinRequestUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var signalingRepository: SignalingRepository
    private lateinit var memberRegistry: MemberRegistry
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: ProcessJoinRequestUseCase

    private val selfNodeId = "aabb"
    private val selfIdentity = NodeIdentity(selfNodeId, byteArrayOf(0xAA.toByte(), 0xBB.toByte()), 0.9f)

    private val candidateNodeId = byteArrayOf(0x01, 0x02)
    private val candidatePubKey = byteArrayOf(0x03, 0x04)

    @Before
    fun setup() {
        securityRepository = mockk()
        signalingRepository = mockk()
        memberRegistry = RamMemberRegistry()
        joinStateMachine = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)

        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { signalingRepository.fetchActiveSuperPeerHints() } returns Result.success(emptyList())
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cluster-1"))

        useCase = ProcessJoinRequestUseCase(
            securityRepository, signalingRepository,
            memberRegistry, joinStateMachine, networkEventRepository
        )
    }

    private fun makeRequest(freeBytes: Long = 1_000_000L): JoinRequest {
        val ts = System.currentTimeMillis()
        val signed = joinRequestSignedBytes(candidateNodeId, candidatePubKey, freeBytes, 0.8f, ts)
        return JoinRequest(
            senderNodeId = candidateNodeId,
            candidatePublicKey = candidatePubKey,
            freeBytes = freeBytes,
            reliabilityScore = 0.8f,
            timestampMs = ts,
            signatureBytes = signed
        )
    }

    // AC7 — admission sans GPS : tout JOIN_REQUEST valide est accepté si capacité disponible
    @Test
    fun `acceptsRegardlessOfLocation - candidat valide sans GPS est accepte`() = runTest {
        val result = useCase.invoke(makeRequest())
        assertTrue(result is JoinResponse.JoinAccept)
        assertEquals(1, memberRegistry.size())
    }

    // Branche 1 : Signature invalide → INVALID_SIGNATURE
    @Test
    fun `signature invalide retourne JoinRedirect INVALID_SIGNATURE`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)
        val result = useCase.invoke(makeRequest())
        assertTrue(result is JoinResponse.JoinRedirect)
        assertEquals(JoinRedirectReason.INVALID_SIGNATURE, (result as JoinResponse.JoinRedirect).reason)
    }

    // Branche 2 : Timestamp hors fenêtre → INVALID_SIGNATURE
    @Test
    fun `timestamp hors fenetre anti-replay retourne INVALID_SIGNATURE`() = runTest {
        val oldTs = System.currentTimeMillis() - 60_000L
        val req = JoinRequest(
            senderNodeId = candidateNodeId,
            candidatePublicKey = candidatePubKey,
            freeBytes = 0L,
            reliabilityScore = 0.8f,
            timestampMs = oldTs,
            signatureBytes = byteArrayOf(1)
        )
        val result = useCase.invoke(req)
        assertTrue(result is JoinResponse.JoinRedirect)
        assertEquals(JoinRedirectReason.INVALID_SIGNATURE, (result as JoinResponse.JoinRedirect).reason)
    }

    // Branche 3 : Cluster plein → CLUSTER_FULL (seul critère de rejet automatique Story 12.1)
    @Test
    fun `cluster plein retourne JoinRedirect CLUSTER_FULL`() = runTest {
        repeat(MAX_CLUSTER_SIZE) { i ->
            memberRegistry.add(MemberInfo(
                nodeId = byteArrayOf(i.toByte()),
                publicKey = byteArrayOf(i.toByte()),
                ipAddress = "x", port = i,
                freeBytes = 0L, role = MemberRole.MEMBER
            ))
        }
        val result = useCase.invoke(makeRequest())
        assertTrue(result is JoinResponse.JoinRedirect)
        assertEquals(JoinRedirectReason.CLUSTER_FULL, (result as JoinResponse.JoinRedirect).reason)
    }

    // AC7 — nœud dans INVALID_STATE → INVALID_STATE (pas SuperPair)
    @Test
    fun `noeud non SuperPair retourne JoinRedirect INVALID_STATE`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.Undiscovered)
        val result = useCase.invoke(makeRequest())
        assertTrue(result is JoinResponse.JoinRedirect)
        assertEquals(JoinRedirectReason.INVALID_STATE, (result as JoinResponse.JoinRedirect).reason)
    }
}
