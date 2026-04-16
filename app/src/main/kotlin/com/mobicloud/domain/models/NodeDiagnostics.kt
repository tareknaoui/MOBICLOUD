package com.mobicloud.domain.models

data class NodeDiagnostics(
    val batteryPercent: Int,
    val uptimeMs: Long,
    val networkType: NetworkType,
    val activePeerCount: Int,
    val reliabilityScore: Float
) {
    companion object {
        val DEFAULT = NodeDiagnostics(0, 0L, NetworkType.UNKNOWN, 0, 0f)
    }
}
