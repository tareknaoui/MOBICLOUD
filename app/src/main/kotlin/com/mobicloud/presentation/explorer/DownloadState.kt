package com.mobicloud.presentation.explorer

import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadProgressState

sealed class DownloadState {
    object Idle : DownloadState()
    data class Locating(val fileHash: String, val isPreview: Boolean = false) : DownloadState()
    data class Located(
        val fileHash: String,
        val blockMap: Map<String, ResolvedBlockLocation>,
        val isPreview: Boolean = false
    ) : DownloadState()

    // Story 6.2 — progression du téléchargement K+2 compétitif.
    // Story 6.4 — étendu avec contributions et slowNodeIds pour l'UI.
    data class Downloading(
        val fileHash: String,
        val received: Int,
        val k: Int,
        val failed: Int,
        val contributions: List<DownloadProgressState.BlockContribution> = emptyList(),
        val slowNodeIds: Set<String> = emptySet(),
        val failedFragmentIndices: Set<Int> = emptySet(),
        val isPreview: Boolean = false
    ) : DownloadState()

    // Story 6.3 — déchiffrement / décodage Erasure / réassemblage en cours.
    data class Decrypting(
        val fileHash: String,
        val processed: Int,
        val k: Int,
        val isPreview: Boolean = false
    ) : DownloadState()

    // Story 6.3 — fichier reconstitué et matérialisé sur disque (chemin absolu).
    // Story 6.4 — étendu avec durationMs et nodeCount pour le BottomSheet.
    data class Assembled(
        val fileHash: String,
        val filePath: String,
        val durationMs: Long = 0L,
        val nodeCount: Int = 0,
        val failedCount: Int = 0,
        val isPreview: Boolean = false
    ) : DownloadState()

    data class Error(val fileHash: String, val message: String, val isPreview: Boolean = false) : DownloadState()
}
