package com.mobicloud.domain.usecase.m00_identity

import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportIdentityUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(code: String): Result<Unit> {
        val identity = securityRepository.importFromRecoveryCode(code)
            .getOrElse { return Result.failure(it) }
        return identityRepository.restoreIdentity(identity)
    }
}
