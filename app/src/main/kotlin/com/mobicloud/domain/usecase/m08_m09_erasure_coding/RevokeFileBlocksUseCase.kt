package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import android.util.Log
import com.mobicloud.data.p2p.relay.GossipRelayChannel
import com.mobicloud.domain.models.RevokeBlockMessage
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 13.2 — révocation distante des fragments d'un fichier supprimé définitivement.
 *
 * Pour chaque fragment du fichier, si le pair hébergeur est joignable (DHT lookup réussit),
 * envoie un [RevokeBlockMessage] signé via le canal relay (SIGNAL 0x04).
 * Les pairs hors-ligne sont ignorés silencieusement (fire-and-forget).
 *
 * @return [Result<Int>] nombre de révocations envoyées avec succès.
 */
@Singleton
class RevokeFileBlocksUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val dhtRepository: DhtRepository,
    private val securityRepository: SecurityRepository,
    private val gossipRelayChannel: GossipRelayChannel
) {

    suspend operator fun invoke(fileHash: String): Result<Int> = runCatching {
        val identity = securityRepository.getIdentity().getOrThrow()
        val entry = catalogRepository.getEntry(fileHash).getOrThrow()
            ?: return@runCatching 0

        var revokedCount = 0

        for (frag in entry.fragmentLocations) {
            val blockId = frag.fragmentHash

            // Résoudre le nodeId courant depuis le DHT (peut différer du catalog si migration)
            val dhtEntry = dhtRepository.findByBlockId(blockId).getOrNull() ?: run {
                Log.w("MobiCloud:Revoke", "DHT introuvable pour bloc ${blockId.take(16)} — ignoré")
                continue
            }

            val timestampMs = System.currentTimeMillis()
            val payload = "revoke:$blockId|ts=$timestampMs".toByteArray()
            val sig = securityRepository.signData(payload).getOrNull() ?: run {
                Log.w("MobiCloud:Revoke", "Signature échouée pour bloc ${blockId.take(16)} — ignoré")
                continue
            }

            val msg = RevokeBlockMessage(
                blockId = blockId,
                ownerNodeId = identity.nodeId,
                ownerPublicKeyBytes = identity.publicKeyBytes,
                signatureBytes = sig,
                timestampMs = timestampMs
            )

            gossipRelayChannel.sendRevokeBlock(dhtEntry.nodeId, msg)
                .onSuccess {
                    revokedCount++
                    Log.i("MobiCloud:Revoke", "Révocation envoyée bloc ${blockId.take(16)} → ${dhtEntry.nodeId.take(8)}")
                }
                .onFailure {
                    Log.w("MobiCloud:Revoke", "Pair ${dhtEntry.nodeId.take(8)} hors-ligne — bloc ${blockId.take(16)} non révoqué")
                }
        }

        revokedCount
    }.onFailure { if (it is CancellationException) throw it }
}
