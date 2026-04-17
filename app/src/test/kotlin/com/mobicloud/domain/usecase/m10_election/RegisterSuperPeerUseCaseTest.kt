package com.mobicloud.domain.usecase.m10_election

import android.util.Log
import com.mobicloud.data.network.PublicIpFetcher
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SignalingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterSuperPeerUseCaseTest {

    private lateinit var signalingRepository: SignalingRepository
    private lateinit var identityRepository: IdentityRepository
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var publicIpFetcher: PublicIpFetcher
    private lateinit var trustScoreProvider: ITrustScoreProvider

    private lateinit var useCase: RegisterSuperPeerUseCase

    private val localIdentity = NodeIdentity("localNode123", ByteArray(0), 0.85f)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        signalingRepository = mockk()
        identityRepository = mockk()
        networkEventRepository = mockk(relaxed = true)
        publicIpFetcher = mockk()
        trustScoreProvider = mockk()

        coEvery { identityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { publicIpFetcher.fetchPublicIp() } returns Result.success("203.0.113.42")
        coEvery { trustScoreProvider.getTrustScore("localNode123") } returns 85
        coEvery { signalingRepository.registerSuperPeer(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { signalingRepository.unregisterSuperPeer() } returns Result.success(Unit)
        every { networkEventRepository.pushEvent(any()) } returns Unit

        useCase = RegisterSuperPeerUseCase(
            signalingRepository = signalingRepository,
            identityRepository = identityRepository,
            networkEventRepository = networkEventRepository,
            publicIpFetcher = publicIpFetcher,
            trustScoreProvider = trustScoreProvider
        )
    }

    @Test
    fun `enregistrement Firebase reussi - emet Result success et log evenement`() = runTest {
        val result = useCase(tcpPort = 7777).first()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { signalingRepository.registerSuperPeer("203.0.113.42", 7777, any(), any()) }
        coVerify(exactly = 1) { networkEventRepository.pushEvent(match { it.contains("Super-Pair enregistré") }) }
    }

    @Test
    fun `keepalive - registerSuperPeer rappele apres 30 secondes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val results = mutableListOf<Result<Unit>>()

        val job = launch(testDispatcher) {
            useCase(tcpPort = 7777).toList(results)
        }

        // Avancer d'un tick pour que l'enregistrement initial se fasse
        advanceTimeBy(1L)

        // Vérifier enregistrement initial
        coVerify(exactly = 1) { signalingRepository.registerSuperPeer(any(), any(), any(), any()) }

        // Avancer de 30s + 1ms pour déclencher le keepalive
        advanceTimeBy(30_001L)

        coVerify(exactly = 2) { signalingRepository.registerSuperPeer(any(), any(), any(), any()) }

        job.cancel()
    }

    @Test
    fun `keepalive - deux keepalives apres 60 secondes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val job = launch(testDispatcher) {
            useCase(tcpPort = 7777).toList(mutableListOf())
        }

        advanceTimeBy(1L)
        advanceTimeBy(60_001L)

        // 1 initial + 2 keepalives = 3 appels
        coVerify(exactly = 3) { signalingRepository.registerSuperPeer(any(), any(), any(), any()) }

        job.cancel()
    }

    @Test
    fun `fallback gracieux si Firebase inaccessible - emet Result failure sans crash`() = runTest {
        coEvery { signalingRepository.registerSuperPeer(any(), any(), any(), any()) } returns
            Result.failure(Exception("Firebase unavailable"))

        val result = useCase(tcpPort = 7777).first()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Firebase") == true)
    }

    @Test
    fun `abdication - unregisterSuperPeer appele lors de l annulation de la coroutine`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val job = launch(testDispatcher) {
            useCase(tcpPort = 7777).toList(mutableListOf())
        }

        // Laisser le temps à l'enregistrement initial de se faire
        advanceTimeBy(1L)

        // Annuler = abdication
        job.cancel()
        job.join()

        coVerify(exactly = 1) { signalingRepository.unregisterSuperPeer() }
    }

    @Test
    fun `echec recuperation identite - emet Result failure`() = runTest {
        coEvery { identityRepository.getIdentity() } returns Result.failure(Exception("No identity"))

        val result = useCase(tcpPort = 7777).first()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { signalingRepository.registerSuperPeer(any(), any(), any(), any()) }
    }

    @Test
    fun `echec recuperation IP publique - emet Result failure`() = runTest {
        coEvery { publicIpFetcher.fetchPublicIp() } returns Result.failure(Exception("Network error"))

        val result = useCase(tcpPort = 7777).first()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { signalingRepository.registerSuperPeer(any(), any(), any(), any()) }
    }

    @Test
    fun `keepalive echec Firebase - log warning mais pas de crash`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        // Initial réussit, keepalive échoue
        coEvery { signalingRepository.registerSuperPeer(any(), any(), any(), any()) }
            .returnsMany(Result.success(Unit), Result.failure(Exception("Keepalive failed")))

        val job = launch(testDispatcher) {
            useCase(tcpPort = 7777).toList(mutableListOf())
        }

        advanceTimeBy(1L)
        advanceTimeBy(30_001L)

        // Le flow n'a pas crashé — la coroutine tourne toujours
        assertTrue(job.isActive)

        job.cancel()
    }
}
