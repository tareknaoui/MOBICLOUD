package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.GpsCoordinate
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinMetrics
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.LocationRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendJoinRequestUseCaseTest {

    private lateinit var networkClient: IJoinNetworkClient
    private lateinit var securityRepository: SecurityRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var useCase: SendJoinRequestUseCase
    private val dispatcher = UnconfinedTestDispatcher()

    private val localIdentity = NodeIdentity("0102", byteArrayOf(0x01, 0x02), 0.8f)
    private val spHint = SuperPeerHint(byteArrayOf(0xAA.toByte()), ipAddress = "1.2.3.4", port = 5000, reliabilityScore = 0.9f)
    private val algerGps = GpsCoordinate(36.7, 3.08, 5f, 1L)

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
        locationRepository = mockk()
        nodeSettingsRepository = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        joinStateMachine = mockk(relaxed = true)

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0xFF.toByte()))
        every { locationRepository.currentLocation } returns MutableStateFlow(null)

        useCase = SendJoinRequestUseCase(
            networkClient, securityRepository, locationRepository,
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
        val altHint = SuperPeerHint(byteArrayOf(0xBB.toByte()), ipAddress = "2.3.4.5", port = 6000, reliabilityScore = 0.7f)
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

    // Scénario 4 : Tri par distance GPS quand self GPS disponible
    @Test
    fun `tri par distance GPS si self GPS disponible`() = runTest(dispatcher) {
        every { locationRepository.currentLocation } returns MutableStateFlow(algerGps)

        // Candidat proche (Alger) et candidat loin (Oran ≈ 354 km)
        val close = SuperPeerHint(byteArrayOf(0xCC.toByte()), gpsLatitude = 36.7, gpsLongitude = 3.1,
            ipAddress = "1.1.1.1", port = 5000, reliabilityScore = 0.5f)
        val far = SuperPeerHint(byteArrayOf(0xDD.toByte()), gpsLatitude = 35.7, gpsLongitude = -0.62,
            ipAddress = "2.2.2.2", port = 5000, reliabilityScore = 0.9f)

        // Le candidat proche doit être appelé en premier (même si son reliabilityScore est inférieur)
        var firstCalled: ByteArray? = null
        coEvery { networkClient.sendJoinRequest(any(), any()) } answers {
            if (firstCalled == null) firstCalled = firstArg<SuperPeerHint>().nodeId
            Result.failure(Exception("rejected"))
        }

        useCase.invoke(listOf(far, close)).first()

        assertTrue(firstCalled?.contentEquals(close.nodeId) == true)
    }

    // Scénario 5 : Fallback reliabilityScore quand GPS null
    @Test
    fun `fallback reliabilityScore si self GPS null`() = runTest(dispatcher) {
        every { locationRepository.currentLocation } returns MutableStateFlow(null)

        val lowScore = SuperPeerHint(byteArrayOf(0xEE.toByte()), ipAddress = "3.3.3.3", port = 5000, reliabilityScore = 0.3f)
        val highScore = SuperPeerHint(byteArrayOf(0xFF.toByte()), ipAddress = "4.4.4.4", port = 5000, reliabilityScore = 0.9f)

        var firstCalled: ByteArray? = null
        coEvery { networkClient.sendJoinRequest(any(), any()) } answers {
            if (firstCalled == null) firstCalled = firstArg<SuperPeerHint>().nodeId
            Result.failure(Exception("rejected"))
        }

        useCase.invoke(listOf(lowScore, highScore)).first()

        assertTrue(firstCalled?.contentEquals(highScore.nodeId) == true)
    }
}
