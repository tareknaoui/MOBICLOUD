package com.mobicloud.data.repository_impl

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeerRegistryImplTest {

    @Test
    fun `registerOrUpdatePeer adds new peer if not exists`() = runTest {
        val registry = PeerRegistryImpl()
        val identity = NodeIdentity(nodeId = "dev1", publicKeyBytes = "pub1".toByteArray())
        
        registry.registerOrUpdatePeer(identity, 1000L)
        
        val activePeers = registry.activePeers.first()
        assertEquals(1, activePeers.size)
        assertEquals("dev1", activePeers[0].identity.nodeId)
        assertEquals(1000L, activePeers[0].lastSeenTimestampMs)
    }

    @Test
    fun `registerOrUpdatePeer updates timestamp for existing peer`() = runTest {
        val registry = PeerRegistryImpl()
        val identity = NodeIdentity(nodeId = "dev1", publicKeyBytes = "pub1".toByteArray())
        
        registry.registerOrUpdatePeer(identity, 1000L)
        registry.registerOrUpdatePeer(identity, 5000L) // Update
        
        val activePeers = registry.activePeers.first()
        assertEquals(1, activePeers.size)
        assertEquals(5000L, activePeers[0].lastSeenTimestampMs)
    }

    @Test
    fun `evictStalePeers removes peers exceeding timeout`() = runTest {
        val registry = PeerRegistryImpl()
        val id1 = NodeIdentity(nodeId = "dev1", publicKeyBytes = "pub1".toByteArray())
        val id2 = NodeIdentity(nodeId = "dev2", publicKeyBytes = "pub2".toByteArray())
        
        registry.registerOrUpdatePeer(id1, 1000L)
        registry.registerOrUpdatePeer(id2, 4000L)
        
        // Evict peers older than 5000ms from current time 7000L
        // So peers older than 7000 - 5000 = 2000L should be evicted.
        // dev1 (1000L) < 2000L -> Evicted
        // dev2 (4000L) >= 2000L -> Kept
        registry.evictStalePeers(timeoutMs = 5000L, currentTimeMs = 7000L)
        
        val activePeers = registry.activePeers.first()
        assertEquals(1, activePeers.size)
        assertEquals("dev2", activePeers[0].identity.nodeId)
    }
}
