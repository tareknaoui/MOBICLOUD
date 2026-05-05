package com.mobicloud.data.repository

import android.os.SystemClock
import android.util.Log
import com.mobicloud.data.p2p.websocket.RELAY_SERVER_URLS
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
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
    private val networkEventRepository: NetworkEventRepository,
    private val identityRepository: IdentityRepository,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val hostedBlockRepository: HostedBlockRepository
) : SignalingRepository {

    // Scope lié au cycle de vie du processus — correct pour un @Singleton Android.
    // En test, relayClient.connect() retourne emptyFlow() donc la coroutine init{} se termine immédiatement.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // true dès que AUTH_OK est reçu au moins une fois — permet de distinguer
    // "connexion en cours" (jamais connecté) de "connexion perdue" (était connecté)
    @Volatile internal var everConnected = false

    // Déclenchement immédiat de REGISTER + GET_PEERS à chaque reconnexion WebSocket.
    // Sans ça, après un disconnect/reconnect, l'autre pair ne nous voit plus avant ~30s.
    @Volatile var onConnectedHook: (suspend () -> Unit)? = null

    init {
        scope.launch {
            val url = RELAY_SERVER_URLS.firstOrNull() ?: return@launch
            relayClient.connect(url).collect { event ->
                when (event) {
                    is RelayEvent.Connected    -> {
                        everConnected = true
                        Log.i(TAG, "[DIAG] WebSocket reconnecté — déclenchement REGISTER + GET_PEERS immédiats")
                        onConnectedHook?.invoke()
                    }
                    is RelayEvent.PeerList     -> processPeerList(event.peers)
                    is RelayEvent.Disconnected -> Log.w(TAG, "Relais HA déconnecté : ${event.reason}")
                    else -> Unit
                }
            }
        }
    }

    internal suspend fun processPeerList(peers: List<RelayPeer>) {
        val now = System.currentTimeMillis()
        val localNodeId = identityRepository.getIdentity().getOrNull()?.nodeId
        Log.i(TAG, "[DIAG] processPeerList reçu ${peers.size} pairs (self=${localNodeId?.take(8)}) : ${peers.map { "${it.nodeId.take(8)}@${it.ip}:${it.port} lastSeen=${now - it.lastSeen}ms" }}")
        var insertedCount = 0
        peers.forEach { peer ->
            if (peer.nodeId == localNodeId) {
                Log.w(TAG, "[DIAG] pair ${peer.nodeId.take(8)} ignoré : c'est moi")
                return@forEach
            }
            val ageMs = maxOf(0L, now - peer.lastSeen)
            if (ageMs > RELAY_TTL_MS) {
                Log.w(TAG, "[DIAG] pair ${peer.nodeId.take(8)} ignoré : trop ancien ($ageMs ms > $RELAY_TTL_MS)")
                return@forEach
            }
            Log.i(TAG, "[DIAG] insertion ${peer.nodeId.take(8)}@${peer.ip}:${peer.port} isSuperPair=${peer.isSuperPair}")
            // La clé publique est un placeholder vide — elle sera résolue lors du TCP handshake direct.
            // Le relais est une couche de découverte, pas d'authentification.
            // isSuperPair vient maintenant du serveur (Story Bully) : true si REGISTER_PEER, false si JOIN.
            //
            // Story 9.2 — `peer.clusterId` et `peer.freeBytes` ne sont volontairement PAS
            // persistés ici. Ces deux champs sont des *snapshots volatiles* de l'annuaire HA
            // (TTL serveur 60s). Les persister dans la table `peers` créerait un risque de
            // servir des données obsolètes après expiration côté serveur. Ils seront consommés
            // en mémoire par les use-cases inter-cluster (Stories 9.3 RequestHosting,
            // 9.4 RequestBlock) directement à partir du Flow<RelayEvent.PeerList>.
            peerRepository.registerOrUpdatePeer(
                identity    = NodeIdentity(peer.nodeId, ByteArray(0)),
                timestampMs = SystemClock.elapsedRealtime(),
                source      = DiscoverySource.RELAY_HA,
                ipAddress   = peer.ip,
                port        = peer.port,
                isSuperPair = peer.isSuperPair
            )
            insertedCount++
        }
        Log.i(TAG, "[DIAG] processPeerList END — $insertedCount insérés sur ${peers.size}")
    }

    override suspend fun joinAsParticipant(
        nodeId: String,
        ip: String?,
        port: Int?,
        reliabilityScore: Float
    ): Result<Unit> = runCatching {
        val sent = relayClient.sendJoin(nodeId, ip, port, reliabilityScore)
        if (!sent) error("RelayWebSocketClient non connecté — JOIN non envoyé")
        Log.d(TAG, "JOIN envoyé : nodeId=${nodeId.take(8)} ip=$ip port=$port score=$reliabilityScore")
        Unit
    }.onFailure { e ->
        Log.w(TAG, "joinAsParticipant échoué : ${e.message}")
    }

    override suspend fun registerAsSuperPeer(
        ip: String,
        port: Int,
        reliabilityScore: Float,
        electedAt: Long,
        nodeId: String
    ): Result<Unit> = runCatching {
        val settings = nodeSettingsRepository.getSettings()
        val clusterId = settings.clusterId
        // Story 9.2 — snapshot best-effort de la capacité libre. Deux requêtes DB séquentielles ;
        // une concurrence ajout-bloc/REGISTER_PEER peut donner ±1 bloc d'écart, acceptable.
        val used = hostedBlockRepository.getTotalHostedBytes()
        val freeBytes = (settings.allocatedStorageBytes - used).coerceAtLeast(0L)
        val sent = relayClient.sendRegisterPeer(nodeId, ip, port, reliabilityScore, electedAt, clusterId, freeBytes)
        if (!sent) error("RelayWebSocketClient non connecté — REGISTER_PEER non envoyé")
        Log.d(TAG, "REGISTER_PEER envoyé : ip=$ip port=$port score=$reliabilityScore clusterId=${clusterId.take(8).ifEmpty { "(legacy)" }} freeBytes=$freeBytes")
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
