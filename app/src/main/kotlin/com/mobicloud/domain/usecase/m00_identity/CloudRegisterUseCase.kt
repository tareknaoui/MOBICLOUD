package com.mobicloud.domain.usecase.m00_identity

import com.mobicloud.domain.repository.CloudIdentityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRegisterUseCase @Inject constructor(
    private val cloudIdentityRepository: CloudIdentityRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<Unit> =
        cloudIdentityRepository.registerAndBackup(email, password)
}
