package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.DepartureNoticeMessage
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.data.p2p.tcp.TcpConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendDepartureNoticeUseCase @Inject constructor(
    private val hostedBlockRepository: HostedBlockRepository,
    private val securityRepository: SecurityRepository,
    private val peerRepository: PeerRepository,
    private val tcpConnectionManager: TcpConnectionManager,
    private val networkEventRepository: NetworkEventRepository
) {
    companion object {
        const val MIGRATION_WINDOW_MS = 5_000L
    }

    // Fix P6 : .onFailure { rethrow CancellationException } — runCatching seul
    // swallowe CancellationException et casse la coopération coroutine.
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val identity = securityRepository.getIdentity().getOrThrow()
        val blockIds = hostedBlockRepository.getAllBlockIds().getOrThrow()

        val payloadToSign = "${identity.nodeId}:${blockIds.joinToString(",")}".toByteArray()
        val signature = securityRepository.signData(payloadToSign).getOrThrow()

        val notice = DepartureNoticeMessage(
            senderNodeId = identity.nodeId,
            hostedBlockIds = blockIds,
            signatureBytes = signature
        )

        val superPeer = peerRepository.peers.value
            .firstOrNull { it.isSuperPair && it.isActive }

        if (superPeer != null) {
            // Fix P4 : ipAddress et port sont nullable dans Peer — pas de !!
            val ip = superPeer.ipAddress
            val port = superPeer.port
            if (ip != null && port != null) {
                // Fix P5 : résultat de sendDepartureNotice loggué en cas d'échec
                tcpConnectionManager.sendDepartureNotice(notice, ip, port)
                    .onSuccess { networkEventRepository.pushEvent("[DÉPART] DEPARTURE_NOTICE envoyé à ${superPeer.identity.nodeId.take(8)} (${blockIds.size} blocs)") }
                    .onFailure { networkEventRepository.pushEvent("[DÉPART] Échec envoi DEPARTURE_NOTICE : ${it.message}") }
            } else {
                networkEventRepository.pushEvent("[DÉPART] Super-Pair sans adresse connue — départ immédiat")
            }
        } else {
            networkEventRepository.pushEvent("[DÉPART] Aucun Super-Pair connu — départ immédiat")
        }

        // AC#3 : fenêtre de migration de 5 secondes
        delay(MIGRATION_WINDOW_MS)

        // AC#4 (D1 — Option 2) : fermeture du TCP server après expiration de la fenêtre.
        // Le nœud a perdu son WiFi ; son ancienne IP:port est injoignable de toute façon.
        // stopServer() finalise proprement l'état sans détruire le service entier.
        tcpConnectionManager.stopServer()
        networkEventRepository.pushEvent("[DÉPART] Fenêtre de migration expirée — TCP server arrêté")
    }.onFailure { if (it is CancellationException) throw it }
}
