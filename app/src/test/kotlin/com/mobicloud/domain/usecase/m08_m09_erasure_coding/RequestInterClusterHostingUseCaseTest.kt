package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.repository.SignalingRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RequestInterClusterHostingUseCaseTest {

    private lateinit var signalingRepository: SignalingRepository
    private lateinit var useCase: RequestInterClusterHostingUseCase

    private val localClusterId = "cluster-local-AAAA"
    private val peersFlow = MutableStateFlow<List<RelayPeer>>(emptyList())

    @Before
    fun setUp() {
        signalingRepository = mockk()
        coEvery { signalingRepository.latestPeers } returns peersFlow
        useCase = RequestInterClusterHostingUseCase(signalingRepository)
    }

    private fun peer(
        nodeId: String,
        clusterId: String = "cluster-remote-BBBB",
        freeBytes: Long = 10_000_000L,
        isSuperPair: Boolean = true,
        ip: String = "10.0.0.1",
        port: Int = 9000
    ) = RelayPeer(
        nodeId = nodeId,
        ip = ip,
        port = port,
        reliabilityScore = 0.9f,
        lastSeen = System.currentTimeMillis(),
        isSuperPair = isSuperPair,
        clusterId = clusterId,
        freeBytes = freeBytes
    )

    // AC#6 — empty list → null (no-op gracieux avant tout GET_PEERS)
    @Test
    fun `selectRemoteHost retourne null quand latestPeers est vide`() = runTest {
        peersFlow.value = emptyList()
        assertNull(useCase.selectRemoteHost(1024, localClusterId))
    }

    // AC#6 — local clusterId pas encore provisionné → null (early return)
    @Test
    fun `selectRemoteHost retourne null quand localClusterId est vide`() = runTest {
        peersFlow.value = listOf(peer("n1"))
        assertNull(useCase.selectRemoteHost(1024, ""))
    }

    // Patch — blockSize <= 0 → null (sinon le filtre freeBytes >= 0 matcherait tout)
    @Test
    fun `selectRemoteHost retourne null quand blockSize est zero`() = runTest {
        peersFlow.value = listOf(peer("any"))
        assertNull(useCase.selectRemoteHost(0, localClusterId))
    }

    @Test
    fun `selectRemoteHost retourne null quand blockSize est negatif`() = runTest {
        peersFlow.value = listOf(peer("any"))
        assertNull(useCase.selectRemoteHost(-1, localClusterId))
    }

    // AC#2 — candidat parfait retourné
    @Test
    fun `selectRemoteHost retourne le candidat parfait`() = runTest {
        peersFlow.value = listOf(peer("perfect"))
        val r = useCase.selectRemoteHost(1024, localClusterId)
        assertEquals("perfect", r?.nodeId)
    }

    // AC#2 — clusterId blank rejected (legacy/coerce)
    @Test
    fun `selectRemoteHost rejette clusterId vide`() = runTest {
        peersFlow.value = listOf(peer("legacy", clusterId = ""))
        assertNull(useCase.selectRemoteHost(1024, localClusterId))
    }

    // AC#2 — clusterId == local rejected
    @Test
    fun `selectRemoteHost rejette clusterId egal au local`() = runTest {
        peersFlow.value = listOf(peer("same-cluster", clusterId = localClusterId))
        assertNull(useCase.selectRemoteHost(1024, localClusterId))
    }

    // AC#2 — non-super-pair rejected
    @Test
    fun `selectRemoteHost rejette isSuperPair=false`() = runTest {
        peersFlow.value = listOf(peer("join-only", isSuperPair = false))
        assertNull(useCase.selectRemoteHost(1024, localClusterId))
    }

    // AC#2 — freeBytes < blockSize rejected
    @Test
    fun `selectRemoteHost rejette freeBytes insuffisant`() = runTest {
        peersFlow.value = listOf(peer("small", freeBytes = 500L))
        assertNull(useCase.selectRemoteHost(1024, localClusterId))
    }

    // AC#2 — freeBytes == blockSize accepted (borne >=)
    @Test
    fun `selectRemoteHost accepte freeBytes egal a blockSize`() = runTest {
        peersFlow.value = listOf(peer("exact", freeBytes = 1024L))
        val r = useCase.selectRemoteHost(1024, localClusterId)
        assertEquals("exact", r?.nodeId)
    }

    // AC#7 — freeBytes proche de blockSize accepté
    @Test
    fun `selectRemoteHost accepte freeBytes legerement superieur a blockSize`() = runTest {
        peersFlow.value = listOf(peer("close", freeBytes = 1024L + 1024L))
        val r = useCase.selectRemoteHost(1024, localClusterId)
        assertEquals("close", r?.nodeId)
    }

    // AC#2 — tri freeBytes décroissant
    @Test
    fun `selectRemoteHost retourne le candidat avec le plus grand freeBytes`() = runTest {
        peersFlow.value = listOf(
            peer("small-100MB", freeBytes = 100_000_000L),
            peer("big-500MB", freeBytes = 500_000_000L),
            peer("medium-200MB", freeBytes = 200_000_000L)
        )
        val r = useCase.selectRemoteHost(1024, localClusterId)
        assertEquals("big-500MB", r?.nodeId)
    }

    // AC#2 — ip vide rejected
    @Test
    fun `selectRemoteHost rejette ip vide`() = runTest {
        peersFlow.value = listOf(peer("noip", ip = ""))
        assertNull(useCase.selectRemoteHost(1024, localClusterId))
    }

    // AC#2 — port 0 rejected
    @Test
    fun `selectRemoteHost rejette port 0`() = runTest {
        peersFlow.value = listOf(peer("noport", port = 0))
        assertNull(useCase.selectRemoteHost(1024, localClusterId))
    }
}
