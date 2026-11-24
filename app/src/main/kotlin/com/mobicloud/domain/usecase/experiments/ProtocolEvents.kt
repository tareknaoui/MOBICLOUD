package com.mobicloud.domain.usecase.experiments

/**
 * Journal horodaté des décisions du protocole, utilisé pour évaluer le circuit-breaker
 * par injection de pannes (réponse à R1-5) : quand un pair est vu inactif, quand le
 * circuit s'ouvre ou se referme, et ce que deviennent les réparations (émises, mises en
 * file, abandonnées, ou relâchées à la fermeture).
 */
enum class ProtocolEventType {
    PEER_INACTIVE,
    CIRCUIT_OPEN,
    CIRCUIT_CLOSE,
    REPAIR_EMITTED,
    REPAIR_BUFFERED,
    REPAIR_DROPPED,
    REPAIR_DRAINED
}

interface ProtocolEventRecorder {
    /** Non bloquant : l'écriture se fait en arrière-plan. */
    fun record(type: ProtocolEventType, value: Double = 0.0, detail: String = "")
}

object NoOpProtocolEventRecorder : ProtocolEventRecorder {
    override fun record(type: ProtocolEventType, value: Double, detail: String) = Unit
}

object ProtocolEventCsv {
    const val HEADER = "timestamp_ms,type,value,detail"

    fun toRow(timestampMs: Long, type: ProtocolEventType, value: Double, detail: String): String =
        listOf(
            timestampMs.toString(),
            type.name,
            String.format(java.util.Locale.ROOT, "%.4f", value),
            MeasurementCsv.escape(detail)
        ).joinToString(",")
}
