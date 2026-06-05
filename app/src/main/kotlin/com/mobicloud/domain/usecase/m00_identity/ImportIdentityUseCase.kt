package com.mobicloud.domain.usecase.m00_identity

import android.util.Base64
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val lenientJson = Json { ignoreUnknownKeys = true }

@Singleton
class ImportIdentityUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val identityRepository: IdentityRepository,
    private val catalogRepository: CatalogRepository
) {
    suspend operator fun invoke(code: String): Result<Unit> {
        // Decode full payload — may have 4 parts (keys only) or 5 parts (keys + catalog).
        val decoded = runCatching {
            Base64.decode(code.trim(), Base64.URL_SAFE).toString(Charsets.UTF_8)
        }.getOrElse { return Result.failure(IllegalArgumentException("Code de récupération invalide.")) }

        // Split with limit=5 so catalog JSON (part 5) is never broken on a hypothetical pipe.
        val parts = decoded.split("|", limit = 5)
        if (parts.size < 4) return Result.failure(IllegalArgumentException("Code de récupération invalide."))

        // Reconstruct identity-only code (parts 0–3) for SecurityRepository.
        val identityPayload = parts.take(4).joinToString("|")
        val identityCode = Base64.encodeToString(
            identityPayload.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )

        val identity = securityRepository.importFromRecoveryCode(identityCode)
            .getOrElse { return Result.failure(it) }
        identityRepository.restoreIdentity(identity)
            .getOrElse { return Result.failure(it) }

        // Restore catalog if present (part 5).
        if (parts.size == 5) {
            runCatching {
                val entries = lenientJson.decodeFromString<List<CatalogEntry>>(parts[4])
                entries.forEach { catalogRepository.insertOwnerEntry(it) }
            }
            // Catalog restore failure is non-fatal: identity is already restored.
        }

        return Result.success(Unit)
    }
}
