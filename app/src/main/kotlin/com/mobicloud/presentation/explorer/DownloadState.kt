package com.mobicloud.presentation.explorer

import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.ResolvedBlockLocation

sealed class DownloadState {
    object Idle : DownloadState()
    data class Locating(val fileHash: String) : DownloadState()
    data class Located(
        val fileHash: String,
        val blockMap: Map<String, ResolvedBlockLocation>
    ) : DownloadState()
    // Story 6.2 — progression du téléchargement K+2 compétitif.
    data class Downloading(
        val fileHash: String,
        val received: Int,
        val k: Int,
        val failed: Int
    ) : DownloadState()
    // Story 6.2 — set complet des blocs ciphertext, prêt pour le pipeline 6.3.
    data class Downloaded(
        val fileHash: String,
        val blocks: Map<Int, DownloadedBlock>
    ) : DownloadState()
    data class Error(val fileHash: String, val message: String) : DownloadState()
}
