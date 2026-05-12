package com.mobicloud.data.p2p.m11_join

import com.mobicloud.data.p2p.join.JoinNetworkClientImpl
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.JoinSubType
import com.mobicloud.domain.models.m11_join.Leave
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemberHeartbeatSenderImplTest {

    private lateinit var relay: RelayWebSocketClient
    private lateinit var sender: MemberHeartbeatSenderImpl

    private val destBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
    private val ts = 1_700_000_000_000L
    private val sig = byteArrayOf(0x01)

    @Before
    fun setUp() {
        relay = mockk()
        sender = MemberHeartbeatSenderImpl(relay, mockk(relaxed = true))
    }

    // 1. send() encode avec JOIN_MAGIC + HEARTBEAT.byte et blockId "HB-"
    @Test
    fun `send encode avec magic HEARTBEAT et blockId HB-`() = runTest {
        val blockIdSlot = slot<String>()
        val payloadSlot = slot<ByteArray>()
        coEvery { relay.uploadBlock(any(), capture(blockIdSlot), capture(payloadSlot)) } returns Result.success(Unit)

        val hb = Heartbeat(destBytes, 1000L, "1.2.3.4", 80, ts, sig)
        sender.send(destBytes, hb)

        assertTrue(blockIdSlot.captured.startsWith("HB-"))
        assertTrue(payloadSlot.captured[0] == JoinNetworkClientImpl.JOIN_MAGIC)
        assertTrue(payloadSlot.captured[1] == JoinSubType.HEARTBEAT.byte)
    }

    // 2. broadcast() itère sur N destinataires et ne propage pas l'échec d'un seul
    @Test
    fun `broadcast envoie a 2 destinataires meme si le premier echoue`() = runTest {
        val dest1 = byteArrayOf(0x01)
        val dest2 = byteArrayOf(0x02)
        coEvery { relay.uploadBlock("01", any(), any()) } returns Result.failure(Exception("err"))
        coEvery { relay.uploadBlock("02", any(), any()) } returns Result.success(Unit)

        val member = MemberInfo(dest1, sig, "1.2.3.4", 80, 100L, MemberRole.MEMBER)
        val update = MemberUpdate(MemberUpdateEvent.JOINED, member, byteArrayOf(), ts, sig)
        val result = sender.broadcast(listOf(dest1, dest2), update)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { relay.uploadBlock("02", any(), any()) }
    }

    // 3. sendLeave() utilise blockId "LV-"
    @Test
    fun `sendLeave blockId prefixe LV-`() = runTest {
        val blockIdSlot = slot<String>()
        coEvery { relay.uploadBlock(any(), capture(blockIdSlot), any()) } returns Result.success(Unit)

        val leave = Leave(destBytes, ts, sig)
        sender.sendLeave(destBytes, leave)

        assertTrue(blockIdSlot.captured.startsWith("LV-"))
    }
}
