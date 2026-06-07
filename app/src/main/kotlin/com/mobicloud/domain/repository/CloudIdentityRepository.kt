package com.mobicloud.domain.repository

interface CloudIdentityRepository {
    suspend fun registerAndBackup(email: String, password: String): Result<Unit>
    suspend fun loginAndRestore(email: String, password: String): Result<Unit>
    suspend fun loginAndBackup(email: String, password: String): Result<Unit>
}
