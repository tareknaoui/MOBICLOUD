package com.mobicloud.domain.usecase.m00_identity

import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportIdentityUseCase @Inject constructor(
    private val securityRepository: SecurityRepository
) {
    suspend operator fun invoke(): Result<String> = securityRepository.exportRecoveryCode()
}
