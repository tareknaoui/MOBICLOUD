package com.mobicloud.data.p2p.websocket

import android.util.Base64
import com.mobicloud.core.security.KeystoreManager
import com.mobicloud.domain.repository.IdentityRepository
import org.json.JSONObject
import java.security.KeyStore
import java.security.Signature
import javax.inject.Inject

class RelayAuthSigner @Inject constructor(
    private val identityRepository: IdentityRepository
) {
    /**
     * Construit le payload JSON UTF-8 du message AUTH (0x01).
     * Signe "MobiCloud-HA-AUTH:$nodeId:$timestamp" via Keystore EC P-256.
     * @throws IllegalStateException si le Keystore est inaccessible ou l'identité absente.
     */
    suspend fun buildAuthPayload(): ByteArray {
        val identity = identityRepository.getIdentity().getOrElse { e ->
            throw IllegalStateException("Identité inaccessible — AUTH relay impossible", e)
        }
        val nodeId = identity.nodeId
        val timestamp = System.currentTimeMillis()

        val signedData = "MobiCloud-HA-AUTH:$nodeId:$timestamp".toByteArray(Charsets.UTF_8)

        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = ks.getEntry(KeystoreManager.KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(entry.privateKey)
            update(signedData)
        }
        val signatureBytes = sig.sign()

        val pubKeyB64 = Base64.encodeToString(identity.publicKeyBytes, Base64.NO_WRAP)
        val sigB64     = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("nodeId",        nodeId)
            put("pubKeySpkiDer", pubKeyB64)
            put("timestamp",     timestamp)
            put("signature",     sigB64)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }
}
