package com.mobicloud.presentation.explorer

import com.mobicloud.domain.models.ResolvedBlockLocation

sealed class DownloadState {
    object Idle : DownloadState()
    data class Locating(val fileHash: String) : DownloadState()
    data class Located(
        val fileHash: String,
        val blockMap: Map<String, ResolvedBlockLocation>
    ) : DownloadState()
    data class Error(val fileHash: String, val message: String) : DownloadState()
}
