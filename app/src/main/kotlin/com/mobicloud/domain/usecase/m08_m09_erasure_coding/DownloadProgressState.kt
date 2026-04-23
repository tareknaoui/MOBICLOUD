package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.DownloadedBlock

/**
 * Story 6.2 — état émis par le `Flow` du [DownloadFileBlocksUseCase].
 * Story 6.4 — étendu avec [BlockContribution], contributions et slowNodeIds pour l'UI.
 *
 * Cycle : `Progress(...)` (n × réception/échec) → `Completed(blocks)` ou `Failed(reason)`.
 */
sealed class DownloadProgressState {

    data class BlockContribution(
        val nodeId: String,
        val fragmentIndex: Int,
        val latencyMs: Long,
        val isFallback: Boolean = false
    )

    data class Progress(
        val received: Int,
        val k: Int,
        val failed: Int,
        val contributions: List<BlockContribution> = emptyList(),
        val slowNodeIds: Set<String> = emptySet(),
        val failedFragmentIndices: Set<Int> = emptySet()
    ) : DownloadProgressState()

    data class Completed(val blocks: Map<Int, DownloadedBlock>) : DownloadProgressState()
    data class Failed(val reason: String, val received: Int, val k: Int) : DownloadProgressState()
}
