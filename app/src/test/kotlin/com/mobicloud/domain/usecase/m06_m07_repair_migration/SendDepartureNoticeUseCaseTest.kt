package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendDepartureNoticeUseCaseTest {

    private lateinit var hostedBlockRepository: HostedBlockRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var gossipRelayChannel: com.mobicloud.data.p2p.relay.GossipRelayChannel
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: SendDepartureNoticeUseCase

    private lateinit var peersFlow: MutableStateFlow<List<Peer>>

    private val fakeIdentity = NodeIdentity(
        nodeId = "aabbcc112233aabbcc112233aabbcc112233aabbcc112233aabbcc1122334455",
        publicKeyBytes = byteArrayOf()
    )

    private val superPeer = Peer(
        identity = NodeIdentity(
            nodeId = "superPeer0011223344556677889900aabbccddeeff0011223344556677889900aa",
            publicKeyBytes = byteArrayOf()
        ),
        lastSeenTimestampMs = System.currentTimeMillis(),
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = "192.168.1.1",
        port = 9000,
        isActive = true,
        isSuperPair = true
    )

    @Before
    fun setup() {
        hostedBlockRepository = mockk()
        securityRepository = mockk()
        peerRepository = mockk()
        gossipRelayChannel = mockk(relaxed = true)
        networkEventRepository = mockk()

        peersFlow = MutableStateFlow(emptyList())
        every { peerRepository.peers } returns peersFlow
        every { networkEventRepository.pushEvent(any()) } just Runs

        useCase = SendDepartureNoticeUseCase(
            hostedBlockRepository = hostedBlockRepository,
            securityRepository = securityRepository,
            peerRepository = peerRepository,
            gossipRelayChannel = gossipRelayChannel,
            networkEventRepository = networkEventRepository
        )
    }

    @Test
    fun `test 1 - notice envoyee avec blockIds vers super-pair`() = runTest {
        val blockIds = listOf(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )
        coEvery { securityRepository.getIdentity() } returns Result.success(fakeIdentity)
        coEvery { hostedBlockRepository.getAllBlockIds() } returns Result.success(blockIds)
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf(0x01, 0x02))
        peersFlow.value = listOf(superPeer)
        coEvery { gossipRelayChannel.sendDepartureNotice(any(), any()) } returns Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify {
            gossipRelayChannel.sendDepartureNotice(
                any(),
                match { it.hostedBlockIds.size == 2 }
            )
        }
    }

    @Test
    fun `test 2 - aucun super-pair - sendDepartureNotice non appele`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(fakeIdentity)
        coEvery { hostedBlockRepository.getAllBlockIds() } returns Result.success(listOf("aaaa".padEnd(64, 'a')))
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf())
        peersFlow.value = emptyList()

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { gossipRelayChannel.sendDepartureNotice(any(), any()) }
    }

    @Test
    fun `test 3 - noeud sans blocs heberges - sendDepartureNotice appele avec liste vide`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(fakeIdentity)
        coEvery { hostedBlockRepository.getAllBlockIds() } returns Result.success(emptyList())
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf())
        peersFlow.value = listOf(superPeer)
        coEvery { gossipRelayChannel.sendDepartureNotice(any(), any()) } returns Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify {
            gossipRelayChannel.sendDepartureNotice(
                any(),
                match { it.hostedBlockIds.isEmpty() }
            )
        }
    }

    @Test
    fun `test 4 - delai de 5 secondes respecte`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.success(fakeIdentity)
        coEvery { hostedBlockRepository.getAllBlockIds() } returns Result.success(emptyList())
        coEvery { securityRepository.signData(any()) } returns Result.success(byteArrayOf())
        peersFlow.value = emptyList()

        var completed = false
        val job = backgroundScope.launch {
            useCase()
            completed = true
        }

        // Avant 5000ms, le use case ne doit pas être terminé
        advanceTimeBy(4_999L)
        assertTrue("Le use case ne devrait pas être terminé avant 5s", !completed)

        // Après 5000ms, il doit être terminé
        advanceTimeBy(1L)
        job.join()
        assertTrue("Le use case doit être terminé après 5s", completed)
    }
}
