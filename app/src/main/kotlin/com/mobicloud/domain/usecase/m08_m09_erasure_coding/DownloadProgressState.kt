package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.DownloadedBlock

/**
 * Story 6.2 — état émis par le `Flow` du [DownloadFileBlocksUseCase].
 *
 * Cycle : `Progress(...)` (n × réception/échec) → `Completed(blocks)` ou `Failed(reason)`.
 */
sealed class DownloadProgressState {
    data class Progress(val received: Int, val k: Int, val failed: Int) : DownloadProgressState()
    data class Completed(val blocks: Map<Int, DownloadedBlock>) : DownloadProgressState()
    data class Failed(val reason: String, val received: Int, val k: Int) : DownloadProgressState()
}
