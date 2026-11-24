package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.core.security.FileEnvelopeCipher
import com.mobicloud.core.security.SealedFile
import com.mobicloud.domain.models.CipherScheme
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.ErasureParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline d'upload **chiffrer-puis-encoder** (remplace encoder-puis-chiffrer).
 *
 * 1. le fichier entier est chiffré une fois (AES-256-GCM, [FileEnvelopeCipher]) ;
 * 2. le chiffré est découpé par Reed-Solomon en k fragments de données + n de parité.
 *
 * Chaque fragment distribué est un morceau de chiffré ; le champ `iv` de chaque bloc porte le
 * nonce GCM du fichier (identique pour tous les fragments), ce qui garde inchangés le format
 * réseau et le stockage des hôtes. `originalFileSize` des fragments vaut la taille du chiffré
 * (nécessaire au décodeur pour retirer le bourrage) ; la taille du clair est portée par
 * [EncryptedBundle.plaintextSize] puis par l'entrée de catalogue.
 *
 * Intérêt : un nœud détenant k fragments peut régénérer un fragment perdu sans la clé
 * ([RegenerateFragmentsUseCase]) ; les fragments de données d'un code RS systématique ne
 * sont jamais des morceaux du clair.
 */
@Singleton
class EncryptThenEncodeUseCase @Inject constructor(
    private val envelopeCipher: FileEnvelopeCipher,
    private val encoder: EncodeErasureFragmentsUseCase
) {
    /** Étape 1 : chiffrement du fichier entier pour [recipientPublicKeyBytes]. */
    suspend fun seal(plaintext: ByteArray, recipientPublicKeyBytes: ByteArray): SealedFile =
        withContext(Dispatchers.Default) { envelopeCipher.seal(plaintext, recipientPublicKeyBytes) }

    /** Étape 2 : encodage Reed-Solomon du chiffré. */
    suspend fun encode(sealed: SealedFile, plaintextSize: Long, params: ErasureParameters): EncryptedBundle {
        val shards = encoder.encodeBytes(sealed.ciphertext, params)
        return EncryptedBundle(
            encryptedFragments = shards.map { shard ->
                EncryptedFragment(
                    index = shard.index,
                    isParity = shard.isParity,
                    ciphertext = shard.data,
                    iv = sealed.nonce.copyOf(),
                    originalFileSize = shard.originalFileSize
                )
            },
            wrappedFileMasterKey = sealed.wrappedFileMasterKey,
            cipherScheme = CipherScheme.ENCRYPT_THEN_ENCODE,
            plaintextSize = plaintextSize
        )
    }

    suspend operator fun invoke(
        plaintext: ByteArray,
        recipientPublicKeyBytes: ByteArray,
        params: ErasureParameters
    ): Result<EncryptedBundle> = runCatching {
        encode(seal(plaintext, recipientPublicKeyBytes), plaintext.size.toLong(), params)
    }
}
