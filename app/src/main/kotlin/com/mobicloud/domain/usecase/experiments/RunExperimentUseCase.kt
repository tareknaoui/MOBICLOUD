package com.mobicloud.domain.usecase.experiments

import com.mobicloud.core.security.FragmentCipherUseCase
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.LocalizeFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadProgressState
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.SelectOptimalPeersUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Répète une opération de stockage (upload, ou upload puis download) et enregistre une
 * ligne de mesure par opération. Enchaîne exactement les mêmes use cases que l'explorateur
 * (sélection, encodage RS, chiffrement, distribution ; localisation, téléchargement,
 * déchiffrement et décodage), sur des fichiers aléatoires générés pour chaque répétition.
 *
 * Chronométrage : l'upload se termine quand le dernier destinataire a renvoyé son ACK
 * (retour de distribute) et non quand le relais a accepté les octets. Le download se termine
 * quand k fragments sont reçus ; l'assemblage est chronométré à part.
 */
class RunExperimentUseCase @Inject constructor(
    private val selectOptimalPeers: SelectOptimalPeersUseCase,
    private val encodeErasureFragments: EncodeErasureFragmentsUseCase,
    private val fragmentCipher: FragmentCipherUseCase,
    private val distributeEncryptedBlocks: DistributeEncryptedBlocksUseCase,
    private val securityRepository: SecurityRepository,
    private val localizeFileBlocks: LocalizeFileBlocksUseCase,
    private val downloadFileBlocks: DownloadFileBlocksUseCase,
    private val assembleDownloadedFile: AssembleDownloadedFileUseCase,
    private val catalogRepository: CatalogRepository,
    private val energyMeter: EnergyMeter,
    private val sink: MeasurementSink,
    private val environment: ExperimentEnvironment
) {
    internal var clockMs: () -> Long = System::currentTimeMillis
    private val random = SecureRandom()

    suspend operator fun invoke(
        config: ExperimentConfig,
        onProgress: (String) -> Unit = {}
    ): ExperimentSummary {
        var attempted = 0
        var succeeded = 0
        for (rep in 1..config.repetitions) {
            val upload = runUpload(config, rep)
            attempted++
            sink.append(upload.record)
            if (upload.record.success) succeeded++
            onProgress(progressLine(upload.record))

            if (config.roundTrip) {
                val download = upload.fileHash?.takeIf { upload.record.success }
                    ?.let { runDownload(config, rep, it) }
                    ?: baseRecord(config, rep, ExperimentOperation.DOWNLOAD, upload.record.fileBytes)
                        .copy(success = false, error = "skipped: upload failed")
                attempted++
                sink.append(download)
                if (download.success) succeeded++
                onProgress(progressLine(download))
            }

            // Fichier de test : retiré de l'explorateur (corbeille locale), best-effort.
            upload.fileHash?.let { runCatching { catalogRepository.moveToTrash(it) } }
            if (rep < config.repetitions && config.cooldownMs > 0) delay(config.cooldownMs)
        }
        return ExperimentSummary(attempted, succeeded, attempted - succeeded)
    }

    private class UploadOutcome(val record: MeasurementRecord, val fileHash: String?)

    private fun baseRecord(config: ExperimentConfig, rep: Int, op: ExperimentOperation, fileBytes: Long) =
        MeasurementRecord(
            runId = config.runId, label = config.label, repetition = rep, operation = op,
            timestampMs = clockMs(), deviceModel = environment.deviceModel, androidApi = environment.androidApi,
            networkType = environment.networkType(), fileBytes = fileBytes, k = config.baseK, n = 0,
            wireBytes = 0, hostCount = 0, success = false
        )

    private suspend fun runUpload(config: ExperimentConfig, rep: Int): UploadOutcome {
        var record = baseRecord(config, rep, ExperimentOperation.UPLOAD, config.fileSizeBytes)
        val batteryStart = environment.batteryPercent()
        val file = File(environment.scratchDir(), "exp_${config.runId}_$rep.bin")
        var fileHash: String? = null
        try {
            val bytes = ByteArray(config.fileSizeBytes.toInt()).also { random.nextBytes(it) }
            file.writeBytes(bytes)
            fileHash = sha256Hex(bytes)
            val hash = fileHash

            val (result, energy) = energyMeter.measure {
                runCatching {
                    val t0 = clockMs()
                    val selection = selectOptimalPeers(config.fileSizeBytes, config.baseK).getOrThrow()
                    val t1 = clockMs()
                    val fragments = encodeErasureFragments(file, selection.params).getOrThrow()
                    val t2 = clockMs()
                    val recipient = securityRepository.getEncryptionIdentity().getOrThrow().publicKeyBytes
                    val bundle = fragmentCipher.encrypt(fragments, recipient).getOrThrow()
                    val t3 = clockMs()
                    val entry = distributeEncryptedBlocks.distribute(
                        encryptedBundle = bundle,
                        fileHash = hash,
                        params = selection.params,
                        selectedPeers = selection.selectedPeers,
                        originalFileName = file.name
                    ).getOrThrow()
                    val t4 = clockMs()
                    record.copy(
                        k = selection.params.k, n = selection.params.n,
                        wireBytes = wireBytes(bundle, entry), hostCount = entry.fragmentLocations.flatMap { it.nodeIds }.toSet().size,
                        selectMs = t1 - t0, encodeMs = t2 - t1, encryptMs = t3 - t2, transferMs = t4 - t3, totalMs = t4 - t0,
                        success = true
                    )
                }
            }
            record = result.getOrElse { e ->
                if (e is CancellationException) throw e
                record.copy(error = e.message ?: e.javaClass.simpleName)
            }.copy(energy = energy)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            record = record.copy(success = false, error = e.message ?: e.javaClass.simpleName)
        } finally {
            file.delete()
        }
        return UploadOutcome(
            record.copy(batteryStartPct = batteryStart, batteryEndPct = environment.batteryPercent()),
            fileHash
        )
    }

    private suspend fun runDownload(config: ExperimentConfig, rep: Int, fileHash: String): MeasurementRecord {
        val base = baseRecord(config, rep, ExperimentOperation.DOWNLOAD, config.fileSizeBytes)
        val batteryStart = environment.batteryPercent()
        val (record, energy) = energyMeter.measure {
            runCatching {
                val t0 = clockMs()
                val blockMap = localizeFileBlocks.invoke(fileHash).getOrThrow()
                val k = catalogRepository.getEntry(fileHash).getOrNull()?.k ?: config.baseK
                val n = catalogRepository.getEntry(fileHash).getOrNull()?.n ?: 0
                val t1 = clockMs()
                val terminal = downloadFileBlocks.invoke(blockMap, k).first {
                    it is DownloadProgressState.Completed || it is DownloadProgressState.Failed
                }
                val t2 = clockMs()
                val completed = when (terminal) {
                    is DownloadProgressState.Completed -> terminal
                    is DownloadProgressState.Failed -> error("download failed: ${terminal.reason} (${terminal.received}/${terminal.k})")
                    else -> error("unexpected state")
                }
                val assembled = assembleDownloadedFile.invoke(fileHash, completed.blocks, isPreview = true)
                    .first { it is AssembleDownloadedFileUseCase.AssembleProgress.Finalized }
                    as AssembleDownloadedFileUseCase.AssembleProgress.Finalized
                val t3 = clockMs()
                when (val r = assembled.result) {
                    is AssembleDownloadedFileUseCase.AssembleResult.Success -> runCatching { File(r.filePath).delete() }
                    is AssembleDownloadedFileUseCase.AssembleResult.Failure -> throw r.exception
                }
                base.copy(
                    k = k, n = n,
                    wireBytes = completed.blocks.values.sumOf { it.ciphertext.size.toLong() },
                    hostCount = blockMap.size,
                    selectMs = t1 - t0, transferMs = t2 - t1, assembleMs = t3 - t2, totalMs = t3 - t0,
                    success = true
                )
            }
        }
        return record.getOrElse { e ->
            if (e is CancellationException) throw e
            base.copy(error = e.message ?: e.javaClass.simpleName)
        }.copy(energy = energy, batteryStartPct = batteryStart, batteryEndPct = environment.batteryPercent())
    }

    /** Octets envoyés : chaque fragment chiffré compte autant de fois qu'il a d'hôtes. */
    private fun wireBytes(bundle: EncryptedBundle, entry: CatalogEntry): Long {
        val sizeByIndex = bundle.encryptedFragments.associate { it.index to it.ciphertext.size.toLong() }
        return entry.fragmentLocations.sumOf { loc -> (sizeByIndex[loc.fragmentIndex] ?: 0L) * loc.nodeIds.size }
    }

    private fun progressLine(r: MeasurementRecord): String {
        val mbps = MeasurementMath.megabitsPerSecond(r.fileBytes, r.transferMs)
        val status = if (r.success) "OK" else "FAIL(${r.error})"
        return "[${r.label}] ${r.operation} #${r.repetition} $status transfer=${r.transferMs}ms " +
            "goodput=${mbps?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "-"}Mbps " +
            "energy=${r.energy?.energyMilliJoules?.let { String.format(java.util.Locale.ROOT, "%.0f", it) } ?: "-"}mJ"
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
