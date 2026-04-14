package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.local.entity.PeerNodeEntity
import com.mobicloud.data.local.entity.toDomain
import com.mobicloud.data.local.entity.toEntity
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeerRepositoryImplTest {

    private lateinit var peerDao: PeerDao
    private lateinit var testScope: TestScope
    private lateinit var repository: PeerRepositoryImpl

    private val identity = NodeIdentity(
        nodeId = "node-1",
        publicKeyBytes = "pubkey".toByteArray(),
        reliabilityScore = 0.9f
    )

    @Before
    fun setUp() {
        peerDao = mockk(relaxed = true)
        testScope = TestScope(UnconfinedTestDispatcher())
        every { peerDao.getAllPeers() } returns flowOf(emptyList())
        repository = PeerRepositoryImpl(peerDao, testScope)
    }

    @Test
    fun `registerOrUpdatePeer retourne Result_success et appelle peerDao_insertOrUpdate`() = runTest {
        coEvery { peerDao.insertOrUpdate(any()) } returns Unit

        val result = repository.registerOrUpdatePeer(identity, 1000L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { peerDao.insertOrUpdate(any()) }
    }

    @Test
    fun `registerOrUpdatePeer retourne Result_failure si peerDao_insertOrUpdate leve une exception`() = runTest {
        val exception = RuntimeException("DB error")
        coEvery { peerDao.insertOrUpdate(any()) } throws exception

        val result = repository.registerOrUpdatePeer(identity, 1000L)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `evictStalePeers appelle peerDao_markInactive avec cutoff = currentTimeMs - timeoutMs`() = runTest {
        val cutoffSlot = slot<Long>()
        coEvery { peerDao.markInactive(capture(cutoffSlot)) } returns Unit

        val result = repository.evictStalePeers(timeoutMs = 15000L, currentTimeMs = 20000L)

        assertTrue(result.isSuccess)
        assertEquals(5000L, cutoffSlot.captured)
    }

    @Test
    fun `PeerNodeEntity_toDomain preserve isActive = false`() {
        val entity = PeerNodeEntity(
            nodeId = "node-2",
            publicKeyBytes = "key".toByteArray(),
            reliabilityScore = 1.0f,
            ipAddress = "192.168.1.10",
            port = 8080,
            lastSeenTimestampMs = 5000L,
            isActive = false,
            source = "LOCAL_UDP"
        )

        val peer = entity.toDomain()

        assertFalse(peer.isActive)
        assertEquals("node-2", peer.identity.nodeId)
    }

    @Test
    fun `Peer_toEntity preserve source_name`() {
        val peer = Peer(
            identity = identity,
            lastSeenTimestampMs = 3000L,
            source = DiscoverySource.REMOTE_FIREBASE,
            ipAddress = "10.0.0.1",
            port = 9090,
            isActive = true
        )

        val entity = peer.toEntity()

        assertEquals("REMOTE_FIREBASE", entity.source)
    }
}
