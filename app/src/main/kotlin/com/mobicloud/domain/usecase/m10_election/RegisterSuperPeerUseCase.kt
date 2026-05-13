package com.mobicloud.domain.usecase.m10_election

import android.util.Log
import com.mobicloud.data.network.PublicIpFetcher
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SignalingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "RegisterSuperPeerUC"
private const val KEEPALIVE_INTERVAL_MS = 10_000L

class RegisterSuperPeerUseCase @Inject constructor(
    private val signalingRepository: SignalingRepository,
    private val identityRepository: IdentityRepository,
    private val networkEventRepository: NetworkEventRepository,
    private val publicIpFetcher: PublicIpFetcher,
    private val trustScoreProvider: ITrustScoreProvider
) {
    /**
     * Enregistre ce nœud comme Super-Pair auprès des Serveurs Relais HA et maintient un keepalive toutes les 30s.
     * Annuler la coroutine déclenche l'abdication via `unregisterAsSuperPeer`.
     *
     * @param tcpPort Port TCP en écoute, fourni par le service appelant.
     * @param electedAt Timestamp Unix (ms) de l'élection.
     */
    operator fun invoke(tcpPort: Int, electedAt: Long = System.currentTimeMillis()): Flow<Result<Unit>> = flow {
        val identity = identityRepository.getIdentity().getOrElse { e ->
            emit(Result.failure(e))
            return@flow
        }
        val reliabilityScore = trustScoreProvider.getTrustScore(identity.nodeId).toFloat()

        // L'IP publique n'est PAS critique : le relay route via la session WebSocket active
        // (il connait l'origine reelle du socket). L'IP sert uniquement aux tentatives directes
        // peer-to-peer (try-direct-then-relay). Si api.ipify.org est inaccessible (filtrage
        // hotspot, DNS, rate-limit, etc.), on fallback sur "0.0.0.0" -- les autres pairs
        // tomberont en mode relay-only pour ce super-peer, ce qui est l'objectif de V5.0.
        // Bug historique : un fetch IP qui echoue bloquait tout REGISTER_PEER -> super-peer
        // jamais visible dans l'annuaire signaling -> tous les nœuds elisaient en parallele.
        val ip = publicIpFetcher.fetchPublicIp().getOrElse { e ->
            Log.w(TAG, "fetchPublicIp echoue -- fallback ip=0.0.0.0 (relay-only mode)", e)
            networkEventRepository.pushEvent(
                "[ELECTION] IP publique indisponible -- enregistrement Super-Pair en mode relay-only"
            )
            "0.0.0.0"
        }

        try {
            // Enregistrement initial avec retry — si le WS relay est en cours de reconnexion
            // au moment de la victoire Bully, on retente toutes les 5s jusqu'au succès.
            // Avant ce fix, .getOrThrow() échouait silencieusement → la boucle keepalive
            // ne démarrait jamais → TTL 60s expirait → registeredSuperPeers = 0 → élections
            // en boucle sur les autres nœuds même si ce nœud est bien Super-Pair.
            while (currentCoroutineContext().isActive) {
                val reg = signalingRepository.registerAsSuperPeer(ip, tcpPort, reliabilityScore, electedAt, identity.nodeId)
                if (reg.isSuccess) break
                Log.w(TAG, "Initial REGISTER_PEER échoué (relay WS?), retry dans 5s: ${reg.exceptionOrNull()?.message}")
                networkEventRepository.pushEvent("[ELECTION] REGISTER_PEER échoué — retry dans 5s (relay WS?)")
                delay(5_000L)
            }

            // Log dans RadarLogConsole
            networkEventRepository.pushEvent(
                "[ELECTION] Super-Pair enregistré sur Relais HA: $ip:$tcpPort"
            )
            emit(Result.success(Unit))

            // Keepalive toutes les 30s pour maintenir le TTL 60s côté serveur
            while (currentCoroutineContext().isActive) {
                delay(KEEPALIVE_INTERVAL_MS)
                signalingRepository.registerAsSuperPeer(ip, tcpPort, reliabilityScore, electedAt, identity.nodeId)
                    .onFailure { Log.w(TAG, "Keepalive Relais HA échoué — mode P2P local actif", it) }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                signalingRepository.unregisterAsSuperPeer()
                    .onFailure { Log.w(TAG, "Abdication Relais HA échouée — TTL serveur purgera l'entrée", it) }
            }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Enregistrement Super-Pair Relais HA échoué — mode P2P local", e)
            emit(Result.failure(e))
        }
    }
}
