package com.mobicloud.domain.models

import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.serialization.Serializable

/**
 * Lien signé entre l'identité d'un appareil et sa clé publique de chiffrement ECIES
 * (réponse à R2-10 : « comment la clé publique de chiffrement du destinataire est-elle
 * authentifiée et transmise ? »).
 *
 * La paire de chiffrement est logicielle (ECDH indisponible sur la clé Keystore avant
 * l'API 31, voir [EncryptionIdentity]) ; elle est donc certifiée par la clé d'identité
 * matérielle : [signatureBytes] = ECDSA-P256 (clé Android Keystore) sur
 * [encryptionKeyBindingSignedBytes].
 *
 * Le lien est auto-certifié : il se vérifie avec la seule clé publique d'identité du
 * membre, déjà présente dans [com.mobicloud.domain.models.m11_join.MemberInfo], sans
 * autorité de certification. Il circule dans le JOIN_REQUEST, est vérifié par le
 * Super-Pair à l'admission, puis diffusé à tout le cluster dans le snapshot JOIN_ACCEPT
 * et les MEMBER_UPDATE ; chaque destinataire le revérifie de bout en bout
 * ([verifiedEncryptionPublicKey]), sans faire confiance au Super-Pair.
 *
 * Limite connue : un intermédiaire ne peut pas forger ni remplacer le lien, mais il peut
 * le retirer ; un membre sans lien vérifié n'a alors simplement pas de clé utilisable.
 */
@Serializable
data class EncryptionKeyBinding(
    val nodeId: ByteArray,
    val encryptionPublicKey: ByteArray,
    val issuedAtMs: Long,
    val signatureBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptionKeyBinding) return false
        return nodeId.contentEquals(other.nodeId) &&
            encryptionPublicKey.contentEquals(other.encryptionPublicKey) &&
            issuedAtMs == other.issuedAtMs &&
            signatureBytes.contentEquals(other.signatureBytes)
    }

    override fun hashCode(): Int {
        var result = nodeId.contentHashCode()
        result = 31 * result + encryptionPublicKey.contentHashCode()
        result = 31 * result + issuedAtMs.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/** Octets signés, versionnés et préfixés par le type pour éviter toute réutilisation croisée. */
fun encryptionKeyBindingSignedBytes(
    nodeId: ByteArray,
    encryptionPublicKey: ByteArray,
    issuedAtMs: Long
): ByteArray = "v1|ENC_KEY_BINDING|${nodeId.hex()}|${encryptionPublicKey.hex()}|$issuedAtMs"
    .toByteArray(Charsets.UTF_8)

/**
 * Vérifie que [binding] appartient bien au membre [expectedNodeId] et qu'il a été signé
 * par sa clé d'identité [signingPublicKey].
 */
suspend fun verifyEncryptionKeyBinding(
    binding: EncryptionKeyBinding,
    expectedNodeId: ByteArray,
    signingPublicKey: ByteArray,
    securityRepository: SecurityRepository
): Boolean {
    if (!binding.nodeId.contentEquals(expectedNodeId)) return false
    if (binding.encryptionPublicKey.isEmpty() || signingPublicKey.isEmpty()) return false
    val data = encryptionKeyBindingSignedBytes(binding.nodeId, binding.encryptionPublicKey, binding.issuedAtMs)
    return securityRepository.verifySignature(data, binding.signatureBytes, signingPublicKey)
        .getOrDefault(false)
}

/**
 * Clé publique de chiffrement d'un membre, uniquement si son lien signé est valide.
 * Tout futur usage de la clé d'un autre membre (partage d'un fichier, ré-encapsulation
 * de K_F) doit passer par cette fonction plutôt que lire le champ brut.
 */
suspend fun com.mobicloud.domain.models.m11_join.MemberInfo.verifiedEncryptionPublicKey(
    securityRepository: SecurityRepository
): ByteArray? {
    val binding = encryptionKeyBinding ?: return null
    return if (verifyEncryptionKeyBinding(binding, nodeId, publicKey, securityRepository)) {
        binding.encryptionPublicKey
    } else null
}
