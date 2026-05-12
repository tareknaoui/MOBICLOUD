package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.GpsCoordinate
import com.mobicloud.domain.models.m11_join.JOIN_REQUEST_TIMEOUT_MS
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinMetrics
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.LocationRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.util.Haversine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val MAX_ATTEMPTS = 3

class SendJoinRequestUseCase @Inject constructor(
    private val networkClient: IJoinNetworkClient,
    private val securityRepository: SecurityRepository,
    private val locationRepository: LocationRepository,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val networkEventRepository: NetworkEventRepository,
    private val joinStateMachine: JoinStateMachine
) {
    /** Tri des candidats et tentatives séquentielles (max 3). */
    operator fun invoke(candidates: List<SuperPeerHint>): Flow<Result<JoinMetrics>> = flow {
        val selfGps = locationRepository.currentLocation.value
        val localIdentity = securityRepository.getIdentity().getOrElse { err ->
            emit(Result.failure(err))
            return@flow
        }

        val sorted = sortCandidates(candidates, selfGps)
        val top = sorted.take(MAX_ATTEMPTS).toMutableList()
        val triedNodeIds = mutableSetOf<List<Byte>>()
        var attemptIndex = 0

        // Lecture freeBytes une seule fois en début de boucle — valeur snapshot
        // suffisante pour la décision de placement côté SP (NFR-08 latence).
        val freeBytesNow = runCatching { nodeSettingsRepository.observeFreeSpaceBytes().first() }
            .getOrDefault(0L)

        while (top.isNotEmpty() && attemptIndex < MAX_ATTEMPTS) {
            val hint = top.removeAt(0)
            triedNodeIds += hint.nodeId.toList()
            val dist = computeDist(selfGps, hint)
            networkEventRepository.pushEvent(
                "[JOIN-CAND] Sending JOIN_REQUEST to ${hint.nodeId.toHexShort()} (dist=${dist?.toLong() ?: "?"}m, attempt=${attemptIndex + 1}/$MAX_ATTEMPTS)"
            )

            val gps = selfGps
            val timestampMs = System.currentTimeMillis()

            val senderNodeIdBytes = localIdentity.nodeId.hexToByteArray()
            val pubKeyBytes = localIdentity.publicKeyBytes
            val reliabilityScore = localIdentity.reliabilityScore

            // Construire les bytes à signer
            val signedBytes = joinRequestSignedBytes(
                senderNodeId = senderNodeIdBytes,
                candidatePublicKey = pubKeyBytes,
                gpsLatitude = gps?.latitude,
                gpsLongitude = gps?.longitude,
                freeBytes = freeBytesNow,
                reliabilityScore = reliabilityScore,
                timestampMs = timestampMs
            )

            val signature = securityRepository.signData(signedBytes).getOrElse {
                attemptIndex++
                continue
            }

            val request = JoinRequest(
                senderNodeId = senderNodeIdBytes,
                candidatePublicKey = pubKeyBytes,
                gpsLatitude = gps?.latitude,
                gpsLongitude = gps?.longitude,
                freeBytes = freeBytesNow,
                reliabilityScore = reliabilityScore,
                timestampMs = timestampMs,
                signatureBytes = signature
            )

            val startMs = System.currentTimeMillis()
            val response = withTimeoutOrNull(JOIN_REQUEST_TIMEOUT_MS) {
                networkClient.sendJoinRequest(hint, request).getOrNull()
            }

            when {
                response == null -> {
                    attemptIndex++
                }

                response is JoinResponse.JoinAccept -> {
                    val latencyMs = System.currentTimeMillis() - startMs
                    networkEventRepository.pushEvent(
                        "[JOIN-CAND] JOIN_ACCEPT received from ${hint.nodeId.toHexShort()} clusterId=${response.clusterId} latencyMs=$latencyMs"
                    )
                    nodeSettingsRepository.updateClusterId(response.clusterId)
                    joinStateMachine.transition(JoinEvent.JoinAcceptReceived(response))
                    emit(Result.success(JoinMetrics(latencyMs)))
                    return@flow
                }

                response is JoinResponse.JoinRedirect -> {
                    networkEventRepository.pushEvent(
                        "[JOIN-CAND] JOIN_REDIRECT(${response.reason}) from ${hint.nodeId.toHexShort()} — trying next candidate"
                    )
                    joinStateMachine.transition(JoinEvent.JoinRedirectReceived(response))
                    // Dédup contre hints déjà tentés ET ceux qui restent dans la file.
                    val existingIds = top.map { it.nodeId.toList() }.toSet() + triedNodeIds
                    val remaining = MAX_ATTEMPTS - (attemptIndex + 1)
                    response.alternativeSuperPeers
                        .filter { alt -> alt.nodeId.toList() !in existingIds }
                        .take(remaining.coerceAtLeast(0))
                        .forEach { top.add(it) }
                    attemptIndex++
                }
            }
        }

        // Tous les candidats épuisés
        networkEventRepository.pushEvent("[JOIN-CAND] All $MAX_ATTEMPTS candidates exhausted → Isolated")
        joinStateMachine.transition(JoinEvent.AllCandidatesExhausted)
        emit(Result.failure(JoinExhaustedException("Tous les $MAX_ATTEMPTS candidats ont échoué")))
    }

    private fun sortCandidates(candidates: List<SuperPeerHint>, selfGps: GpsCoordinate?): List<SuperPeerHint> {
        if (selfGps == null) return candidates.sortedByDescending { it.reliabilityScore }
        // Tri primaire : distance Haversine croissante (GPS connu d'abord) ; secondaire :
        // reliabilityScore décroissant pour départager les hints sans GPS.
        // (l'astuce Double.MAX_VALUE - score perdait la précision flottante)
        return candidates.sortedWith(
            compareBy<SuperPeerHint> { hint ->
                if (hint.gpsLatitude != null && hint.gpsLongitude != null) {
                    Haversine.distanceMeters(
                        selfGps,
                        GpsCoordinate(hint.gpsLatitude, hint.gpsLongitude, 0f, 0L)
                    )
                } else Double.POSITIVE_INFINITY
            }.thenByDescending { it.reliabilityScore }
        )
    }

    private fun computeDist(selfGps: GpsCoordinate?, hint: SuperPeerHint): Double? {
        if (selfGps == null || hint.gpsLatitude == null || hint.gpsLongitude == null) return null
        val hintGps = GpsCoordinate(hint.gpsLatitude, hint.gpsLongitude, 0f, 0L)
        return Haversine.distanceMeters(selfGps, hintGps)
    }
}

class JoinExhaustedException(message: String) : Exception(message)
