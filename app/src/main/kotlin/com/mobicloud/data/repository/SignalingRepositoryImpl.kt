package com.mobicloud.data.repository

import android.os.SystemClock
import android.util.Log
import com.mobicloud.data.p2p.websocket.RELAY_SERVER_URLS
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SignalingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SignalingRepo"
private const val RELAY_TTL_MS = 60_000L

@Singleton
class SignalingRepositoryImpl @Inject constructor(
    private val relayClient: RelayWebSocketClient,
    private val peerRepository: PeerRepository,
    private val networkEventRepository: NetworkEventRepository
) : SignalingRepository {

    // Scope lié au cycle de vie du processus — correct pour un @Singleton Android.
    // En test, relayClient.connect() retourne emptyFlow() donc la coroutine init{} se termine immédiatement.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // true dès que AUTH_OK est reçu au moins une fois — permet de distinguer
    // "connexion en cours" (jamais connecté) de "connexion perdue" (était connecté)
    @Volatile internal var everConnected = false

    init {
        scope.launch {
            val url = RELAY_SERVER_URLS.firstOrNull() ?: return@launch
            relayClient.connect(url).collect { event ->
                when (event) {
                    is RelayEvent.Connected    -> everConnected = true
                    is RelayEvent.PeerList     -> processPeerList(event.peers)
                    is RelayEvent.Disconnected -> Log.w(TAG, "Relais HA déconnecté : ${event.reason}")
                    else -> Unit
                }
            }
        }
    }

    internal suspend fun processPeerList(peers: List<RelayPeer>) {
        val now = System.currentTimeMillis()
        var insertedCount = 0
        peers.forEach { peer ->
            if (maxOf(0L, now - peer.lastSeen) > RELAY_TTL_MS) return@forEach
            // La clé publique est un placeholder vide — elle sera résolue lors du TCP handshake direct.
            // Le relais est une couche de découverte, pas d'authentification.
            peerRepository.registerOrUpdatePeer(
                identity    = NodeIdentity(peer.nodeId, ByteArray(0)),
                timestampMs = SystemClock.elapsedRealtime(),
                source      = DiscoverySource.RELAY_HA,
                ipAddress   = peer.ip,
                port        = peer.port,
                isSuperPair = true
            )
            insertedCount++
        }
        if (insertedCount > 0) {
            Log.d(TAG, "GET_PEERS : $insertedCount Super-Pairs insérés (source RELAY_HA)")
        }
    }

    override suspend fun registerAsSuperPeer(
        ip: String,
        port: Int,
        reliabilityScore: Float,
        electedAt: Long,
        nodeId: String
    ): Result<Unit> = runCatching {
        val sent = relayClient.sendRegisterPeer(nodeId, ip, port, reliabilityScore, electedAt)
        if (!sent) error("RelayWebSocketClient non connecté — REGISTER_PEER non envoyé")
        Log.d(TAG, "REGISTER_PEER envoyé : ip=$ip port=$port score=$reliabilityScore")
        Unit
    }.onFailure { e ->
        Log.e(TAG, "registerAsSuperPeer échoué : ${e.message}")
        networkEventRepository.pushEvent("Signalisation HA : enregistrement Super-Pair échoué — ${e.message}")
    }

    override suspend fun fetchActiveSuperPeers(): Result<Unit> = runCatching {
        val sent = relayClient.sendGetPeers()
        if (!sent) {
            if (everConnected) {
                // Était connecté mais plus maintenant → vraie perte de connexion
                networkEventRepository.pushEvent("Signalisation HA : tous les serveurs injoignables")
            } else {
                // Jamais connecté encore → connexion en cours, pas une erreur
                Log.d(TAG, "GET_PEERS ignoré — connexion WSS en cours d'établissement")
            }
            error("RelayWebSocketClient non connecté — GET_PEERS non envoyé")
        }
        Log.d(TAG, "GET_PEERS envoyé — réponse attendue via Flow<RelayEvent.PeerList>")
        Unit
    }.onFailure { e ->
        Log.e(TAG, "fetchActiveSuperPeers échoué : ${e.message}")
    }

    override suspend fun unregisterAsSuperPeer(): Result<Unit> = runCatching {
        Log.d(TAG, "unregisterAsSuperPeer : TTL serveur se chargera de la purge")
    }
}
