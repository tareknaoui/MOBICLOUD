package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.GpsCoordinate
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.repository.LocationRepository
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
    private lateinit var locationRepository: LocationRepository
    private lateinit var signalingRepository: SignalingRepository
    private lateinit var memberRegistry: MemberRegistry
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: ProcessJoinRequestUseCase

    private val selfNodeId = "aabb"
    private val selfIdentity = NodeIdentity(selfNodeId, byteArrayOf(0xAA.toByte(), 0xBB.toByte()), 0.9f)
    private val algerGps = GpsCoordinate(36.72, 3.08, 5f, 1L)
    private val oranGps = GpsCoordinate(35.70, -0.62, 5f, 1L) // ≈354 km

    private val candidateNodeId = byteArrayOf(0x01, 0x02)
    private val candidatePubKey = byteArrayOf(0x03, 0x04)

    @Before
    fun setup() {
        securityRepository = mockk()
        locationRepository = mockk()
        signalingRepository = mockk()
        memberRegistry = RamMemberRegistry()
        joinStateMachine = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)

        coEvery { securityRepository.getIdentity() } returns Result.success(selfIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        // Signature valide par défaut
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        every { locationRepository.currentLocation } returns MutableStateFlow(null)
        coEvery { signalingRepository.fetchActiveSuperPeerHints() } returns Result.success(emptyList())
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cluster-1"))

        useCase = ProcessJoinRequestUseCase(
            securityRepository, locationRepository, signalingRepository,
            memberRegistry, joinStateMachine, networkEventRepository
        )
    }

    private fun makeRequest(
        lat: Double? = null, lng: Double? = null,
        freeBytes: Long = 1_000_000L
    ): JoinRequest {
        val ts = System.currentTimeMillis()
        val signed = joinRequestSignedBytes(candidateNodeId, candidatePubKey, lat, lng, freeBytes, 0.8f, ts)
        return JoinRequest(
            senderNodeId = candidateNodeId,
            candidatePublicKey = candidatePubKey,
            gpsLatitude = lat, gpsLongitude = lng,
            freeBytes = freeBytes, reliabilityScore = 0.8f,
            timestampMs = ts, signatureBytes = signed
        )
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
            senderNodeId = candidateNodeId, candidatePublicKey = candidatePubKey,
            gpsLatitude = null, gpsLongitude = null,
            freeBytes = 0L, reliabilityScore = 0.8f,
            timestampMs = oldTs, signatureBytes = byteArrayOf(1)
        )
        val result = useCase.invoke(req)
        assertTrue(result is JoinResponse.JoinRedirect)
        assertEquals(JoinRedirectReason.INVALID_SIGNATURE, (result as JoinResponse.JoinRedirect).reason)
    }

    // Branche 3 : OUT_OF_RADIUS (self GPS + candidate GPS, distance > MAX_RADIUS)
    @Test
    fun `candidat trop loin retourne JoinRedirect OUT_OF_RADIUS`() = runTest {
        every { locationRepository.currentLocation } returns MutableStateFlow(algerGps)
        val result = useCase.invoke(makeRequest(oranGps.latitude, oranGps.longitude))
        assertTrue(result is JoinResponse.JoinRedirect)
        val redirect = result as JoinResponse.JoinRedirect
        assertEquals(JoinRedirectReason.OUT_OF_RADIUS, redirect.reason)
        assertTrue(redirect.distanceMeters!! > 5_000.0)
    }

    // Branche 3b : GPS self null → filtre sauté → candidat admis
    @Test
    fun `GPS self null saute le filtre haversine et accepte le candidat`() = runTest {
        every { locationRepository.currentLocation } returns MutableStateFlow(null)
        val result = useCase.invoke(makeRequest(oranGps.latitude, oranGps.longitude))
        assertTrue(result is JoinResponse.JoinAccept)
    }

    // Branche 3c : GPS candidat null → filtre sauté → candidat admis
    @Test
    fun `GPS candidat null saute le filtre haversine et accepte le candidat`() = runTest {
        every { locationRepository.currentLocation } returns MutableStateFlow(algerGps)
        val result = useCase.invoke(makeRequest(null, null))
        assertTrue(result is JoinResponse.JoinAccept)
    }

    // Branche 3d : les deux GPS null → filtre sauté → candidat admis
    @Test
    fun `les deux GPS null saute le filtre haversine et accepte le candidat`() = runTest {
        every { locationRepository.currentLocation } returns MutableStateFlow(null)
        val result = useCase.invoke(makeRequest(null, null))
        assertTrue(result is JoinResponse.JoinAccept)
    }

    // Branche 4 : Cluster plein → CLUSTER_FULL
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

    // Branche 5 : Acceptation normale → JoinAccept + membre ajouté au registre
    @Test
    fun `candidat valide dans le rayon retourne JoinAccept`() = runTest {
        every { locationRepository.currentLocation } returns MutableStateFlow(algerGps)
        val result = useCase.invoke(makeRequest(algerGps.latitude + 0.01, algerGps.longitude + 0.01))
        assertTrue(result is JoinResponse.JoinAccept)
        assertEquals(1, memberRegistry.size())
    }
}
