package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DhtLookupResponseMessage(
    @ProtoNumber(1) val blockId: String = "",
    @ProtoNumber(2) val nodeId: String = "",
    @ProtoNumber(3) val ipAddress: String = "",
    @ProtoNumber(4) val port: Int = 0,
    @ProtoNumber(5) val found: Boolean = false,
    @ProtoNumber(6) val timestamp: Long = 0L
)
