package com.mobicloud.data.instrumentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mobicloud.domain.usecase.experiments.ExperimentConfig
import com.mobicloud.domain.usecase.experiments.MeasurementSink
import com.mobicloud.domain.usecase.experiments.RunExperimentUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.ParityPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Exécute une campagne de mesures à la fois, hors de tout écran. */
@Singleton
class ExperimentRunner @Inject constructor(
    private val runExperiment: Provider<RunExperimentUseCase>,
    private val sink: MeasurementSink
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Synchronized
    fun start(config: ExperimentConfig): Boolean {
        if (job?.isActive == true) {
            Log.w(TAG, "EXPERIMENT BUSY : une campagne est déjà en cours, commande ignorée")
            return false
        }
        job = scope.launch {
            Log.i(TAG, "EXPERIMENT START $config → ${sink.location}")
            try {
                val summary = runExperiment.get()(config) { line -> Log.i(TAG, line) }
                Log.i(TAG, "EXPERIMENT DONE label=${config.label} ok=${summary.succeeded} failed=${summary.failed} file=${sink.location}")
            } catch (e: CancellationException) {
                Log.i(TAG, "EXPERIMENT STOPPED label=${config.label}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "EXPERIMENT ERROR label=${config.label} : ${e.message}", e)
            }
        }
        return true
    }

    @Synchronized
    fun stop() {
        job?.cancel()
    }

    companion object {
        const val TAG = "MobiCloud:EXP"
    }
}

/**
 * Point d'entrée adb des campagnes de mesures (voir scripts/experiments/run-experiment.ps1).
 *
 * Protégé par la permission système DUMP : seul le shell adb (et le système) peut l'envoyer,
 * aucune autre application installée ne peut déclencher d'upload. L'intent doit être
 * explicite (-n), les broadcasts implicites n'atteignant plus les récepteurs du manifeste.
 *
 * adb shell am broadcast -n com.mobicloud/com.mobicloud.data.instrumentation.ExperimentCommandReceiver \
 *   -a com.mobicloud.action.RUN_EXPERIMENT --es label wifi_10mb --ei size_kb 10240 --ei reps 30 \
 *   --ez round_trip true --ei k 2 --ei cooldown_s 5
 */
@AndroidEntryPoint
class ExperimentCommandReceiver : BroadcastReceiver() {

    @Inject lateinit var runner: ExperimentRunner

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RUN -> {
                val config = runCatching {
                    ExperimentConfig(
                        runId = intent.getStringExtra(EXTRA_RUN_ID) ?: System.currentTimeMillis().toString(),
                        label = intent.getStringExtra(EXTRA_LABEL) ?: "default",
                        fileSizeBytes = intent.getIntExtra(EXTRA_SIZE_KB, 1024).toLong() * 1024L,
                        repetitions = intent.getIntExtra(EXTRA_REPS, 10),
                        roundTrip = intent.getBooleanExtra(EXTRA_ROUND_TRIP, true),
                        baseK = intent.getIntExtra(EXTRA_K, ParityPolicy.DEFAULT_K),
                        cooldownMs = intent.getIntExtra(EXTRA_COOLDOWN_S, 5).toLong() * 1000L
                    )
                }.getOrElse { e ->
                    Log.e(ExperimentRunner.TAG, "EXPERIMENT ERROR paramètres invalides : ${e.message}")
                    return
                }
                runner.start(config)
            }
            ACTION_STOP -> runner.stop()
        }
    }

    companion object {
        const val ACTION_RUN = "com.mobicloud.action.RUN_EXPERIMENT"
        const val ACTION_STOP = "com.mobicloud.action.STOP_EXPERIMENT"
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SIZE_KB = "size_kb"
        const val EXTRA_REPS = "reps"
        const val EXTRA_ROUND_TRIP = "round_trip"
        const val EXTRA_K = "k"
        const val EXTRA_COOLDOWN_S = "cooldown_s"
    }
}
