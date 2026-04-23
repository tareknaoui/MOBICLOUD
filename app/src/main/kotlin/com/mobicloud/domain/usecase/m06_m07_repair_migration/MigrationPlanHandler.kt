package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.MigrationPlanMessage

interface MigrationPlanHandler {
    suspend fun onMigrationPlanReceived(plan: MigrationPlanMessage)
}
