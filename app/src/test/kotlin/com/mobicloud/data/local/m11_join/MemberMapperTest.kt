package com.mobicloud.data.local.m11_join

import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberMapperTest {

    private val publicKey = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    private val nodeIdBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
    private val nodeIdHex = "aabbccdd"

    private fun memberInfo(role: MemberRole = MemberRole.MEMBER) = MemberInfo(
        nodeId = nodeIdBytes,
        publicKey = publicKey,
        ipAddress = "192.168.1.1",
        port = 9090,
        freeBytes = 1_000_000L,
        role = role
    )

    // 1. MEMBER → ACTIVE
    @Test
    fun `MEMBER vers entity et retour`() {
        val info = memberInfo()
        val entity = info.toEntity("cid", 1000L)
        assertEquals(nodeIdHex, entity.nodeId)
        assertEquals("MEMBER", entity.role)
        assertEquals("ACTIVE", entity.status)
        val back = entity.toMemberInfo()
        assertArrayEquals(nodeIdBytes, back.nodeId)
        assertEquals(MemberRole.MEMBER, back.role)
    }

    // 2. SUPER_PAIR → ACTIVE
    @Test
    fun `SUPER_PAIR round-trip`() {
        val info = memberInfo(role = MemberRole.SUPER_PAIR)
        val entity = info.toEntity("cid", 3000L)
        assertEquals("SUPER_PAIR", entity.role)
        assertEquals(MemberRole.SUPER_PAIR, entity.toMemberInfo().role)
    }

    // 3. MEMBER → EVICTED
    @Test
    fun `status EVICTED preserve`() {
        val info = memberInfo()
        val entity = info.toEntity("cid", 5000L, MemberStatus.EVICTED)
        assertEquals("EVICTED", entity.status)
    }

    // 4. SUPER_PAIR → EVICTED
    @Test
    fun `SUPER_PAIR EVICTED preserve`() {
        val info = memberInfo(role = MemberRole.SUPER_PAIR)
        val entity = info.toEntity("cid", 6000L, MemberStatus.EVICTED)
        assertEquals("EVICTED", entity.status)
    }

    // 5. publicKeyBytes préservés fidèlement
    @Test
    fun `publicKey round-trip`() {
        val info = memberInfo()
        val entity = info.toEntity("cid", 9000L)
        assertArrayEquals(publicKey, entity.publicKeyBytes)
        assertArrayEquals(publicKey, entity.toMemberInfo().publicKey)
    }

    // 6. ipAddress et port préservés
    @Test
    fun `ipAddress et port round-trip`() {
        val entity = memberInfo().toEntity("cid", 10000L)
        assertEquals("192.168.1.1", entity.ipAddress)
        assertEquals(9090, entity.port)
        val back = entity.toMemberInfo()
        assertEquals("192.168.1.1", back.ipAddress)
        assertEquals(9090, back.port)
    }

    // 7. freeBytes préservé
    @Test
    fun `freeBytes round-trip`() {
        val entity = memberInfo().toEntity("cid", 11000L)
        assertEquals(1_000_000L, entity.freeBytes)
        assertEquals(1_000_000L, entity.toMemberInfo().freeBytes)
    }

    // 8. toMemberInfoList fonctionne sur une liste
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
