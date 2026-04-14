package com.mobicloud.domain.usecase.m01_auth_discovery

import com.mobicloud.domain.models.HashcashToken
import com.mobicloud.domain.repository.HashcashTokenRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject

/**
 * UseCase chargé de générer la preuve de travail Hashcash (Anti-Sybil).
 * S'assure que le nœud a bien dépensé de l'énergie (CPU) pour obtenir le droit de diffuser.
 *
 * Stratégie de cache : retourne le token persisté s'il existe, sinon déclenche
 * le calcul CPU coûteux (~1s sur ARM). Conforme à l'AC "éviter le recalcul continu".
 */
class GenerateHashcashProofUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val tokenRepository: HashcashTokenRepository
) {

    /**
     * @param difficultyBits Le nombre de bits initiaux à zéro requis (doit être dans [1, 255]).
     * @return Le token Hashcash existant (cache hit) ou nouvellement généré, signé et persisté.
     */
    suspend operator fun invoke(difficultyBits: Int = 18): Result<HashcashToken> = withContext(Dispatchers.Default) {
        require(difficultyBits in 1..255) {
            "difficultyBits doit être compris entre 1 et 255, reçu : $difficultyBits"
        }

        // 0. Vérification du cache — évite le recalcul lors des reconnexions (AC FR-09.1b)
        tokenRepository.getToken().getOrNull()?.let { cachedToken ->
            return@withContext Result.success(cachedToken)
        }

        try {
            // 1. Récupération de l'identité
            val identity = securityRepository.getIdentity()
                .getOrElse { return@withContext Result.failure(it) }

            val timestamp = System.currentTimeMillis()
            val md = MessageDigest.getInstance("SHA-256")

            var nonce = 0L
            var hashBytes: ByteArray
            var hashHex = ""

            // 2. Calcul des masques pour valider les bits à "0"
            val bytesRequired = difficultyBits / 8
            val extraBits = difficultyBits % 8
            val mask = if (extraBits > 0) 0xFF shl (8 - extraBits) else 0

            val basePayload = "${identity.nodeId}:$timestamp:"

            // 3. Boucle CPU intensive (Preuve de Travail)
            while (true) {
                // Cooperative cancellation (vérifie toutes les 1000 itérations)
                if (nonce % 1000L == 0L) {
                    ensureActive()
                }

                val attemptString = "$basePayload$nonce"
                hashBytes = md.digest(attemptString.toByteArray(Charsets.UTF_8))

                if (checkDifficulty(hashBytes, bytesRequired, extraBits, mask)) {
                    hashHex = hashBytes.joinToString("") { "%02x".format(it) }
                    break
                }
                nonce++
            }

            // 4. Signature du payload — la cause d'erreur originale est propagée
            val finalPayloadString = "$basePayload$nonce"
            val signature = securityRepository.signData(finalPayloadString.toByteArray(Charsets.UTF_8))
                .getOrElse { return@withContext Result.failure(it) }

            // 5. Création et sauvegarde — l'échec de persistance est propagé
            val token = HashcashToken(
                resource = identity.nodeId,
                timestamp = timestamp,
                nonce = nonce,
                hash = hashHex,
                signature = signature
            )

            tokenRepository.saveToken(token)
                .getOrElse { return@withContext Result.failure(it) }

            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fonction rapide et allégée pour valider le nombre de bits à zéro.
     * Précondition : [difficultyBits] est dans [1, 255] (validé à l'entrée de [invoke]).
     */
    private fun checkDifficulty(hash: ByteArray, bytesRequired: Int, extraBits: Int, mask: Int): Boolean {
        // Validation des bytes complets (8 bits = 0)
        for (i in 0 until bytesRequired) {
            if (hash[i] != 0.toByte()) return false
        }

        // Validation des bits restants
        if (extraBits > 0) {
            val byteValue = hash[bytesRequired].toInt()
            if ((byteValue and mask) != 0) return false
        }

        return true
    }
}

