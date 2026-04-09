package com.mobicloud.domain.models

data class Peer(
    val identity: NodeIdentity,
    val lastSeenTimestampMs: Long
)
