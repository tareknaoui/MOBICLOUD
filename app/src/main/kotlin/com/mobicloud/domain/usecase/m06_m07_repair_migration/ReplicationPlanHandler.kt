package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.ReplicationPlanMessage

/**
 * Story 7.3 — Contrat pour le traitement d'un `REPLICATE_PLAN` reçu par un donneur.
 * Implémenté par [ExecuteReplicationPlanUseCase] et câblé à [TcpConnectionManager.replicationPlanHandler].
 */
interface ReplicationPlanHandler {
    suspend fun onReplicationPlanReceived(plan: ReplicationPlanMessage)
}
