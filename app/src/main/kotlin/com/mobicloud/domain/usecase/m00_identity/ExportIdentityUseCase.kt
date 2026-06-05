package com.mobicloud.domain.usecase.m00_identity

import android.util.Base64
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportIdentityUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val catalogRepository: CatalogRepository
) {
    suspend operator fun invoke(): Result<String> {
        val keyCode = securityRepository.exportRecoveryCode()
            .getOrElse { return Result.failure(it) }

        // Decode 4-part key payload, append catalog as 5th part.
        val decoded = Base64.decode(keyCode.trim(), Base64.URL_SAFE).toString(Charsets.UTF_8)
        val entries: List<CatalogEntry> = runCatching {
            catalogRepository.getActiveEntriesFlow().first()
        }.getOrDefault(emptyList())

        val catalogJson = Json.encodeToString(entries)
        val combined = "$decoded|$catalogJson"
        val fullCode = Base64.encodeToString(
            combined.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return Result.success(fullCode)
    }
}
