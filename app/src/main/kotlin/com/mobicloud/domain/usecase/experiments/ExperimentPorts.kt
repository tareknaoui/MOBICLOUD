package com.mobicloud.domain.usecase.experiments

import java.io.File

/** Mesure l'énergie consommée pendant l'exécution de [block]. */
interface EnergyMeter {
    suspend fun <T> measure(block: suspend () -> T): Pair<T, EnergyReading>
}

/** Destination des mesures (fichier CSV sur l'appareil). */
interface MeasurementSink {
    suspend fun append(record: MeasurementRecord)
    /** Chemin lisible par `adb pull`, pour les logs. */
    val location: String
}

/** Informations sur l'appareil au moment de la mesure. */
interface ExperimentEnvironment {
    val deviceModel: String
    val androidApi: Int
    fun networkType(): String
    fun batteryPercent(): Int
    /** Répertoire de travail pour les fichiers de test générés. */
    fun scratchDir(): File
}

data class ExperimentConfig(
    val runId: String,
    val label: String,
    val fileSizeBytes: Long,
    val repetitions: Int,
    /** Vrai : chaque upload est suivi du download du même fichier. */
    val roundTrip: Boolean,
    val baseK: Int = 2,
    /** Pause entre deux répétitions, pour laisser le courant de la batterie revenir au repos. */
    val cooldownMs: Long = 5_000L
) {
    init {
        require(fileSizeBytes > 0) { "fileSizeBytes doit être > 0" }
        require(repetitions in 1..500) { "repetitions doit être dans [1, 500]" }
        require(baseK >= 1) { "baseK doit être >= 1" }
        require(cooldownMs >= 0) { "cooldownMs doit être >= 0" }
    }
}

data class ExperimentSummary(val attempted: Int, val succeeded: Int, val failed: Int)
