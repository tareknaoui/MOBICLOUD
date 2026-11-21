package com.mobicloud.data.instrumentation

import android.content.Context
import com.mobicloud.domain.usecase.experiments.ProtocolEventCsv
import com.mobicloud.domain.usecase.experiments.ProtocolEventRecorder
import com.mobicloud.domain.usecase.experiments.ProtocolEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Journal des événements protocolaires dans
 * /sdcard/Android/data/com.mobicloud/files/measurements/events.csv.
 * Écriture séquentielle en arrière-plan ; rotation à 5 Mo (events.1.csv).
 */
@Singleton
class CsvProtocolEventRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : ProtocolEventRecorder {

    private val channel = Channel<String>(capacity = 1_000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for (row in channel) {
                runCatching {
                    val dir = (context.getExternalFilesDir("measurements") ?: File(context.filesDir, "measurements"))
                        .apply { mkdirs() }
                    val f = File(dir, FILE_NAME)
                    if (f.length() > MAX_BYTES) {
                        val previous = File(dir, "events.1.csv")
                        previous.delete()
                        f.renameTo(previous)
                    }
                    if (!f.exists() || f.length() == 0L) f.writeText(ProtocolEventCsv.HEADER + "\n")
                    f.appendText(row + "\n")
                }
            }
        }
    }

    override fun record(type: ProtocolEventType, value: Double, detail: String) {
        // trySend : un journal saturé ne doit jamais bloquer ni ralentir le protocole.
        channel.trySend(ProtocolEventCsv.toRow(System.currentTimeMillis(), type, value, detail))
    }

    companion object {
        const val FILE_NAME = "events.csv"
        private const val MAX_BYTES = 5L * 1024 * 1024
    }
}
