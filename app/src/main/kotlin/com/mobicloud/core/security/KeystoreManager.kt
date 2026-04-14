package com.mobicloud.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import com.mobicloud.domain.models.NodeIdentity
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import javax.inject.Singleton

@Singleton
class KeystoreManager() {

    companion object {
        const val KEY_ALIAS = "mobicloud_node_identity_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TAG = "KeystoreManager"
    }

    /**
     * Tente de récupérer l'identité existante à partir du Keystore.
     */
    fun getExistingIdentity(): NodeIdentity? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: return null
            val keyBytes = entry.certificate.publicKey.encoded

            return NodeIdentity(
                nodeId = generateNodeId(keyBytes),
                publicKeyBytes = keyBytes,
                reliabilityScore = 1.0f
            )
        }
        return null
    }

    /**
     * Génère une nouvelle paire de clés dans le Keystore et retourne l'identité.
     */
    fun generateIdentity(): NodeIdentity {
        // Génération EC secp256r1 dans l'Android Keystore (hardware-backed TEE par défaut)
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
            // StrongBox non forcé : tous les appareils ne le supportent pas.
            // Le TEE normal (hardware-backed par défaut) est acceptable per AC5.
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        val keyPair = keyPairGenerator.generateKeyPair()
        val keyBytes = keyPair.public.encoded

        // AC5 : Vérification que la clé est bien dans le secure hardware (TEE/StrongBox).
        val keyFactory = KeyFactory.getInstance(keyPair.private.algorithm, ANDROID_KEYSTORE)
        val keyInfo = keyFactory.getKeySpec(keyPair.private, KeyInfo::class.java)
        if (!keyInfo.isInsideSecureHardware) {
            Log.w(TAG, "[AC5-WARNING] La clé privée n'est PAS dans le secure hardware (TEE). "
                + "Ceci est attendu sur émulateur, mais doit être investigué sur un appareil physique.")
        }

        return NodeIdentity(
            nodeId = generateNodeId(keyBytes),
            publicKeyBytes = keyBytes,
            reliabilityScore = 1.0f
        )
    }

    private fun generateNodeId(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        // Prendre les 8 premiers octets et formater en Hex (16 chars)
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
