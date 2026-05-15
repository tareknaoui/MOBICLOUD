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
import io.mockk.coVerify
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
    private lateinit var memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase
    private lateinit var sendMemberUpdateUseCase: SendMemberUpdateUseCase
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

    private val evictedEntity = validEntity.copy(status = "EVICTED")

    private fun validHb(ts: Long = now) = Heartbeat(nodeIdBytes, 100L, "1.2.3.4", 9090, ts, sig)

    @Before
    fun setUp() {
        memberDao = mockk()
        securityRepository = mockk()
        joinStateMachine = mockk()
        networkEventRepository = mockk(relaxed = true)
        memberSnapshotCacheUseCase = mockk(relaxed = true)
        sendMemberUpdateUseCase = mockk(relaxed = true)
        useCase = ProcessHeartbeatUseCase(
            memberDao, securityRepository, joinStateMachine, networkEventRepository,
            memberSnapshotCacheUseCase, sendMemberUpdateUseCase
        )
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
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns null
        val result = useCase(validHb())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnknownMemberException)
    }

    // 3. Signature invalide → failure
    @Test
    fun `signature invalide retourne failure`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)
        val result = useCase(validHb())
        assertTrue(result.isFailure)
    }

    // 4. Timestamp stale → StaleTimestampException
    @Test
    fun `timestamp hors fenetre retourne StaleTimestampException`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        val staleTs = now - BULLY_TIMESTAMP_WINDOW_MS - 1000L
        val result = useCase(validHb(ts = staleTs))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleTimestampException)
    }

    // 5. IP vide → failure (heartbeatSignedBytes rejette l'ip invalide avant signature)
    @Test
    fun `ipAddress vide retourne failure`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        val hbEmptyIp = Heartbeat(nodeIdBytes, 100L, "", 0, now, sig)
        val result = useCase(hbEmptyIp)
        assertTrue(result.isFailure)
    }

    // 6. Nominal → success + touchHeartbeat appelé
    @Test
    fun `nominal retourne success et touch heartbeat`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.touchHeartbeat(any(), any(), any(), any(), any()) } returns 1
        val result = useCase(validHb())
        assertTrue(result.isSuccess)
    }

    // 7. Race : membre supprimé entre vérif signature et touch → 0 lignes, log WARN, success
    @Test
    fun `race condition touchHeartbeat retourne 0 lignes mais success`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.touchHeartbeat(any(), any(), any(), any(), any()) } returns 0
        val result = useCase(validHb())
        assertTrue(result.isSuccess)
    }

    // C4 régression : HB avec port=0 (convention relay-bound) DOIT appeler touchHeartbeat.
    @Test
    fun `port zero relay-bound rafraichit lastSeen`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.touchHeartbeat(any(), any(), any(), any(), any()) } returns 1
        val hbPortZero = Heartbeat(nodeIdBytes, 100L, "1.2.3.4", 0, now, sig)
        val result = useCase(hbPortZero)
        assertTrue(result.isSuccess)
        coVerify { memberDao.touchHeartbeat(nodeIdHex, any(), 100L, "1.2.3.4", 0) }
    }

    // C7 régression : timestamp = Long.MIN_VALUE ne doit PAS bypass la fenêtre via overflow abs().
    @Test
    fun `timestamp Long MIN VALUE rejete sans overflow`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        val result = useCase(validHb(ts = Long.MIN_VALUE))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleTimestampException)
    }

    // C7 régression : timestamp = Long.MAX_VALUE → rejeté.
    @Test
    fun `timestamp Long MAX VALUE rejete`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns validEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        val result = useCase(validHb(ts = Long.MAX_VALUE))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleTimestampException)
    }

    // Re-admission : membre EVICTED avec HB valide → reactivateIfEvicted + snapshot + broadcast
    @Test
    fun `membre EVICTED readmis sur HB valide`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns evictedEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { memberDao.reactivateIfEvicted(any(), any(), any(), any(), any()) } returns 1
        val result = useCase(validHb())
        assertTrue(result.isSuccess)
        coVerify { memberDao.reactivateIfEvicted(nodeIdHex, any(), 100L, "1.2.3.4", 9090) }
        coVerify { memberSnapshotCacheUseCase.applyUpdate(any()) }
        coVerify { sendMemberUpdateUseCase.invoke(any()) }
    }

    // Re-admission : membre EVICTED avec signature invalide → rejeté, pas de reactivation
    @Test
    fun `membre EVICTED avec signature invalide non readmis`() = runTest {
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cid"))
        coEvery { memberDao.findByNodeIdAnyStatus(nodeIdHex) } returns evictedEntity
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)
        val result = useCase(validHb())
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { memberDao.reactivateIfEvicted(any(), any(), any(), any(), any()) }
    }
}
