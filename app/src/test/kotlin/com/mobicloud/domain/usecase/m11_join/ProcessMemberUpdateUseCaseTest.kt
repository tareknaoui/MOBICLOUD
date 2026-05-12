package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T1 (review 2026-05-12) — Couvre les 3 branches AC14 de [ProcessMemberUpdateUseCase] :
 *  1. SP signataire connu + signature valide + ts dans la fenêtre  → `Applied` + applyUpdate
 *  2. fromNodeId != SP courant (ancien SP ou inconnu)               → `Ignored`, pas d'applyUpdate
 *  3. Signature invalide                                            → `Ignored`, pas d'applyUpdate
 *  + Bonus : timestamp hors fenêtre → `Ignored` (anti-replay).
 */
class ProcessMemberUpdateUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: ProcessMemberUpdateUseCase

    private val spNodeId = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
    private val spPubKey = byteArrayOf(0x01, 0x02)
    private val spHex = spNodeId.toHexString()
    private val targetNodeId = byteArrayOf(0xCC.toByte(), 0xDD.toByte())
    private val sig = byteArrayOf(0x10, 0x20)
    private val virtualNow = 1_700_000_000_000L

    private val spMember = MemberInfo(
        nodeId = spNodeId,
        publicKey = spPubKey,
        ipAddress = "10.0.0.1",
        port = 9090,
        freeBytes = 1000L,
        role = MemberRole.SUPER_PAIR
    )

    private fun joinedUpdate(ts: Long = virtualNow) = MemberUpdate(
        event = MemberUpdateEvent.JOINED,
        member = MemberInfo(
            nodeId = targetNodeId, publicKey = byteArrayOf(),
            ipAddress = "10.0.0.2", port = 9091, freeBytes = 500L, role = MemberRole.MEMBER
        ),
        leftNodeId = byteArrayOf(),
        timestampMs = ts,
        signatureBytes = sig
    )

    @Before
    fun setUp() {
        securityRepository = mockk()
        memberSnapshotCacheUseCase = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        useCase = ProcessMemberUpdateUseCase(
            securityRepository, memberSnapshotCacheUseCase, networkEventRepository,
            clock = { virtualNow }
        )
    }

    // Branche 1 — nominal : SP connu + sig valide + ts dans fenêtre
    @Test
    fun `MEMBER_UPDATE valide est applique`() = runTest {
        coEvery { memberSnapshotCacheUseCase.inMemory } returns MutableStateFlow(listOf(spMember))
        coEvery { securityRepository.verifySignature(any(), any(), spPubKey) } returns Result.success(true)

        val outcome = useCase(spHex, joinedUpdate())

        assertTrue(outcome is ProcessMemberUpdateUseCase.Result.Applied)
        coVerify(exactly = 1) { memberSnapshotCacheUseCase.applyUpdate(any()) }
    }

    // Branche 2a — fromNodeId inconnu (jamais vu dans inMemoryRegistry)
    @Test
    fun `MEMBER_UPDATE d'un fromNodeId inconnu est ignore`() = runTest {
        coEvery { memberSnapshotCacheUseCase.inMemory } returns MutableStateFlow(listOf(spMember))
        val unknownHex = "deadbeef"

        val outcome = useCase(unknownHex, joinedUpdate())

        assertTrue(outcome is ProcessMemberUpdateUseCase.Result.Ignored)
        assertEquals("fromNodeId=deadbeef pas SP courant", (outcome as ProcessMemberUpdateUseCase.Result.Ignored).reason)
        coVerify(exactly = 0) { memberSnapshotCacheUseCase.applyUpdate(any()) }
    }

    // Branche 2b — fromNodeId est un MEMBER (pas SUPER_PAIR) — ancien SP qui a perdu son rôle
    @Test
    fun `MEMBER_UPDATE d'un ancien SP devenu MEMBER est ignore`() = runTest {
        val demotedSp = spMember.copy(role = MemberRole.MEMBER)
        coEvery { memberSnapshotCacheUseCase.inMemory } returns MutableStateFlow(listOf(demotedSp))

        val outcome = useCase(spHex, joinedUpdate())

        assertTrue(outcome is ProcessMemberUpdateUseCase.Result.Ignored)
        coVerify(exactly = 0) { memberSnapshotCacheUseCase.applyUpdate(any()) }
    }

    // Branche 3 — signature invalide
    @Test
    fun `MEMBER_UPDATE avec signature invalide est ignore`() = runTest {
        coEvery { memberSnapshotCacheUseCase.inMemory } returns MutableStateFlow(listOf(spMember))
        coEvery { securityRepository.verifySignature(any(), any(), spPubKey) } returns Result.success(false)

        val outcome = useCase(spHex, joinedUpdate())

        assertTrue(outcome is ProcessMemberUpdateUseCase.Result.Ignored)
        assertEquals("signature invalide", (outcome as ProcessMemberUpdateUseCase.Result.Ignored).reason)
        coVerify(exactly = 0) { memberSnapshotCacheUseCase.applyUpdate(any()) }
    }

    // Bonus — timestamp stale (anti-replay)
    @Test
    fun `MEMBER_UPDATE avec timestamp stale est ignore`() = runTest {
        coEvery { memberSnapshotCacheUseCase.inMemory } returns MutableStateFlow(listOf(spMember))
        coEvery { securityRepository.verifySignature(any(), any(), spPubKey) } returns Result.success(true)

        val staleTs = virtualNow - BULLY_TIMESTAMP_WINDOW_MS - 1_000L
        val outcome = useCase(spHex, joinedUpdate(ts = staleTs))

        assertTrue(outcome is ProcessMemberUpdateUseCase.Result.Ignored)
        assertEquals("timestamp stale", (outcome as ProcessMemberUpdateUseCase.Result.Ignored).reason)
        coVerify(exactly = 0) { memberSnapshotCacheUseCase.applyUpdate(any()) }
    }

    // C7 régression — timestamp Long.MIN_VALUE ne doit pas bypass via overflow
    @Test
    fun `MEMBER_UPDATE timestamp Long MIN VALUE est ignore`() = runTest {
        coEvery { memberSnapshotCacheUseCase.inMemory } returns MutableStateFlow(listOf(spMember))
        coEvery { securityRepository.verifySignature(any(), any(), spPubKey) } returns Result.success(true)

        val outcome = useCase(spHex, joinedUpdate(ts = Long.MIN_VALUE))

        assertTrue(outcome is ProcessMemberUpdateUseCase.Result.Ignored)
    }
}
