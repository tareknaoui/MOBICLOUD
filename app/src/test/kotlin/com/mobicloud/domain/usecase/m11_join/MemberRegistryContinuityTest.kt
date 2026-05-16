package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test critique FR-11.8 — Scénario T=6 :
 *   Alice (SP) meurt à T=91s.
 *   Bob gagne le Bully, repeuple cluster_members depuis le snapshot.
 *   Carol reste dans le cluster via la branche AC12 (Member + CoordinatorReceived)
 *   SANS re-JOIN.
 *
 * Ce test est l'évidence directe de la valeur thèse FR-11.8 ("Continuité post-Bully").
 */
class MemberRegistryContinuityTest {

    private val CLUSTER_ID = "CL-7F3A"
    private val aliceId = byteArrayOf(0xAA.toByte())
    private val bobId   = byteArrayOf(0xBB.toByte())
    private val carolId = byteArrayOf(0xCC.toByte())
    private val bobPubKey   = byteArrayOf(0x0B)
    private val carolPubKey = byteArrayOf(0x0C)

    private val carolMember = MemberInfo(
        nodeId = carolId, publicKey = carolPubKey,
        ipAddress = "10.0.0.3", port = 8080, freeBytes = 500L, role = MemberRole.MEMBER
    )
    private val bobMember = MemberInfo(
        nodeId = bobId, publicKey = bobPubKey,
        ipAddress = "10.0.0.2", port = 8080, freeBytes = 800L, role = MemberRole.MEMBER
    )

    private lateinit var networkRepo: NetworkEventRepository

    // Carol
    private lateinit var carolFsm: JoinStateMachine
    private lateinit var carolHeartbeatUseCase: MemberHeartbeatUseCase
    private lateinit var carolSnapshotCache: MemberSnapshotCacheUseCase

    // Bob
    private lateinit var bobFsm: JoinStateMachine
    private lateinit var bobRegistry: RamMemberRegistry
    private lateinit var bobSnapshotCache: MemberSnapshotCacheUseCase

    @Before
    fun setUp() {
        networkRepo = mockk(relaxed = true)

        // --- Carol ---
        carolHeartbeatUseCase = mockk(relaxed = true)
        carolSnapshotCache    = mockk(relaxed = true)
        carolFsm = JoinStateMachine(
            networkEventRepository          = networkRepo,
            sendJoinRequestUseCaseLazy      = dagger.Lazy { mockk(relaxed = true) },
            markSelfAsSuperPairUseCaseLazy  = dagger.Lazy { mockk(relaxed = true) },
            bullySoloElectionUseCaseLazy    = dagger.Lazy { mockk(relaxed = true) },
            memberHeartbeatUseCaseLazy      = dagger.Lazy { carolHeartbeatUseCase },
            monitorMemberLivenessUseCaseLazy = dagger.Lazy { mockk(relaxed = true) },
            memberSnapshotCacheUseCaseLazy  = dagger.Lazy { carolSnapshotCache },
            nodeSettingsRepository          = mockk(relaxed = true),
        )

        // --- Bob ---
        bobRegistry    = RamMemberRegistry()
        bobSnapshotCache = mockk(relaxed = true)
        bobFsm = JoinStateMachine(
            networkEventRepository          = networkRepo,
            sendJoinRequestUseCaseLazy      = dagger.Lazy { mockk(relaxed = true) },
            markSelfAsSuperPairUseCaseLazy  = dagger.Lazy { mockk(relaxed = true) },
            bullySoloElectionUseCaseLazy    = dagger.Lazy { mockk(relaxed = true) },
            memberHeartbeatUseCaseLazy      = dagger.Lazy { mockk(relaxed = true) },
            monitorMemberLivenessUseCaseLazy = dagger.Lazy { mockk(relaxed = true) },
            memberSnapshotCacheUseCaseLazy  = dagger.Lazy { bobSnapshotCache },
            nodeSettingsRepository          = mockk(relaxed = true),
        )
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private suspend fun driveToMember(fsm: JoinStateMachine, spId: ByteArray, snapshot: List<MemberInfo> = emptyList()) {
        fsm.transition(JoinEvent.CoordinatorReceived(spId, CLUSTER_ID))
        fsm.transition(
            JoinEvent.JoinAcceptReceived(
                JoinResponse.JoinAccept(
                    clusterId       = CLUSTER_ID,
                    superPairNodeId = spId,
                    memberSnapshot  = snapshot,
                    timestampMs     = 1_000L,
                    signatureBytes  = byteArrayOf()
                )
            )
        )
    }

    private fun buildMarkSelfForBob(): MarkSelfAsSuperPairUseCase {
        val secRepo = mockk<SecurityRepository>()
        val identityRepo = mockk<IdentityRepository>()
        val settingsRepo = mockk<NodeSettingsRepository>()
        val peerRepo = mockk<PeerRepository>(relaxed = true)

        coEvery { secRepo.getIdentity() } returns Result.success(
            NodeIdentity(nodeId = "bbbb", publicKeyBytes = bobPubKey, reliabilityScore = 0.9f)
        )
        coEvery { identityRepo.getIdentity() } returns Result.success(
            NodeIdentity(nodeId = "bbbb", publicKeyBytes = bobPubKey, reliabilityScore = 0.9f)
        )
        every { settingsRepo.observeFreeSpaceBytes() } returns MutableStateFlow(1024L)
        coEvery { settingsRepo.updateClusterId(any()) } returns Unit

        // Le snapshot que Bob connaît : carol était dans le cluster (reçu via MEMBER_UPDATE de Alice)
        every { bobSnapshotCache.snapshot() } returns listOf(carolMember)
        every { bobSnapshotCache.snapshotClusterId() } returns CLUSTER_ID

        return MarkSelfAsSuperPairUseCase(
            securityRepository               = secRepo,
            identityRepository               = identityRepo,
            nodeSettingsRepository           = settingsRepo,
            peerRepository                   = peerRepo,
            memberRegistry                   = bobRegistry,
            joinStateMachine                 = bobFsm,
            monitorMemberLivenessUseCaseLazy = dagger.Lazy { mockk(relaxed = true) },
            memberSnapshotCacheUseCaseLazy   = dagger.Lazy { bobSnapshotCache }
        )
    }

    // ─── Tests ──────────────────────────────────────────────────────────────────

    /**
     * AC12 — Carol reçoit COORDINATOR de Bob (nouveau SP, même clusterId) sans jamais
     * passer par l'état Rejoining. État final : Member(CLUSTER_ID, bob.nodeId).
     */
    @Test
    fun `carol_swap_sp_AC12_sans_rejoin`() = runTest {
        // Prépare Carol dans Member(CLUSTER_ID, alice)
        driveToMember(carolFsm, aliceId, listOf(carolMember, bobMember))

        val stateBefore = carolFsm.currentState.value
        assertTrue("Carol doit être Member avant l'événement",
            stateBefore is NodeJoinState.Member)
        assertTrue("SP initial = Alice",
            (stateBefore as NodeJoinState.Member).superPairNodeId.contentEquals(aliceId))

        // Alice meurt — Bob gagne Bully et diffuse COORDINATOR
        carolFsm.transition(
            JoinEvent.CoordinatorReceived(bobId, CLUSTER_ID)
        )

        val stateAfter = carolFsm.currentState.value
        assertTrue("Carol doit rester Member après swap SP", stateAfter is NodeJoinState.Member)
        val memberAfter = stateAfter as NodeJoinState.Member
        assertEquals("clusterId inchangé", CLUSTER_ID, memberAfter.clusterId)
        assertTrue("SP mis à jour vers Bob",
            memberAfter.superPairNodeId.contentEquals(bobId))
    }

    /**
     * Bob gagne le Bully et appelle MarkSelfAsSuperPairUseCase.
     * Son registre doit contenir Carol (depuis le snapshot) SANS qu'elle ait re-JOIN.
     */
    @Test
    fun `bob_gagne_bully_repeuple_registry_depuis_snapshot`() = runTest {
        // Bob était Member(CLUSTER_ID, alice) avant le timeout
        driveToMember(bobFsm, aliceId, listOf(carolMember))
        bobFsm.transition(JoinEvent.SpTimeoutDetected(aliceId))

        // Bob gagne le Bully
        val markSelf = buildMarkSelfForBob()
        markSelf.invoke(CLUSTER_ID)

        val members = bobRegistry.list()
        assertTrue("Registry de Bob doit contenir Carol sans re-JOIN",
            members.any { it.nodeId.contentEquals(carolId) })
        assertFalse("Alice (ancienne SP morte) ne doit PAS être dans le registry",
            members.any { it.nodeId.contentEquals(aliceId) })
    }

    /**
     * Scénario T=6 complet — FR-11.8 :
     *   T=0..90 : Alice est SP, Bob et Carol sont Members.
     *   T=91    : Alice meurt (silencieusement — pas de LEAVE).
     *   T=92    : Bob gagne Bully → SuperPair(CLUSTER_ID), registry = {bob, carol}.
     *   T=93    : Bob diffuse COORDINATOR → Carol swap SP (AC12) sans re-JOIN.
     *
     * Invariants post-T=6 :
     *   - carolFsm.state == Member(CLUSTER_ID, bob)         ← continuité sans re-JOIN
     *   - bobFsm.state   == SuperPair(CLUSTER_ID)           ← nouveau SP élu
     *   - bobRegistry contient carol                        ← cluster intact
     */
    @Test
    fun `scenario_t6_alice_meurt_bob_gagne_bully_carol_reste_sans_rejoin`() = runTest {
        // T=0 : Carol et Bob rejoignent le cluster via Alice SP
        driveToMember(carolFsm, aliceId, listOf(carolMember, bobMember))
        driveToMember(bobFsm, aliceId, listOf(carolMember, bobMember))

        // T=91 : Alice meurt → timeout détecté par Bob
        bobFsm.transition(JoinEvent.SpTimeoutDetected(aliceId))
        assertEquals("Bob doit être Rejoining après SpTimeout",
            NodeJoinState.Rejoining::class, bobFsm.currentState.value::class)

        // T=92 : Bob gagne le Bully → SuperPair, registry repeuplé depuis snapshot
        val markSelf = buildMarkSelfForBob()
        markSelf.invoke(CLUSTER_ID)

        assertEquals("Bob doit être SuperPair après victoire Bully",
            NodeJoinState.SuperPair(CLUSTER_ID), bobFsm.currentState.value)
        assertTrue("Registry Bob doit contenir Carol post-Bully",
            bobRegistry.list().any { it.nodeId.contentEquals(carolId) })

        // T=93 : Bob diffuse COORDINATOR → Carol swap SP sans re-JOIN (AC12)
        // Carol n'a PAS encore détecté le timeout Alice — elle reçoit le COORDINATOR avant
        val carolStateBefore = carolFsm.currentState.value
        assertTrue("Carol est encore Member(CLUSTER_ID, alice) à T=93",
            carolStateBefore is NodeJoinState.Member
                && (carolStateBefore as NodeJoinState.Member).superPairNodeId.contentEquals(aliceId))

        carolFsm.transition(
            JoinEvent.CoordinatorReceived(bobId, CLUSTER_ID)
        )

        // Invariants finaux FR-11.8
        val carolFinal = carolFsm.currentState.value
        assertTrue("Carol reste Member (pas de re-JOIN)", carolFinal is NodeJoinState.Member)
        assertEquals("Carol clusterId inchangé", CLUSTER_ID,
            (carolFinal as NodeJoinState.Member).clusterId)
        assertTrue("Carol pointe vers le nouveau SP Bob",
            carolFinal.superPairNodeId.contentEquals(bobId))

        // Carol n'est jamais passée par Rejoining → prouvé par le fait qu'elle est
        // directement Member(CLUSTER_ID, bob) sans avoir émis de JoinRequest
        assertFalse("Carol ne doit PAS être Rejoining",
            carolFinal is NodeJoinState.Rejoining)
    }
}
