package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeDiagnostics
import kotlinx.coroutines.flow.StateFlow

interface DiagnosticsRepository {
    val diagnostics: StateFlow<NodeDiagnostics>
}
