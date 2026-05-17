package com.mobicloud.domain.usecase.m08_hosting

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.RelayRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 9.4 — répond à une `RelayEvent.BlockRequestForwarded` émise par le pair [fromNodeId]
 * pour le [blockId]. Si je l'héberge localement, je le pousse via le canal UPLOAD/FORWARD
 * existant (pas de nouveau message retour : la réponse réutilise `relayRepository.uploadBlock`).
 * Sinon, no-op (le requester time-out, fallback K+2 sur autre réplique).
 *
 * Best-effort : toute exception est attrapée et loggée, jamais propagée — sauf
 * `CancellationException` re-thrown explicitement (W-9.3-7).
 */
@Singleton
class RespondToBlockRequestUseCase @Inject constructor(
    private val hostedBlockRepository: HostedBlockRepository,
    private val relayRepository: RelayRepository
) {
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun respond(fromNodeId: String, blockId: String) = withContext(Dispatchers.IO) {
        try {
            val payload = hostedBlockRepository.getBlock(blockId).getOrNull()
            if (payload == null) {
                Log.i(TAG, "[INTER-CLUSTER][RESPOND] bloc absent ${blockId.take(16)}, ignoré")
                return@withContext
            }
            // [P5-Fix] Utiliser l'ownerId original du bloc (stocké en DB) au lieu du nodeId local.
            // L'ownerId local corrompait l'ownership metadata à chaque hop inter-cluster.
            val message = BlockTransferMessage(
                blockId = payload.blockId,
                ownerId = payload.ownerId,
                fragmentIndex = payload.fragmentIndex,
                isParity = payload.isParity,
                ciphertext = payload.ciphertext,
                iv = payload.iv,
                originalFileSize = 0L  // inconnu côté répondeur — non-bloquant pour le requester
            )
            val data = MobiCloudProtoBuf.encodeToByteArray(BlockTransferMessage.serializer(), message)
            val result = relayRepository.uploadBlock(
                destNodeId = fromNodeId,
                blockId = blockId,
                data = data
            )
            if (result.isFailure) {
                Log.w(TAG, "[INTER-CLUSTER][RESPOND] uploadBlock échoué ${blockId.take(16)} : ${result.exceptionOrNull()?.message}")
            } else {
                Log.i(TAG, "[INTER-CLUSTER][RESPOND] bloc ${blockId.take(16)} servi à ${fromNodeId.take(8)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[INTER-CLUSTER][RESPOND] exception ${blockId.take(16)} : ${e.message}")
        }
    }

    companion object {
        private const val TAG = "RespondToBlockRequest"
    }
}
