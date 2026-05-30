package com.mobicloud.domain.usecase.m08_hosting

import android.os.Environment
import android.os.StatFs
import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiveAndHostBlockUseCase internal constructor(
    private val hostedBlockRepository: HostedBlockRepository,
    private val securityRepository: SecurityRepository,
    private val diskSpaceProvider: () -> Long
) {
    @Inject
    constructor(
        hostedBlockRepository: HostedBlockRepository,
        securityRepository: SecurityRepository
    ) : this(
        hostedBlockRepository,
        securityRepository,
        { StatFs(Environment.getDataDirectory().path).availableBytes }
    )


    suspend fun receive(message: BlockTransferMessage): ReceiveBlockResult =
        withContext(Dispatchers.IO) {
            android.util.Log.i("MobiCloud:Host", "[RX] block ${message.blockId.take(8)} fragmentIdx=${message.fragmentIndex} parity=${message.isParity} size=${message.ciphertext.size}")
            // [Review][Patch] Guard taille AVANT tout travail (AC#6)
            if (message.ciphertext.size > MAX_BLOCK_PAYLOAD_BYTES) {
                android.util.Log.w("MobiCloud:Host", "[RX] REJECT TooBig ${message.blockId.take(8)}")
                return@withContext ReceiveBlockResult.TooBig
            }
            // [Review][Patch] Rejet ciphertext vide : sha256 de vide est une constante connue → spam trivial
            if (message.ciphertext.isEmpty()) {
                return@withContext ReceiveBlockResult.HashMismatch
            }
            // [Review][Patch] Validation format blockId — défense en profondeur contre path traversal
            if (!BLOCK_ID_REGEX.matches(message.blockId)) {
                return@withContext ReceiveBlockResult.HashMismatch
            }
            // Story 6.3 — IV (12 bytes AES-GCM nonce) requis pour permettre au client
            // de déchiffrer le bloc plus tard. Toute autre taille = pair non conforme → rejet.
            if (message.iv.size != 12) {
                return@withContext ReceiveBlockResult.HashMismatch
            }
            // [P6-Fix] Vérifier le hash AVANT l'espace disque pour éviter la fuite d'information :
            // STORAGE_FULL vs HASH_MISMATCH permettrait sinon d'inférer la disponibilité disque.
            val computedHash = sha256hex(message.ciphertext)
            if (computedHash != message.blockId) {
                return@withContext ReceiveBlockResult.HashMismatch
            }

            if (diskSpaceProvider() < MIN_FREE_BYTES) {
                return@withContext ReceiveBlockResult.StorageFull
            }

            val identityResult = securityRepository.getIdentity()
            if (identityResult.isFailure) {
                return@withContext ReceiveBlockResult.IoError(
                    identityResult.exceptionOrNull() ?: RuntimeException("identity unavailable")
                )
            }
            val receiverNodeId = identityResult.getOrThrow().nodeId

            // [Review][Patch] Idempotence : si le bloc est déjà stocké, renvoyer un ACK sans ré-écrire.
            // Évite les blocs orphelins quand la cancellation survient entre saveBlock et signData.
            if (!hostedBlockRepository.blockExists(message.blockId)) {
                val saveResult = hostedBlockRepository.saveBlock(
                    blockId = message.blockId,
                    ownerId = message.ownerId,
                    fragmentIndex = message.fragmentIndex,
                    isParity = message.isParity,
                    ciphertext = message.ciphertext,
                    iv = message.iv
                )
                if (saveResult.isFailure) {
                    android.util.Log.w("MobiCloud:Host", "[RX] saveBlock FAILED ${message.blockId.take(8)}: ${saveResult.exceptionOrNull()?.message}")
                    return@withContext ReceiveBlockResult.IoError(
                        saveResult.exceptionOrNull() ?: RuntimeException("saveBlock failed")
                    )
                }
                android.util.Log.i("MobiCloud:Host", "[RX] STORED ${message.blockId.take(8)} on disk")
            } else {
                android.util.Log.i("MobiCloud:Host", "[RX] already exists ${message.blockId.take(8)} — idempotent")
            }

            // [Review][Patch] Domain separation : signer un payload structuré pour éviter l'oracle
            // de signature (hash arbitraire choisi par le pair émetteur).
            val signingPayload = "$ACK_DOMAIN_PREFIX|$receiverNodeId|$computedHash"
                .toByteArray(Charsets.UTF_8)
            val signResult = securityRepository.signData(signingPayload)
            if (signResult.isFailure) {
                return@withContext ReceiveBlockResult.IoError(
                    signResult.exceptionOrNull() ?: RuntimeException("signData failed")
                )
            }
            val signature = signResult.getOrThrow()

            // Karma/WeightScore retire du scope V4 (2026-04-28) — perspective rapport uniquement.

            ReceiveBlockResult.Success(
                ack = BlockAckMessage(
                    blockId = message.blockId,
                    blockHash = computedHash,
                    receiverNodeId = receiverNodeId,
                    signature = signature
                )
            )
        }

    companion object {
        const val MAX_BLOCK_PAYLOAD_BYTES = 20_000_000
        const val MIN_FREE_BYTES = 100L * 1024 * 1024 // 100 MB

        // [Review][Patch] Préfixe de domaine pour la signature ACK (domain separation).
        // Payload signé : "$ACK_DOMAIN_PREFIX|$receiverNodeId|$blockHash".toByteArray(UTF-8).
        // Défini ici (domain) pour éviter la dépendance data → domain ; référencé par BlockTransferClient.
        const val ACK_DOMAIN_PREFIX = "MOBICLOUD_BLOCK_ACK_v1"

        // [Review][Patch] Format strict attendu : 64 chars hex lowercase = SHA-256 hex digest
        private val BLOCK_ID_REGEX = Regex("^[0-9a-f]{64}$")

        internal fun sha256hex(data: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(data)
                .joinToString("") { "%02x".format(it) }
    }
}

sealed class ReceiveBlockResult {
    data class Success(val ack: BlockAckMessage) : ReceiveBlockResult()
    object StorageFull : ReceiveBlockResult()
    object HashMismatch : ReceiveBlockResult()
    object TooBig : ReceiveBlockResult()
    data class IoError(val cause: Throwable) : ReceiveBlockResult()
}
