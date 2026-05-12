package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.GpsCoordinate
import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.domain.models.m11_join.MAX_RADIUS_METERS
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.joinAcceptSignedBytes
import com.mobicloud.domain.models.m11_join.joinRedirectSignedBytes
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.repository.LocationRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import com.mobicloud.domain.util.Haversine
import kotlin.math.abs
import javax.inject.Inject

class ProcessJoinRequestUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val locationRepository: LocationRepository,
    private val signalingRepository: SignalingRepository,
    private val memberRegistry: MemberRegistry,
    private val joinStateMachine: JoinStateMachine,
    private val networkEventRepository: NetworkEventRepository
) {
    suspend operator fun invoke(request: JoinRequest): JoinResponse {
        val localIdentity = securityRepository.getIdentity().getOrThrow()
        val selfNodeIdBytes = localIdentity.nodeId.hexToByteArray()
        val selfGps = locationRepository.currentLocation.value
        val now = System.currentTimeMillis()

        // Branche 0 : garde d'état — seul un Super-Pair traite les JOIN_REQUEST.
        // Sans ce guard, un Member/Joining mute le registre et signe un JoinAccept
        // avec clusterId="" → corruption protocole (décision review Story 11.2).
        val currentState = joinStateMachine.currentState.value
        if (currentState !is NodeJoinState.SuperPair) {
            networkEventRepository.pushEvent(
                "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: " +
                    "INVALID_STATE (state=${currentState::class.simpleName})"
            )
            val alts = getAlternativeSuperPeers(null, selfNodeIdBytes)
            return signedRedirect(JoinRedirectReason.INVALID_STATE, null, alts, selfNodeIdBytes)
        }

        // Validation GPS : rejets précoces si lat/lng hors plage ou non-finis
        // (Haversine renverrait NaN / résultat dégénéré sinon).
        if (!isValidGps(request.gpsLatitude, request.gpsLongitude)) {
            networkEventRepository.pushEvent(
                "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: INVALID_GPS"
            )
            return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, null, emptyList(), selfNodeIdBytes)
        }

        // Branche 1 : Vérification signature EC P-256
        val signedBytes = joinRequestSignedBytes(
            senderNodeId = request.senderNodeId,
            candidatePublicKey = request.candidatePublicKey,
            gpsLatitude = request.gpsLatitude,
            gpsLongitude = request.gpsLongitude,
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
            networkEventRepository.pushEvent("[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: INVALID_SIGNATURE")
            return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, null, emptyList(), selfNodeIdBytes)
        }

        // Branche 2 : Fenêtre anti-replay (±30 s)
        val skewMs = abs(now - request.timestampMs)
        if (skewMs > BULLY_TIMESTAMP_WINDOW_MS) {
            networkEventRepository.pushEvent(
                "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: REPLAY (skewMs=$skewMs)"
            )
            return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, null, emptyList(), selfNodeIdBytes)
        }

        // Branche 3 : Filtre GPS optionnel (NFR-10 — si l'un des deux GPS est null → skip)
        val reqGps = if (request.gpsLatitude != null && request.gpsLongitude != null)
            GpsCoordinate(request.gpsLatitude, request.gpsLongitude, 0f, now)
        else null

        if (selfGps != null && reqGps != null) {
            val distMeters = Haversine.distanceMeters(selfGps, reqGps)
            if (distMeters > MAX_RADIUS_METERS) {
                val alts = getAlternativeSuperPeers(reqGps, selfNodeIdBytes)
                networkEventRepository.pushEvent("[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: OUT_OF_RADIUS (dist=${distMeters.toLong()}m, cluster=${memberRegistry.size}/$MAX_CLUSTER_SIZE)")
                return signedRedirect(JoinRedirectReason.OUT_OF_RADIUS, distMeters, alts, selfNodeIdBytes)
            }
        }

        // Branche 4 : Filtre capacité
        if (memberRegistry.size >= MAX_CLUSTER_SIZE) {
            val alts = getAlternativeSuperPeers(reqGps, selfNodeIdBytes)
            networkEventRepository.pushEvent("[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: CLUSTER_FULL (cluster=${memberRegistry.size}/$MAX_CLUSTER_SIZE)")
            return signedRedirect(JoinRedirectReason.CLUSTER_FULL, null, alts, selfNodeIdBytes)
        }

        // Branche 5 : Accept — insérer dans le registre
        // currentState est garanti SuperPair (vérifié branche 0).
        val clusterId = currentState.clusterId

        val newMember = MemberInfo(
            nodeId = request.senderNodeId,
            publicKey = request.candidatePublicKey,
            ipAddress = "",
            port = 0,
            gpsLatitude = request.gpsLatitude,
            gpsLongitude = request.gpsLongitude,
            freeBytes = request.freeBytes,
            role = MemberRole.MEMBER
        )
        memberRegistry.add(newMember)

        val snapshot = memberRegistry.list()
        val acceptTimestampMs = System.currentTimeMillis()
        val acceptSignedBytes = joinAcceptSignedBytes(clusterId, selfNodeIdBytes, acceptTimestampMs, snapshot)
        // signData failure : on rollback l'ajout au registre et on rejette plutôt que d'émettre
        // une signature vide silencieuse qui serait rejetée côté candidat sans contexte.
        val acceptSignature = securityRepository.signData(acceptSignedBytes).getOrElse { err ->
            memberRegistry.remove(newMember.nodeId)
            networkEventRepository.pushEvent(
                "[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} REJECTED: SIGN_FAILED (${err.message})"
            )
            return signedRedirect(JoinRedirectReason.INVALID_SIGNATURE, null, emptyList(), selfNodeIdBytes)
        }

        networkEventRepository.pushEvent("[JOIN-SP] JOIN_REQUEST from ${request.senderNodeId.toHexShort()} (dist=${computeDistStr(selfGps, reqGps)}, free=${request.freeBytes}) → ACCEPT")

        return JoinResponse.JoinAccept(
            clusterId = clusterId,
            superPairNodeId = selfNodeIdBytes,
            memberSnapshot = snapshot,
            timestampMs = acceptTimestampMs,
            signatureBytes = acceptSignature
        )
    }

    /** Valide une coordonnée GPS optionnelle : null OK ; sinon doit être finie et dans les bornes. */
    private fun isValidGps(lat: Double?, lng: Double?): Boolean {
        if (lat == null && lng == null) return true
        if (lat == null || lng == null) return false
        if (!lat.isFinite() || !lng.isFinite()) return false
        if (lat !in -90.0..90.0) return false
        if (lng !in -180.0..180.0) return false
        return true
    }

    private suspend fun signedRedirect(
        reason: JoinRedirectReason,
        distanceMeters: Double?,
        alts: List<SuperPeerHint>,
        selfNodeIdBytes: ByteArray
    ): JoinResponse.JoinRedirect {
        val ts = System.currentTimeMillis()
        val signedBytes = joinRedirectSignedBytes(reason, ts)
        val sig = securityRepository.signData(signedBytes).getOrElse { byteArrayOf() }
        return JoinResponse.JoinRedirect(
            reason = reason,
            distanceMeters = distanceMeters,
            alternativeSuperPeers = alts,
            timestampMs = ts,
            signatureBytes = sig
        )
    }

    private suspend fun getAlternativeSuperPeers(
        reqGps: GpsCoordinate?,
        selfNodeIdBytes: ByteArray
    ): List<SuperPeerHint> {
        return signalingRepository.fetchActiveSuperPeerHints()
            .getOrDefault(emptyList())
            .filter { !it.nodeId.contentEquals(selfNodeIdBytes) }
            .sortedBy { hint ->
                if (reqGps != null && hint.gpsLatitude != null && hint.gpsLongitude != null) {
                    val hGps = GpsCoordinate(hint.gpsLatitude, hint.gpsLongitude, 0f, 0L)
                    Haversine.distanceMeters(reqGps, hGps)
                } else Double.MAX_VALUE
            }
            .take(3)
    }

    private fun computeDistStr(selfGps: GpsCoordinate?, reqGps: GpsCoordinate?): String {
        if (selfGps == null || reqGps == null) return "?"
        return "${Haversine.distanceMeters(selfGps, reqGps).toLong()}m"
    }

}
