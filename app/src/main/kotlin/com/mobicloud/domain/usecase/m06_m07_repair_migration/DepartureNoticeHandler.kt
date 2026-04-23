package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.DepartureNoticeMessage

interface DepartureNoticeHandler {
    suspend fun onDepartureNoticeReceived(notice: DepartureNoticeMessage)
}
