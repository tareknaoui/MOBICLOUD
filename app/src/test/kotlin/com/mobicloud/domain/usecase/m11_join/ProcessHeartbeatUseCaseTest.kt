package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.heartbeatSignedBytes
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ProcessHeartbeatUseCaseTest {

    private lateinit var memberDao: MemberDao
    private lateinit var securityRepository: SecurityRepository
    private lateinit var joinStateMachine: JoinStateMachine
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: ProcessHeartbeatUseCase

    private val nodeIdBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
    private val nodeIdHex = nodeIdBytes.toHexString()
    private val pubKey = byteArrayOf(0x01, 0x02)
    private val sig = byteArrayOf(0x10, 0x20)
    private val now = System.currentTimeMillis()

    private val validEntity = MemberEntity(
        nodeId = nodeIdHex, clusterId = "cid", publicKeyBytes = pubKey,
        ipAddress = "1.2.3.4", port = 9090,
        freeBytes = 100L, lastSeen = now - 5000L, role = "MEMBER", status = "ACTIVE"
    )

    private fun validHb(ts: Long = now) = Heartbeat(nodeIdBytes, 100L, "1.2.3.4", 9090, ts, sig)

    @Before
    fun setUp() {
        memberDao = mockk()
        securityRepository = mockk()
        joinStateMachine = mockk()
        networkEventRepository = mockk(relaxed = true)
        useCase = ProcessHeartbeatUseCase(memberDao, securityRepository, joinStateMachine, networkEventRepository)
    }

    // 1. Hors état SuperPair → no-op success
    @Test
    fun `hors etat SuperPair retourne success sans toucher dao`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.Undiscovered)
        val result = useCase(validHb())
        assertTrue(result.isSuccess)
    }

    // 2. Membre inconnu → UnknownMemberException
    @Test
    fun `membre inconnu retourne failure UnknownMemberException`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns null
        val result = useCase(validHb())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnknownMemberException)
    }

    // 3. Signature invalide → failure
    @Test
    fun `signature invalide retourne failure`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)
        val result = useCase(validHb())
        assertTrue(result.isFailure)
    }

    // 4. Timestamp stale → StaleTimestampException
    @Test
    fun `timestamp hors fenetre retourne StaleTimestampException`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        val staleTs = now - BULLY_TIMESTAMP_WINDOW_MS - 1000L
        val result = useCase(validHb(ts = staleTs))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleTimestampException)
    }

    // 5. IP vide → failure (post-fix H11/C2 : on rejette explicitement, plus de success silencieux).
    @Test
    fun `ipAddress vide retourne failure`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        val hbEmptyIp = Heartbeat(nodeIdBytes, 100L, "", 0, now, sig)
        val result = useCase(hbEmptyIp)
        assertTrue(result.isFailure)
    }

    // 6. Nominal → success + touchHeartbeat appelé
    @Test
    fun `nominal retourne success et touch heartbeat`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.touchHeartbeat(any(), any(), any(), any(), any()) } returns 1
        val result = useCase(validHb())
        assertTrue(result.isSuccess)
    }

    // 7. Race : membre supprimé entre vérif signature et touch → 0 lignes, log WARN, success
    @Test
    fun `race condition touchHeartbeat retourne 0 lignes mais success`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.touchHeartbeat(any(), any(), any(), any(), any()) } returns 0
        val result = useCase(validHb())
        assertTrue(result.isSuccess)
    }

    // C4 régression : HB avec port=0 (convention relay-bound) DOIT appeler touchHeartbeat.
    // Avant fix, `port !in 1..65535` rejetait silencieusement → lastSeen jamais rafraîchi.
    @Test
    fun `port zero relay-bound rafraichit lastSeen`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.touchHeartbeat(any(), any(), any(), any(), any()) } returns 1
        val hbPortZero = Heartbeat(nodeIdBytes, 100L, "1.2.3.4", 0, now, sig)
        val result = useCase(hbPortZero)
        assertTrue(result.isSuccess)
        io.mockk.coVerify { memberDao.touchHeartbeat(nodeIdHex, any(), 100L, "1.2.3.4", 0) }
    }

    // C7 régression : timestamp = Long.MIN_VALUE ne doit PAS bypass la fenêtre via overflow abs().
    @Test
    fun `timestamp Long MIN VALUE rejete sans overflow`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        val result = useCase(validHb(ts = Long.MIN_VALUE))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleTimestampException)
    }

    // C7 régression : timestamp = Long.MAX_VALUE → rejeté.
    @Test
    fun `timestamp Long MAX VALUE rejete`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeId(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        val result = useCase(validHb(ts = Long.MAX_VALUE))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleTimestampException)
    }
}
