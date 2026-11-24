package com.mobicloud.data.instrumentation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.mobicloud.domain.usecase.experiments.EnergyMeter
import com.mobicloud.domain.usecase.experiments.EnergyReading
import com.mobicloud.domain.usecase.experiments.EnergySample
import com.mobicloud.domain.usecase.experiments.ExperimentEnvironment
import com.mobicloud.domain.usecase.experiments.MeasurementCsv
import com.mobicloud.domain.usecase.experiments.MeasurementMath
import com.mobicloud.domain.usecase.experiments.MeasurementRecord
import com.mobicloud.domain.usecase.experiments.MeasurementSink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Énergie mesurée par échantillonnage de BATTERY_PROPERTY_CURRENT_NOW (courant instantané)
 * et de la tension batterie, intégrés par la méthode des trapèzes ([MeasurementMath]).
 * Le compteur de charge (CHARGE_COUNTER) est relevé en début et fin comme contrôle grossier.
 *
 * Limites, à reporter dans l'article : la mesure inclut toute la consommation du téléphone
 * (écran, autres applications), la résolution dépend du constructeur, et elle est invalide
 * quand le téléphone est branché (colonne was_charging).
 */
@Singleton
class BatteryEnergyMeter @Inject constructor(
    @ApplicationContext private val context: Context
) : EnergyMeter {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    override suspend fun <T> measure(block: suspend () -> T): Pair<T, EnergyReading> = coroutineScope {
        val samples = Collections.synchronizedList(mutableListOf<EnergySample>())
        val chargeStart = chargeCounter()
        val chargingAtStart = isCharging()
        val sampler = launch(Dispatchers.Default) {
            while (isActive) {
                samples += sample()
                delay(SAMPLE_PERIOD_MS)
            }
        }
        val result = try {
            block()
        } finally {
            sampler.cancel()
        }
        samples += sample()
        val chargeEnd = chargeCounter()
        val delta = if (chargeStart != null && chargeEnd != null) chargeStart - chargeEnd else null
        result to MeasurementMath.reading(
            samples = synchronized(samples) { samples.toList() },
            chargeCounterDeltaMicroAh = delta,
            wasCharging = chargingAtStart || isCharging()
        )
    }

    private fun sample(): EnergySample = EnergySample(
        timeMs = System.currentTimeMillis(),
        currentRaw = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
        voltageMilliVolts = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    )

    private fun chargeCounter(): Long? =
        batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it != Long.MIN_VALUE && it > 0 }

    private fun isCharging(): Boolean =
        (batteryIntent()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    private fun batteryIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    companion object {
        const val SAMPLE_PERIOD_MS = 200L
    }
}

/**
 * Fichier CSV dans le stockage externe privé de l'application, récupérable sans root :
 * `adb pull /sdcard/Android/data/com.mobicloud/files/measurements/measurements.csv`.
 */
@Singleton
class CsvMeasurementSink @Inject constructor(
    @ApplicationContext private val context: Context
) : MeasurementSink {

    private val mutex = Mutex()

    private val file: File
        get() = File(context.getExternalFilesDir("measurements") ?: File(context.filesDir, "measurements"), FILE_NAME)

    override val location: String get() = file.absolutePath

    override suspend fun append(record: MeasurementRecord) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val f = file.apply { parentFile?.mkdirs() }
            if (!f.exists() || f.length() == 0L) f.writeText(MeasurementCsv.HEADER + "\n")
            f.appendText(MeasurementCsv.toRow(record) + "\n")
        }
    }

    companion object {
        const val FILE_NAME = "measurements.csv"
    }
}

@Singleton
class AndroidExperimentEnvironment @Inject constructor(
    @ApplicationContext private val context: Context
) : ExperimentEnvironment {

    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
    override val androidApi: Int = Build.VERSION.SDK_INT

    override fun networkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "NONE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    override fun batteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun scratchDir(): File = File(context.cacheDir, "experiments").apply { mkdirs() }
}
