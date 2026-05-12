package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.JOIN_REQUEST_TIMEOUT_MS
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.JoinMetrics
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val MAX_ATTEMPTS = 3

class SendJoinRequestUseCase @Inject constructor(
    private val networkClient: IJoinNetworkClient,
    private val securityRepository: SecurityRepository,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val networkEventRepository: NetworkEventRepository,
    private val joinStateMachine: JoinStateMachine
) {
    /** Tri sticky+charge et tentatives séquentielles (max 3). */
    operator fun invoke(candidates: List<SuperPeerHint>): Flow<Result<JoinMetrics>> = flow {
        val localIdentity = securityRepository.getIdentity().getOrElse { err ->
            emit(Result.failure(err))
            return@flow
        }

        // AC6 — sélection sticky cluster + équilibrage de charge
        // P9 review : tie-break déterministe sur nodeId (hex) en cas d'égalité memberCount,
        // sinon N candidats avec la même vue convergent tous vers le même SP (premier rendu par le tracker).
        val lastKnown: String = nodeSettingsRepository.getClusterIdOnce()
        val (sticky, others) = candidates
            .filter { it.currentMemberCount < MAX_CLUSTER_SIZE }
            .partition { lastKnown.isNotEmpty() && it.clusterId == lastKnown }
        val sorted = sticky + others.sortedWith(
            compareBy<SuperPeerHint> { it.currentMemberCount }.thenBy { it.nodeId.toHexShort() }
        )

        val top = sorted.take(MAX_ATTEMPTS).toMutableList()
        val triedNodeIds = mutableSetOf<List<Byte>>()
        var attemptIndex = 0

        val freeBytesNow = runCatching { nodeSettingsRepository.observeFreeSpaceBytes().first() }
            .getOrDefault(0L)

        while (top.isNotEmpty() && attemptIndex < MAX_ATTEMPTS) {
            val hint = top.removeAt(0)
            triedNodeIds += hint.nodeId.toList()
            networkEventRepository.pushEvent(
                "[JOIN-CAND] Sending JOIN_REQUEST to ${hint.nodeId.toHexShort()} (memberCount=${hint.currentMemberCount}/${MAX_CLUSTER_SIZE}, attempt=${attemptIndex + 1}/$MAX_ATTEMPTS)"
            )

            val timestampMs = System.currentTimeMillis()
            val senderNodeIdBytes = localIdentity.nodeId.hexToByteArray()
            val pubKeyBytes = localIdentity.publicKeyBytes
            val reliabilityScore = localIdentity.reliabilityScore

            val signedBytes = joinRequestSignedBytes(
                senderNodeId = senderNodeIdBytes,
                candidatePublicKey = pubKeyBytes,
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
                    // P14 review : invalider le sticky cluster si ce cluster nous rejette pour
                    // CLUSTER_FULL ou INVALID_STATE — sinon on retentera ce même cluster mort
                    // à chaque relance du nœud (affinité de session devient piège).
                    if (lastKnown.isNotEmpty() && hint.clusterId == lastKnown &&
                        (response.reason == JoinRedirectReason.CLUSTER_FULL ||
                         response.reason == JoinRedirectReason.INVALID_STATE)) {
                        runCatching { nodeSettingsRepository.clearClusterId() }
                        networkEventRepository.pushEvent(
                            "[JOIN-CAND] Sticky cluster ${lastKnown.take(8)} invalidé (${response.reason})"
                        )
                    }
                    joinStateMachine.transition(JoinEvent.JoinRedirectReceived(response))
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

        networkEventRepository.pushEvent("[JOIN-CAND] All $MAX_ATTEMPTS candidates exhausted → Isolated")
        joinStateMachine.transition(JoinEvent.AllCandidatesExhausted)
        emit(Result.failure(JoinExhaustedException("Tous les $MAX_ATTEMPTS candidats ont échoué")))
    }
}

class JoinExhaustedException(message: String) : Exception(message)
