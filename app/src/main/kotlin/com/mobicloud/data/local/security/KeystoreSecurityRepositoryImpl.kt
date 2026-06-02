package com.mobicloud.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.mobicloud.domain.models.EncryptionIdentity
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.SecurityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.ProviderException
import java.security.spec.PKCS8EncodedKeySpec
import javax.inject.Inject

class KeystoreSecurityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurityRepository {

    companion object {
        // Source unique : KeystoreManager.KEY_ALIAS garantit qu'IdentityRepository et SecurityRepository
        // partagent la MÊME paire de clés Keystore et produisent le MÊME nodeId.
        internal val KEY_ALIAS get() = com.mobicloud.core.security.KeystoreManager.KEY_ALIAS
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        internal const val PREFS_FILE = "mobicloud_security_prefs"
        private const val PREF_KEY_PUBLIC = "software_public_key"
        private const val PREF_KEY_PRIVATE = "software_private_key"

        // Story 6.3 — clés EC P-256 dédiées au chiffrement ECIES (paire distincte de
        // l'identité Keystore SIGN/VERIFY, voir Contrainte #1).
        private const val PREF_ENC_KEY_PUBLIC = "encryption_public_key"
        private const val PREF_ENC_KEY_PRIVATE = "encryption_private_key"
    }

    // Cache mémoire pour éviter de rejouer le PKCS#8 → PrivateKey à chaque déchiffrement.
    // Mutex garantit l'atomicité du check-then-generate-then-assign (évite double génération concurrente).
    private val encryptionIdentityMutex = Mutex()
    private var cachedEncryptionIdentity: EncryptionIdentity? = null

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
        // Guard: return existing identity (hardware or software) rather than overwriting
        val existing = getIdentity()
        if (existing.isSuccess) return@withContext existing

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

    override suspend fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Result<Boolean> = withContext(Dispatchers.Default) {
        try {
            val pubKey = java.security.KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
                .generatePublic(java.security.spec.X509EncodedKeySpec(publicKey))
            val sig = java.security.Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pubKey)
            sig.update(data)
            Result.success(sig.verify(signature))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Story 6.3 — Retourne (ou génère au premier appel) la paire EC P-256 dédiée
     * au chiffrement ECIES. Cache mémoire pour éviter le coût répété de
     * désérialisation PKCS#8 → PrivateKey.
     */
    override suspend fun getEncryptionIdentity(): Result<EncryptionIdentity> =
        withContext(Dispatchers.IO) {
            encryptionIdentityMutex.withLock {
                cachedEncryptionIdentity?.let { return@withContext Result.success(it) }
                try {
                    val prefs = getEncryptedPrefs()
                    val storedPublic = prefs.getString(PREF_ENC_KEY_PUBLIC, null)
                    val storedPrivate = prefs.getString(PREF_ENC_KEY_PRIVATE, null)

                    val identity = if (storedPublic != null && storedPrivate != null) {
                        val publicKeyBytes = Base64.decode(storedPublic, Base64.NO_WRAP)
                        val privateKeyBytes = Base64.decode(storedPrivate, Base64.NO_WRAP)
                        val privateKey = decodePrivateKey(privateKeyBytes)
                        EncryptionIdentity(publicKeyBytes, privateKey)
                    } else {
                        // Génération + persistance atomique (idempotente : si déjà présent on lit).
                        generateEncryptionIdentity(prefs)
                    }
                    cachedEncryptionIdentity = identity
                    Result.success(identity)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    private suspend fun generateEncryptionIdentity(
        prefs: SharedPreferences
    ): EncryptionIdentity = withContext(Dispatchers.Default) {
        val keyPair = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            .apply { initialize(256) }
            .generateKeyPair()
        val publicKeyBytes = keyPair.public.encoded
        val privateKeyBytes = keyPair.private.encoded

        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(PREF_ENC_KEY_PUBLIC, Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP))
                .putString(PREF_ENC_KEY_PRIVATE, Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP))
                .commit()
        }
        EncryptionIdentity(publicKeyBytes, keyPair.private)
    }

    private fun decodePrivateKey(pkcs8Bytes: ByteArray): PrivateKey =
        KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))

    override suspend fun exportRecoveryCode(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prefs = getEncryptedPrefs()
            val identityPriv = prefs.getString(PREF_KEY_PRIVATE, null)
                ?: return@withContext Result.failure(
                    IllegalStateException("Identité hardware non exportable. La récupération nécessite une clé logicielle.")
                )
            val identityPub = prefs.getString(PREF_KEY_PUBLIC, null)
                ?: return@withContext Result.failure(IllegalStateException("Clé publique introuvable."))
            val encPriv = prefs.getString(PREF_ENC_KEY_PRIVATE, null)
                ?: return@withContext Result.failure(IllegalStateException("Clé de chiffrement introuvable."))
            val encPub = prefs.getString(PREF_ENC_KEY_PUBLIC, null)
                ?: return@withContext Result.failure(IllegalStateException("Clé publique de chiffrement introuvable."))

            val combined = "$identityPriv|$identityPub|$encPriv|$encPub"
            val code = Base64.encodeToString(combined.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importFromRecoveryCode(code: String): Result<NodeIdentity> = withContext(Dispatchers.IO) {
        try {
            val combined = Base64.decode(code.trim(), Base64.URL_SAFE).toString(Charsets.UTF_8)
            val parts = combined.split("|")
            if (parts.size != 4) return@withContext Result.failure(IllegalArgumentException("Code de récupération invalide."))

            val (identityPriv, identityPub, encPriv, encPub) = parts

            val identityPubBytes = Base64.decode(identityPub, Base64.NO_WRAP)
            val nodeId = sha256Id(identityPubBytes)

            getEncryptedPrefs().edit()
                .putString(PREF_KEY_PRIVATE, identityPriv)
                .putString(PREF_KEY_PUBLIC, identityPub)
                .putString(PREF_ENC_KEY_PRIVATE, encPriv)
                .putString(PREF_ENC_KEY_PUBLIC, encPub)
                .commit()

            cachedEncryptionIdentity = null

            Result.success(NodeIdentity(nodeId = nodeId, publicKeyBytes = identityPubBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
