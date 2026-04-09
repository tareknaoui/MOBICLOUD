package com.mobicloud.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.SecurityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.ProviderException
import javax.inject.Inject

class KeystoreSecurityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurityRepository {

    companion object {
        internal const val KEY_ALIAS = "mobicloud_node_identity"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        internal const val PREFS_FILE = "mobicloud_security_prefs"
        private const val PREF_KEY_PUBLIC = "software_public_key"
        private const val PREF_KEY_PRIVATE = "software_private_key"
    }

    override suspend fun getIdentity(): Result<NodeIdentity> = withContext(Dispatchers.IO) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                    ?: return@withContext Result.failure(Exception("Invalid key entry in AndroidKeyStore"))
                val keyBytes = entry.certificate.publicKey.encoded
                return@withContext Result.success(NodeIdentity(sha256Id(keyBytes), keyBytes))
            }

            // Fallback: restore persisted software key from EncryptedSharedPreferences
            val prefs = getEncryptedPrefs()
            val softwarePublicKeyBase64 = prefs.getString(PREF_KEY_PUBLIC, null)
            if (softwarePublicKeyBase64 != null) {
                val keyBytes = Base64.decode(softwarePublicKeyBase64, Base64.NO_WRAP)
                return@withContext Result.success(NodeIdentity(sha256Id(keyBytes), keyBytes))
            }

            Result.failure(Exception("Identity not found. Call generateIdentity() first."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateIdentity(): Result<NodeIdentity> = withContext(Dispatchers.IO) {
        // Guard: return existing identity rather than silently overwriting the keystore entry
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return@withContext getIdentity()
            }
        } catch (_: Exception) {
            // Keystore unavailable — proceed to generation (will fallback if needed)
        }

        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
             .build()

            keyPairGenerator.initialize(parameterSpec)
            val keyBytes = keyPairGenerator.generateKeyPair().public.encoded
            Result.success(NodeIdentity(sha256Id(keyBytes), keyBytes))

        } catch (e: Exception) {
            when (e) {
                is KeyStoreException, is ProviderException, is StrongBoxUnavailableException ->
                    generateSoftwareFallback()
                else -> Result.failure(e)
            }
        }
    }

    /**
     * Visible for testing — generates an EC key pair in software and persists it
     * securely via EncryptedSharedPreferences, ensuring identity stability across restarts.
     *
     * Key generation runs on [Dispatchers.Default] (CPU-intensive).
     * Persistence runs on [Dispatchers.IO] (disk I/O).
     */
    internal suspend fun generateSoftwareFallback(): Result<NodeIdentity> {
        return try {
            // CPU-intensive key generation on Default dispatcher
            val keyPair = withContext(Dispatchers.Default) {
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
                    .apply { initialize(256) }
                    .generateKeyPair()
            }

            val publicKeyBytes = keyPair.public.encoded
            val privateKeyBytes = keyPair.private.encoded

            // Persist both keys securely on IO dispatcher
            withContext(Dispatchers.IO) {
                getEncryptedPrefs().edit()
                    .putString(PREF_KEY_PUBLIC, Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP))
                    .putString(PREF_KEY_PRIVATE, Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP))
                    .commit()
            }

            Result.success(NodeIdentity(sha256Id(publicKeyBytes), publicKeyBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getEncryptedPrefs(): SharedPreferences {
        // MasterKeys.getOrCreate is blocking — must be called from IO dispatcher
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Derives a stable 16-char hex ID from the SHA-256 hash of the public key bytes. */
    private fun sha256Id(keyBytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(keyBytes)
            .joinToString("") { "%02x".format(it) }
            .take(16)

    override suspend fun signData(data: ByteArray): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                    ?: return@withContext Result.failure(Exception("Clé privée AndroidKeyStore invalide ou absente."))

                // La signature ECDSA est CPU-intensive — exécutée sur Dispatchers.Default
                return@withContext withContext(Dispatchers.Default) {
                    val signature = java.security.Signature.getInstance("SHA256withECDSA")
                    signature.initSign(entry.privateKey)
                    signature.update(data)
                    Result.success(signature.sign())
                }
            }

            // Fallback: Read private key from prefs (IO)
            val prefs = getEncryptedPrefs()
            val softwarePrivateKeyBase64 = prefs.getString(PREF_KEY_PRIVATE, null)
                ?: return@withContext Result.failure(Exception("Identité non trouvée. Veuillez générer l'identité d'abord."))

            val keyBytes = Base64.decode(softwarePrivateKeyBase64, Base64.NO_WRAP)

            // La reconstruction de clé et la signature ECDSA sont CPU-intensives — sur Dispatchers.Default
            withContext(Dispatchers.Default) {
                val privateKey = java.security.KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
                    .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(keyBytes))

                val signature = java.security.Signature.getInstance("SHA256withECDSA")
                signature.initSign(privateKey)
                signature.update(data)
                Result.success(signature.sign())
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
