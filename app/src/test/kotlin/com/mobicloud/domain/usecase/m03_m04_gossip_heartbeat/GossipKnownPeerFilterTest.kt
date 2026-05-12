package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse
import com.mobicloud.domain.models.gossip.DhtEntryDto
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.ResolveDhtConflictUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests adversariaux du filtre "known peer" applique aux 3 entrees gossip :
 *
 *  G1 — Bloom d'un sender NON-pair -> rejete (anti-amplification)
 *  G2 — DeltaSyncRequest d'un requester NON-pair -> rejete (anti-DoS DB)
 *  G3 — DeltaSyncResponse d'un responder NON-pair -> rejete (anti-DHT-poisoning)
 *  G4 — Bloom d'un pair connu -> traite normalement (controle positif)
 */
class GossipKnownPeerFilterTest {

    private lateinit var dhtRepository: DhtRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var gossipOutboundPort: GossipOutboundPort
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var resolveDhtConflictUseCase: ResolveDhtConflictUseCase

    private lateinit var useCase: GossipSyncUseCase

    private val knownNodeId = "known-peer-AAA"
    private val unknownNodeId = "MALLORY---attacker"
    private val localNodeId = "local-node-BBB"

    private fun knownPeer() = Peer(
        identity = NodeIdentity(knownNodeId, ByteArray(65)),
        lastSeenTimestampMs = System.currentTimeMillis(),
        isActive = true,
        isSuperPair = false,
        ipAddress = "10.0.0.1",
        port = 9090
    )

    @Before
    fun setUp() {
        dhtRepository = mockk(relaxed = true)
        peerRepository = mockk()
        gossipOutboundPort = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        securityRepository = mockk()
        resolveDhtConflictUseCase = mockk(relaxed = true)

        coEvery { securityRepository.getIdentity() } returns Result.success(
            NodeIdentity(localNodeId, ByteArray(65))
        )
        every { dhtRepository.observeAllEntries() } returns flowOf(emptyList())

        // Seul knownPeer est dans le registry
        every { peerRepository.peers } returns MutableStateFlow(listOf(knownPeer()))

        useCase = GossipSyncUseCase(
            dhtRepository,
            peerRepository,
            gossipOutboundPort,
            networkEventRepository,
            securityRepository,
            resolveDhtConflictUseCase,
            CoroutineScope(Dispatchers.Default)
        )
    }

    // ── G1 — Bloom d'un non-pair est rejete (anti-amplification) ─────────────

    @Test
    fun `G1 - Bloom d'un sender non-pair declenche un early-return (pas d'acces DHT)`() = runTest {
        val attackerBloom = BloomFilterGossip(
            senderNodeId = unknownNodeId,
            bloomFilterBytes = ByteArray(128),
            bloomFilterSize = 1024,
            numHashFunctions = 3,
            partitionIds = emptyList(),
            timestamp = System.currentTimeMillis()
        )

        val result = useCase.handleIncomingBloom(attackerBloom)

        io.mockk.verify(exactly = 0) { dhtRepository.observeAllEntries() }
        coVerify(exactly = 0) { gossipOutboundPort.sendDeltaSyncRequest(any(), any()) }
        assertTrue("Doit retourner success silencieux (rejet)", result.isSuccess)
    }

    // ── G2 — DeltaSyncRequest d'un non-pair est rejete (anti-DoS DB) ─────────

    @Test
    fun `G2 - DeltaSyncRequest d'un requester non-pair est rejete`() = runTest {
        val attackerRequest = DeltaSyncRequest(
            requesterNodeId = unknownNodeId,
            missingBlockIds = listOf("a".repeat(64), "b".repeat(64)),
            timestamp = System.currentTimeMillis()
        )

        val result = useCase.handleDeltaRequest(attackerRequest)

        coVerify(exactly = 0) { dhtRepository.findByBlockId(any()) }
        assertTrue("DeltaRequest non-pair doit etre rejete", result.isFailure)
    }

    // ── G3 — DeltaSyncResponse d'un non-pair est rejete (anti-poisoning) ─────

    @Test
    fun `G3 - DeltaSyncResponse d'un responder non-pair est rejete`() = runTest {
        val attackerResponse = DeltaSyncResponse(
            responderNodeId = unknownNodeId,
            entries = listOf(
                DhtEntryDto(
                    blockId = "a".repeat(64),
                    nodeId = "victim-node",
                    ipAddress = "1.2.3.4",
                    port = 666,
                    timestamp = System.currentTimeMillis()
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val result = useCase.handleDeltaResponse(attackerResponse)

        coVerify(exactly = 0) { resolveDhtConflictUseCase.resolve(any()) }
        assertTrue("DeltaResponse non-pair doit etre rejete", result.isFailure)
    }

    // ── G4 — Pair connu : traite normalement (controle positif) ──────────────

    @Test
    fun `G4 - DeltaSyncResponse d'un pair connu est accepte`() = runTest {
        val validResponse = DeltaSyncResponse(
            responderNodeId = knownNodeId, // pair connu
            entries = listOf(
                DhtEntryDto(
                    blockId = "c".repeat(64),
                    nodeId = "another-host",
                    ipAddress = "10.0.0.5",
                    port = 9000,
                    timestamp = System.currentTimeMillis()
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        val result = useCase.handleDeltaResponse(validResponse)

        coVerify(exactly = 1) { resolveDhtConflictUseCase.resolve(any()) }
        assertTrue("DeltaResponse pair connu doit passer", result.isSuccess)
    }
}
