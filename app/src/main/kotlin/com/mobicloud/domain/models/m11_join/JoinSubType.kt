package com.mobicloud.domain.models.m11_join

/** Sous-types Epic 11 encapsulés dans le préfixe 1-octet d'un `FORWARD` (0x07). */
enum class JoinSubType(val byte: Byte) {
    HEARTBEAT(0x01),
    MEMBER_UPDATE(0x02),
    LEAVE(0x03),
    JOIN_REQUEST(0x04),
    JOIN_ACCEPT(0x05),
    JOIN_REDIRECT(0x06);

    companion object {
        val bytes: Set<Byte> = values().map { it.byte }.toSet()
    }
}

fun Byte.toJoinSubType(): JoinSubType? = JoinSubType.values().find { it.byte == this }
