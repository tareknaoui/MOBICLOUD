package com.mobicloud.domain.usecase.m06_m07_repair_migration

import android.util.Log
import com.mobicloud.data.p2p.relay.GossipRelayChannel
import com.mobicloud.domain.models.DepartureNoticeMessage
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendDepartureNoticeUseCase @Inject constructor(
    private val hostedBlockRepository: HostedBlockRepository,
    private val securityRepository: SecurityRepository,
    private val peerRepository: PeerRepository,
    private val gossipRelayChannel: GossipRelayChannel,
    private val networkEventRepository: NetworkEventRepository
) {
    companion object {
        const val MIGRATION_WINDOW_MS = 5_000L
        private const val LOGTAG = "MobiCloud:Migrate"
    }

    suspend operator fun invoke(): Result<Unit> = runCatching {
        val identity = securityRepository.getIdentity().getOrThrow()
        val blockIds = hostedBlockRepository.getAllBlockIds().getOrThrow()

        Log.i(LOGTAG, "[DEPART] onLost déclenché — ${blockIds.size} blocs hébergés par ${identity.nodeId.take(8)}")

        val timestampMs = System.currentTimeMillis()
        val payloadToSign = "${identity.nodeId}:${blockIds.joinToString(",")}|ts=$timestampMs".toByteArray()
        val signature = securityRepository.signData(payloadToSign).getOrThrow()

        val notice = DepartureNoticeMessage(
            senderNodeId = identity.nodeId,
            hostedBlockIds = blockIds,
            signatureBytes = signature,
            timestampMs = timestampMs
        )

        val superPeer = peerRepository.peers.value
            .firstOrNull { it.isSuperPair && it.isActive }

        if (superPeer != null) {
            gossipRelayChannel.sendDepartureNotice(superPeer.identity.nodeId, notice)
                .onSuccess {
                    val msg = "[DEPART] DEPARTURE_NOTICE envoye a ${superPeer.identity.nodeId.take(8)} (${blockIds.size} blocs)"
                    networkEventRepository.pushEvent(msg)
                    Log.i(LOGTAG, msg)
                }
                .onFailure {
                    val msg = "[DEPART] Echec envoi DEPARTURE_NOTICE : ${it.message}"
                    networkEventRepository.pushEvent(msg)
                    Log.w(LOGTAG, msg)
                }
        } else {
            val msg = "[DEPART] Aucun Super-Pair connu — depart immediat"
            networkEventRepository.pushEvent(msg)
            Log.w(LOGTAG, msg)
        }

        delay(MIGRATION_WINDOW_MS)
        val msg = "[DEPART] Fenetre de migration expiree"
        networkEventRepository.pushEvent(msg)
        Log.i(LOGTAG, msg)
        Unit
    }.onFailure { if (it is CancellationException) throw it }
}
