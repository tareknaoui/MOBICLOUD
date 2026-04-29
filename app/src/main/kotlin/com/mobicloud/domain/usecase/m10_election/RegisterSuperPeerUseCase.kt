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
private const val KEEPALIVE_INTERVAL_MS = 30_000L

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

        val ip = publicIpFetcher.fetchPublicIp().getOrElse { e ->
            emit(Result.failure(e))
            return@flow
        }

        try {
            // Enregistrement initial
            signalingRepository.registerAsSuperPeer(ip, tcpPort, reliabilityScore, electedAt, identity.nodeId)
                .getOrThrow()

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
