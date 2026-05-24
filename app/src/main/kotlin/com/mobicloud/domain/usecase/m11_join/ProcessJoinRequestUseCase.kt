package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.joinAcceptSignedBytes
import com.mobicloud.domain.models.m11_join.joinRedirectSignedBytes
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import kotlin.math.abs
import javax.inject.Inject

class ProcessJoinRequestUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val signalingRepository: SignalingRepository,
    private val memberRegistry: MemberRegistry,
    private val joinStateMachine: JoinStateMachine,
    private val networkEventRepository: NetworkEventRepository,
    private val memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase,
    private val sendMemberUpdateUseCase: SendMemberUpdateUseCase
) {
    suspend operator fun invoke(request: JoinRequest): JoinResponse {
        val localIdentity = securityRepository.getIdentity().getOrThrow()
        val selfNodeIdBytes = localIdentity.nodeId.hexToByteArray()
        val now = System.currentTimeMillis()

        // Branche 0 : garde d'état — seul un Super-Pair traite les JOIN_REQUEST.
        val currentState = joinStateMachine.currentState.value
        if (currentState !is NodeJoinState.SuperPair) {
            val msg = "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: INVALID_STATE (state=${currentState::class.simpleName})"
            networkEventRepository.pushEvent(msg)
            android.util.Log.d("JOIN_DEBUG", msg)
            val alts = getAlternativeSuperPeers(selfNodeIdBytes)
            return signedRedirect(JoinRedirectReason.INVALID_STATE, alts, selfNodeIdBytes)
        }

        // Branche 1 : Vérification signature EC P-256 (Story 12.1 — protocol v2 sans GPS)
        val signedBytes = joinRequestSignedBytes(
            senderNodeId = request.senderNodeId,
            candidatePublicKey = request.candidatePublicKey,
            freeBytes = request.freeBytes,
            reliabilityScore = request.reliabilityScore,
            timestampMs = request.timestampMs
        )
        val sigValid = securityRepository.verifySignature(
            data = signedBytes,
            signature = request.signatureBytes,
            publicKey = request.candidatePublicKey
        ).getOrElse { false }

        if (!sigValid) {
            val msg = "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: INVALID_SIGNATURE"
            networkEventRepository.pushEvent(msg)
            android.util.Log.d("JOIN_DEBUG", msg)
            return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, emptyList(), selfNodeIdBytes)
        }

        // Branche 2 : Fenêtre anti-replay (±30 s)
        val skewMs = abs(now - request.timestampMs)
        if (skewMs > BULLY_TIMESTAMP_WINDOW_MS) {
            val msg = "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: REPLAY (skewMs=$skewMs)"
            networkEventRepository.pushEvent(msg)
            android.util.Log.d("JOIN_DEBUG", msg)
            return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, emptyList(), selfNodeIdBytes)
        }

        // Branche 3+4 : check-and-add atomique (P7 review) — sans ça, 2 JOIN concurrents passent
        // tous deux le check `size() < MAX` puis appellent add() → cluster à MAX+1.
        val clusterId = currentState.clusterId
        val newMember = MemberInfo(
            nodeId = request.senderNodeId,
            publicKey = request.candidatePublicKey,
            ipAddress = "",
            port = 0,
            freeBytes = request.freeBytes,
            role = MemberRole.MEMBER
        )
        val admitted = memberRegistry.addIfBelowCapacity(newMember, MAX_CLUSTER_SIZE)
        if (!admitted) {
            val alts = getAlternativeSuperPeers(selfNodeIdBytes)
            val msg = "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: CLUSTER_FULL (${memberRegistry.size()}/$MAX_CLUSTER_SIZE)"
            networkEventRepository.pushEvent(msg)
            android.util.Log.d("JOIN_DEBUG", msg)
            return signedRedirect(JoinRedirectReason.CLUSTER_FULL, alts, selfNodeIdBytes)
        }

        // Sync inMemory + broadcast immédiat aux membres existants — sans ça, les pairs
        // qui ont rejoint avant ce nouveau membre ne l'apprennent qu'au keepalive 45s.
        val joinedUpdate = MemberUpdate(
            event = MemberUpdateEvent.JOINED,
            member = newMember,
            leftNodeId = byteArrayOf(),
            timestampMs = System.currentTimeMillis(),
            signatureBytes = byteArrayOf()
        )
        runCatching { memberSnapshotCacheUseCase.applyUpdate(joinedUpdate) }
        runCatching { sendMemberUpdateUseCase.invoke(joinedUpdate) }

        val snapshot = memberRegistry.list()
        val acceptTimestampMs = System.currentTimeMillis()
        val acceptSignedBytes = joinAcceptSignedBytes(clusterId, selfNodeIdBytes, acceptTimestampMs, snapshot)
        val acceptSignature = securityRepository.signData(acceptSignedBytes).getOrElse { err ->
            // P23 review R3 : runCatching pour éviter qu'une erreur DB dans remove() laisse
            // le membre en DB avec un count gonflé et ne renvoie pas de REDIRECT au candidat.
            runCatching { memberRegistry.remove(newMember.nodeId, clusterId) }
            networkEventRepository.pushEvent(
                "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: SIGN_FAILED (${err.message})"
            )
            return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, emptyList(), selfNodeIdBytes)
        }

        val acceptMsg = "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} (free=${request.freeBytes}) → ACCEPT (cluster=${memberRegistry.size()}/$MAX_CLUSTER_SIZE)"
        networkEventRepository.pushEvent(acceptMsg)
        android.util.Log.d("JOIN_DEBUG", acceptMsg)

        return JoinResponse.JoinAccept(
            clusterId = clusterId,
            superPairNodeId = selfNodeIdBytes,
            memberSnapshot = snapshot,
            timestampMs = acceptTimestampMs,
            signatureBytes = acceptSignature
        )
    }

    private suspend fun signedRedirect(
        reason: JoinRedirectReason,
        alts: List<SuperPeerHint>,
        selfNodeIdBytes: ByteArray
    ): JoinResponse.JoinRedirect {
        val ts = System.currentTimeMillis()
        val signedBytes = joinRedirectSignedBytes(reason, ts)
        val sig = securityRepository.signData(signedBytes).getOrElse { byteArrayOf() }
        return JoinResponse.JoinRedirect(
            reason = reason,
            alternativeSuperPeers = alts,
            timestampMs = ts,
            signatureBytes = sig
        )
    }

    private suspend fun getAlternativeSuperPeers(selfNodeIdBytes: ByteArray): List<SuperPeerHint> =
        signalingRepository.fetchActiveSuperPeerHints()
            .getOrDefault(emptyList())
            .filter { !it.nodeId.contentEquals(selfNodeIdBytes) }
            // P10 review : exclure les SP pleins du payload JOIN_REDIRECT — sinon le candidat
            // reçoit 3 hints potentiellement tous saturés et retombe en Isolated inutilement.
            .filter { it.currentMemberCount < MAX_CLUSTER_SIZE }
            .sortedWith(compareBy<SuperPeerHint> { it.currentMemberCount }.thenBy { it.nodeId.toHexShort() })
            .take(3)
}
