package com.mobicloud.data.local.m11_join

import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MemberMapperTest {

    private val publicKey = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    private val nodeIdBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
    private val nodeIdHex = "aabbccdd"

    private fun memberInfo(
        role: MemberRole = MemberRole.MEMBER,
        gps: Boolean = false
    ) = MemberInfo(
        nodeId = nodeIdBytes,
        publicKey = publicKey,
        ipAddress = "192.168.1.1",
        port = 9090,
        gpsLatitude = if (gps) 36.7 else null,
        gpsLongitude = if (gps) 3.1 else null,
        freeBytes = 1_000_000L,
        role = role
    )

    // 1. MEMBER sans GPS → ACTIVE
    @Test
    fun `MEMBER sans GPS vers entity et retour`() {
        val info = memberInfo()
        val entity = info.toEntity("cid", 1000L)
        assertEquals(nodeIdHex, entity.nodeId)
        assertEquals("MEMBER", entity.role)
        assertEquals("ACTIVE", entity.status)
        assertNull(entity.gpsLatitude)
        val back = entity.toMemberInfo()
        assertArrayEquals(nodeIdBytes, back.nodeId)
        assertEquals(MemberRole.MEMBER, back.role)
    }

    // 2. MEMBER avec GPS → ACTIVE
    @Test
    fun `MEMBER avec GPS round-trip`() {
        val info = memberInfo(gps = true)
        val entity = info.toEntity("cid", 2000L)
        assertNotNull(entity.gpsLatitude)
        assertNotNull(entity.gpsLongitude)
        val back = entity.toMemberInfo()
        assertEquals(36.7, back.gpsLatitude!!, 1e-9)
        assertEquals(3.1, back.gpsLongitude!!, 1e-9)
    }

    // 3. SUPER_PAIR sans GPS → ACTIVE
    @Test
    fun `SUPER_PAIR sans GPS round-trip`() {
        val info = memberInfo(role = MemberRole.SUPER_PAIR)
        val entity = info.toEntity("cid", 3000L)
        assertEquals("SUPER_PAIR", entity.role)
        assertEquals(MemberRole.SUPER_PAIR, entity.toMemberInfo().role)
    }

    // 4. SUPER_PAIR avec GPS → ACTIVE
    @Test
    fun `SUPER_PAIR avec GPS round-trip`() {
        val info = memberInfo(role = MemberRole.SUPER_PAIR, gps = true)
        val entity = info.toEntity("cid", 4000L)
        assertEquals("SUPER_PAIR", entity.role)
        assertNotNull(entity.gpsLatitude)
    }

    // 5. MEMBER sans GPS → EVICTED
    @Test
    fun `status EVICTED preserve`() {
        val info = memberInfo()
        val entity = info.toEntity("cid", 5000L, MemberStatus.EVICTED)
        assertEquals("EVICTED", entity.status)
    }

    // 6. SUPER_PAIR sans GPS → EVICTED
    @Test
    fun `SUPER_PAIR EVICTED preserve`() {
        val info = memberInfo(role = MemberRole.SUPER_PAIR)
        val entity = info.toEntity("cid", 6000L, MemberStatus.EVICTED)
        assertEquals("EVICTED", entity.status)
    }

    // 7. MEMBER avec GPS → EVICTED
    @Test
    fun `MEMBER GPS EVICTED round-trip`() {
        val info = memberInfo(gps = true)
        val entity = info.toEntity("cid", 7000L, MemberStatus.EVICTED)
        assertEquals("EVICTED", entity.status)
        assertNotNull(entity.gpsLatitude)
    }

    // 8. SUPER_PAIR avec GPS → EVICTED
    @Test
    fun `SUPER_PAIR GPS EVICTED round-trip`() {
        val info = memberInfo(role = MemberRole.SUPER_PAIR, gps = true)
        val entity = info.toEntity("cid", 8000L, MemberStatus.EVICTED)
        assertEquals("EVICTED", entity.status)
    }

    // 9. publicKeyBytes préservés fidèlement
    @Test
    fun `publicKey round-trip`() {
        val info = memberInfo()
        val entity = info.toEntity("cid", 9000L)
        assertArrayEquals(publicKey, entity.publicKeyBytes)
        assertArrayEquals(publicKey, entity.toMemberInfo().publicKey)
    }

    // 10. ipAddress et port préservés
    @Test
    fun `ipAddress et port round-trip`() {
        val entity = memberInfo().toEntity("cid", 10000L)
        assertEquals("192.168.1.1", entity.ipAddress)
        assertEquals(9090, entity.port)
        val back = entity.toMemberInfo()
        assertEquals("192.168.1.1", back.ipAddress)
        assertEquals(9090, back.port)
    }

    // 11. freeBytes préservé
    @Test
    fun `freeBytes round-trip`() {
        val entity = memberInfo().toEntity("cid", 11000L)
        assertEquals(1_000_000L, entity.freeBytes)
        assertEquals(1_000_000L, entity.toMemberInfo().freeBytes)
    }

    // 12. toMemberInfoList fonctionne sur une liste
    @Test
    fun `toMemberInfoList sur liste de 2 elements`() {
        val entities = listOf(
            memberInfo().toEntity("cid", 1000L),
            memberInfo(role = MemberRole.SUPER_PAIR).toEntity("cid", 2000L)
        )
        val list = entities.toMemberInfoList()
        assertEquals(2, list.size)
        assertEquals(MemberRole.MEMBER, list[0].role)
        assertEquals(MemberRole.SUPER_PAIR, list[1].role)
    }
}
