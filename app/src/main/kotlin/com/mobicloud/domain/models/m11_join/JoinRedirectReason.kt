package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
enum class JoinRedirectReason {
    OUT_OF_RADIUS, CLUSTER_FULL, INVALID_SIGNATURE, BLACKLISTED, INVALID_STATE
}
