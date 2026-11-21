package com.mobicloud.domain.usecase.experiments

import java.util.Locale
import kotlin.math.abs

/**
 * Instrumentation des mesures physiques (réponse à R2-8 et R2-11) : chaque opération
 * mesurée produit une ligne CSV avec les durées par phase, le débit chronométré jusqu'au
 * dernier ACK du destinataire, et l'énergie mesurée par le BatteryManager.
 */
enum class ExperimentOperation { UPLOAD, DOWNLOAD }

/** Échantillon instantané de la batterie (unités brutes Android : µA et mV). */
data class EnergySample(val timeMs: Long, val currentRaw: Long, val voltageMilliVolts: Int)

/** Résultat d'une session de mesure d'énergie. */
data class EnergyReading(
    /** Énergie intégrée (trapèzes sur P = |I|·V), en millijoules ; null si moins de 2 échantillons. */
    val energyMilliJoules: Double?,
    /** Courant moyen en mA ; null sans échantillon. */
    val avgCurrentMilliAmps: Double?,
    /** Variation du compteur de charge (µAh), grossière mais indépendante de l'échantillonnage. */
    val chargeCounterDeltaMicroAh: Long?,
    val sampleCount: Int,
    /** Unité détectée du courant brut : "uA" (norme Android) ou "mA" (certains constructeurs). */
    val currentUnit: String,
    /** Mesure invalide si l'appareil était branché (le courant mesuré est alors celui de charge). */
    val wasCharging: Boolean
)

/** Une ligne du fichier de mesures. Les durées valent -1 quand la phase n'a pas eu lieu. */
data class MeasurementRecord(
    val runId: String,
    val label: String,
    val repetition: Int,
    val operation: ExperimentOperation,
    val timestampMs: Long,
    val deviceModel: String,
    val androidApi: Int,
    val networkType: String,
    val fileBytes: Long,
    val k: Int,
    val n: Int,
    /** Octets réellement transférés (fragments chiffrés × nombre d'hôtes par fragment). */
    val wireBytes: Long,
    val hostCount: Int,
    val selectMs: Long = -1,
    val encodeMs: Long = -1,
    val encryptMs: Long = -1,
    /** Upload : premier envoi → dernier ACK reçu. Download : première requête → k-ième fragment reçu. */
    val transferMs: Long = -1,
    val assembleMs: Long = -1,
    val totalMs: Long = -1,
    val energy: EnergyReading? = null,
    val batteryStartPct: Int = -1,
    val batteryEndPct: Int = -1,
    val success: Boolean,
    val error: String = ""
)

object MeasurementMath {

    /**
     * En dessous de ce courant médian (en valeur absolue), on considère que l'appareil rapporte
     * des mA et non des µA : un téléphone actif consomme 100 à 3000 mA, soit 10⁵ à 3·10⁶ µA.
     */
    const val MILLIAMP_HEURISTIC_THRESHOLD = 10_000L

    fun detectCurrentUnit(samples: List<EnergySample>): String {
        if (samples.isEmpty()) return "uA"
        val sorted = samples.map { abs(it.currentRaw) }.sorted()
        return if (sorted[sorted.size / 2] < MILLIAMP_HEURISTIC_THRESHOLD) "mA" else "uA"
    }

    private fun toMicroAmps(raw: Long, unit: String): Double = abs(raw).toDouble() * if (unit == "mA") 1000.0 else 1.0

    /** Énergie en mJ par la méthode des trapèzes ; null si moins de 2 échantillons. */
    fun integrateEnergyMilliJoules(samples: List<EnergySample>, unit: String = detectCurrentUnit(samples)): Double? {
        if (samples.size < 2) return null
        var joules = 0.0
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            val dtSeconds = (b.timeMs - a.timeMs) / 1000.0
            if (dtSeconds <= 0) continue
            val pa = toMicroAmps(a.currentRaw, unit) / 1e6 * (a.voltageMilliVolts / 1000.0)
            val pb = toMicroAmps(b.currentRaw, unit) / 1e6 * (b.voltageMilliVolts / 1000.0)
            joules += (pa + pb) / 2.0 * dtSeconds
        }
        return joules * 1000.0
    }

    fun averageCurrentMilliAmps(samples: List<EnergySample>, unit: String = detectCurrentUnit(samples)): Double? =
        if (samples.isEmpty()) null else samples.map { toMicroAmps(it.currentRaw, unit) / 1000.0 }.average()

    fun reading(
        samples: List<EnergySample>,
        chargeCounterDeltaMicroAh: Long?,
        wasCharging: Boolean
    ): EnergyReading {
        val unit = detectCurrentUnit(samples)
        return EnergyReading(
            energyMilliJoules = integrateEnergyMilliJoules(samples, unit),
            avgCurrentMilliAmps = averageCurrentMilliAmps(samples, unit),
            chargeCounterDeltaMicroAh = chargeCounterDeltaMicroAh,
            sampleCount = samples.size,
            currentUnit = unit,
            wasCharging = wasCharging
        )
    }

    /** Débit en Mbit/s (10⁶ bits par seconde) ; null si la durée est inconnue ou nulle. */
    fun megabitsPerSecond(bytes: Long, durationMs: Long): Double? =
        if (durationMs <= 0 || bytes < 0) null else bytes * 8.0 / (durationMs / 1000.0) / 1e6
}

/** Sérialisation CSV indépendante de la langue du téléphone (point décimal, pas de virgule). */
object MeasurementCsv {

    val HEADER: String = listOf(
        "run_id", "label", "repetition", "operation", "timestamp_ms", "device_model", "android_api",
        "network_type", "file_bytes", "k", "n", "wire_bytes", "host_count",
        "select_ms", "encode_ms", "encrypt_ms", "transfer_ms", "assemble_ms", "total_ms",
        "goodput_mbps", "wire_mbps",
        "energy_mj", "energy_mj_per_mb", "avg_current_ma", "charge_delta_uah", "energy_samples",
        "current_unit", "was_charging", "battery_start_pct", "battery_end_pct", "success", "error"
    ).joinToString(",")

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun num(value: Double?, decimals: Int = 3): String =
        value?.let { String.format(Locale.ROOT, "%.${decimals}f", it) } ?: ""

    fun toRow(r: MeasurementRecord): String {
        val energy = r.energy
        val megabytes = r.fileBytes / 1_000_000.0
        val energyPerMb = energy?.energyMilliJoules?.takeIf { megabytes > 0 }?.let { it / megabytes }
        return listOf(
            escape(r.runId), escape(r.label), r.repetition.toString(), r.operation.name, r.timestampMs.toString(),
            escape(r.deviceModel), r.androidApi.toString(), escape(r.networkType),
            r.fileBytes.toString(), r.k.toString(), r.n.toString(), r.wireBytes.toString(), r.hostCount.toString(),
            r.selectMs.toString(), r.encodeMs.toString(), r.encryptMs.toString(), r.transferMs.toString(),
            r.assembleMs.toString(), r.totalMs.toString(),
            num(MeasurementMath.megabitsPerSecond(r.fileBytes, r.transferMs)),
            num(MeasurementMath.megabitsPerSecond(r.wireBytes, r.transferMs)),
            num(energy?.energyMilliJoules, 1), num(energyPerMb, 1), num(energy?.avgCurrentMilliAmps, 1),
            energy?.chargeCounterDeltaMicroAh?.toString() ?: "", (energy?.sampleCount ?: 0).toString(),
            energy?.currentUnit ?: "", (energy?.wasCharging ?: false).toString(),
            r.batteryStartPct.toString(), r.batteryEndPct.toString(), r.success.toString(), escape(r.error)
        ).joinToString(",")
    }
}
