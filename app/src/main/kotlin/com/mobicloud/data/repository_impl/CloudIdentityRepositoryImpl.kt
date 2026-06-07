package com.mobicloud.data.repository_impl

import android.util.Base64
import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.CatalogEntrySnapshot
import com.mobicloud.data.local.entity.CatalogSnapshot
import com.mobicloud.data.local.entity.FragmentLocationEntity
import com.mobicloud.data.local.entity.FragmentLocationSnapshot
import com.mobicloud.data.remote.SupabaseClient
import com.mobicloud.data.remote.SupabaseIdentityRecord
import com.mobicloud.domain.repository.CloudIdentityRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudIdentityRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val securityRepository: SecurityRepository,
    private val identityRepository: IdentityRepository,
    private val catalogDao: CatalogDao
) : CloudIdentityRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun registerAndBackup(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val recoveryCode = securityRepository.exportRecoveryCode()
                .getOrElse { return@withContext Result.failure(it) }

            val auth = supabaseClient.signUp(email, password)
                .getOrElse { return@withContext Result.failure(it) }

            storeEncrypted(auth.access_token, auth.user.id, recoveryCode, password)
        }

    override suspend fun loginAndBackup(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val recoveryCode = securityRepository.exportRecoveryCode()
                .getOrElse { return@withContext Result.failure(it) }

            val auth = supabaseClient.signIn(email, password)
                .getOrElse { return@withContext Result.failure(it) }

            storeEncrypted(auth.access_token, auth.user.id, recoveryCode, password)
        }

    override suspend fun loginAndRestore(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val auth = supabaseClient.signIn(email, password)
                .getOrElse { return@withContext Result.failure(it) }

            val record = supabaseClient.fetchIdentity(auth.access_token)
                .getOrElse { return@withContext Result.failure(it) }

            val recoveryCode = decrypt(record.encrypted_identity, record.salt, record.iv, password)
                .getOrElse { return@withContext Result.failure(it) }

            val nodeIdentity = securityRepository.importFromRecoveryCode(recoveryCode)
                .getOrElse { return@withContext Result.failure(it) }

            identityRepository.restoreIdentity(nodeIdentity)
                .onFailure { return@withContext Result.failure(it) }

            // Restore file catalog if available
            if (record.catalog_backup != null && record.catalog_iv != null) {
                importCatalog(record.catalog_backup, record.catalog_iv, record.salt, password)
                    .onFailure { return@withContext Result.failure(it) }
            }

            Result.success(Unit)
        }

    private suspend fun storeEncrypted(
        accessToken: String,
        userId: String,
        recoveryCode: String,
        password: String
    ): Result<Unit> = runCatching {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val encryptedIdentity = encryptBytes(recoveryCode.toByteArray(Charsets.UTF_8), key, iv)

        val catalogJson = exportCatalogJson()
        val catalogIv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val encryptedCatalog = encryptBytes(catalogJson.toByteArray(Charsets.UTF_8), key, catalogIv)

        val record = SupabaseIdentityRecord(
            encrypted_identity = Base64.encodeToString(encryptedIdentity, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            user_id = userId,
            catalog_backup = Base64.encodeToString(encryptedCatalog, Base64.NO_WRAP),
            catalog_iv = Base64.encodeToString(catalogIv, Base64.NO_WRAP)
        )
        supabaseClient.upsertIdentity(accessToken, record).getOrThrow()
    }

    private suspend fun exportCatalogJson(): String {
        val all = catalogDao.getAllWithFragmentsOnce().filter { !it.catalogEntry.isInTrash }
        val entries = all.map { entry ->
            CatalogEntrySnapshot(
                fileHash = entry.catalogEntry.fileHash,
                ownerPubKeyHash = entry.catalogEntry.ownerPubKeyHash,
                versionClock = entry.catalogEntry.versionClock,
                wrappedMasterKeyJson = entry.catalogEntry.wrappedMasterKeyJson,
                originalFileSize = entry.catalogEntry.originalFileSize,
                originalFileName = entry.catalogEntry.originalFileName,
                k = entry.catalogEntry.k,
                n = entry.catalogEntry.n,
                isInTrash = entry.catalogEntry.isInTrash,
                deletedAt = entry.catalogEntry.deletedAt,
                folderPath = entry.catalogEntry.folderPath,
                fragments = entry.fragmentLocations.map { frag ->
                    FragmentLocationSnapshot(
                        fragmentIndex = frag.fragmentIndex,
                        fragmentHash = frag.fragmentHash,
                        nodeIds = frag.nodeIds
                    )
                }
            )
        }
        return json.encodeToString(CatalogSnapshot(entries = entries))
    }

    private suspend fun importCatalog(
        encryptedCatalogB64: String,
        catalogIvB64: String,
        saltB64: String,
        password: String
    ): Result<Unit> = runCatching {
        val catalogJson = decrypt(encryptedCatalogB64, saltB64, catalogIvB64, password).getOrThrow()
        val snapshot = json.decodeFromString<CatalogSnapshot>(catalogJson)
        snapshot.entries.forEach { entry ->
            val entity = CatalogEntryEntity(
                fileHash = entry.fileHash,
                ownerPubKeyHash = entry.ownerPubKeyHash,
                versionClock = entry.versionClock,
                wrappedMasterKeyJson = entry.wrappedMasterKeyJson,
                originalFileSize = entry.originalFileSize,
                originalFileName = entry.originalFileName,
                k = entry.k,
                n = entry.n,
                isInTrash = entry.isInTrash,
                deletedAt = entry.deletedAt,
                folderPath = entry.folderPath
            )
            val fragments = entry.fragments.map { frag ->
                FragmentLocationEntity(
                    catalogFileHash = entry.fileHash,
                    fragmentIndex = frag.fragmentIndex,
                    fragmentHash = frag.fragmentHash,
                    nodeIds = frag.nodeIds
                )
            }
            catalogDao.insertWithFragments(entity, fragments)
        }
    }

    private fun decrypt(
        encryptedB64: String,
        saltB64: String,
        ivB64: String,
        password: String
    ): Result<String> = runCatching {
        val encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP)
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun encryptBytes(data: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
