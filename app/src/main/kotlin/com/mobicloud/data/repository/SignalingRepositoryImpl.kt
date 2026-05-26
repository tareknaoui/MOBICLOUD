package com.mobicloud.data.repository

import android.os.SystemClock
import android.util.Log
import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.p2p.websocket.RELAY_SERVER_URLS
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.domain.models.m11_join.SP_TIMEOUT_MS
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.models.m11_join.toSuperPeerHint
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SignalingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val hostedBlockRepository: HostedBlockRepository,
    private val memberDao: MemberDao
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

    // Story 9.3 — Snapshot mémoire de la dernière PeerList reçue, alimenté par processPeerList.
    // Conserve clusterId/freeBytes (non filtrés) pour usage par les use-cases inter-cluster.
    // Auto-référence (peer.nodeId == localNodeId) retirée. Pas de filtrage TTL ici (les
    // entrées sont déjà fraîches côté serveur via TTL 60s).
    private val _latestPeers = MutableStateFlow<List<RelayPeer>>(emptyList())
    override val latestPeers: StateFlow<List<RelayPeer>> = _latestPeers.asStateFlow()

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

        // Story 9.3 — Mise à jour du snapshot mémoire AVANT toute autre logique.
        // Ne PAS filtrer clusterId/freeBytes/TTL ici — c'est le rôle des consommateurs.
        // Seule l'auto-référence est retirée (un Super-Pair n'est jamais candidat de placement
        // pour lui-même).
        _latestPeers.value = peers.filterNot { it.nodeId == localNodeId }

        var insertedCount = 0
        peers.forEach { peer ->
            if (peer.nodeId == localNodeId) {
                Log.w(TAG, "[DIAG] pair ${peer.nodeId.take(8)} ignoré : c'est moi")
                return@forEach
            }
            // TTL enforced cote serveur (60s via setTimeout dans signalingRegistry).
            // Ne PAS refiltrer ici : la comparaison `now - peer.lastSeen` mélange l'horloge
            // locale du téléphone avec l'horodatage serveur → désynchronisation NTP de quelques
            // secondes suffit à éjecter des pairs valides ("serveur voit 4, téléphone voit 3").

            // Story 10.1 -- decoder la cle publique transportee par le relay (SPKI-DER Base64).
            // Sans cette cle, toutes les verifications de signature Bully (ELECTION/ALIVE/COORDINATOR/
            // ABDICATION) echouent silencieusement -> personne ne reconnait personne comme super-peer.
            // Si la cle est absente (relay legacy ou session WS fermee), on skip le pair pour cette
            // iteration -- il sera retente au prochain GET_PEERS.
            val pubKeyBytes = decodePubKeyBase64(peer.pubKeySpkiDerB64)
            if (pubKeyBytes == null) {
                Log.w(TAG, "[DIAG] pair ${peer.nodeId.take(8)} ignoré : cle publique absente/invalide -- relay legacy ou session WS fermee")
                return@forEach
            }

            Log.i(TAG, "[DIAG] insertion ${peer.nodeId.take(8)}@${peer.ip}:${peer.port} isSuperPair=${peer.isSuperPair} pubKey=${pubKeyBytes.size}o")
            // isSuperPair vient maintenant du serveur (Story Bully) : true si REGISTER_PEER, false si JOIN.
            //
            // Story 9.2 — `peer.clusterId` et `peer.freeBytes` ne sont volontairement PAS
            // persistés ici. Ces deux champs sont des *snapshots volatiles* de l'annuaire HA
            // (TTL serveur 60s). Les persister dans la table `peers` créerait un risque de
            // servir des données obsolètes après expiration côté serveur. Ils seront consommés
            // en mémoire par les use-cases inter-cluster (Stories 9.3 RequestHosting,
            // 9.4 RequestBlock) directement à partir du Flow<RelayEvent.PeerList>.
            peerRepository.registerOrUpdatePeer(
                identity    = NodeIdentity(peer.nodeId, pubKeyBytes),
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

    // Decode la cle SPKI-DER Base64 envoyee par le relay (champ pubKeySpkiDerB64 de Story 10.1).
    // Retourne null si :
    //  - la chaine est vide (relay legacy ou session WS fermee cote pair)
    //  - le decodage Base64 echoue (charset corrompu)
    // Le caller skip silencieusement le pair dans ce cas.
    internal fun decodePubKeyBase64(b64: String): ByteArray? {
        if (b64.isBlank()) return null
        // java.util.Base64 (JVM-natif) plutot qu'android.util.Base64 pour rester testable
        // sans Robolectric. Le serveur encode en standard Base64 (sans wrapping ni URL-safe).
        return runCatching { java.util.Base64.getDecoder().decode(b64) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
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
        // Story 9.2 review (D1) : isoler le calcul DB dans un runCatching local — une panne
        // Room (locked, IO) ne doit PAS abortir REGISTER_PEER ni démotiver le Super-Pair.
        // Fallback freeBytes=0 (le pair sera juste écarté du placement inter-cluster pour ce
        // cycle TTL=60s, exactement comme un nœud à disque plein).
        val freeBytes = runCatching {
            val used = hostedBlockRepository.getTotalHostedBytes()
            (settings.allocatedStorageBytes - used).coerceAtLeast(0L)
        }.getOrElse { e ->
            Log.w(TAG, "freeBytes calc échoué (DB) → fallback=0 : ${e.message}")
            0L
        }
        // Story 12.1 — currentMemberCount pour load balancing côté tracker.
        // P21 : cutoff aligné sur SP_TIMEOUT_MS pour exclure les membres dont le HB est expiré
        // (évite de surestimer la charge avant la purge MonitorMemberLivenessUseCase).
        val currentMemberCount = runCatching {
            val cutoff = System.currentTimeMillis() - SP_TIMEOUT_MS
            memberDao.countActiveByClusterId(clusterId, cutoff)
        }.onFailure { e ->
            Log.w(TAG, "countActiveByClusterId échoué — coerce à 0 pour REGISTER_PEER", e)
        }.getOrDefault(0)
        val sent = relayClient.sendRegisterPeer(nodeId, ip, port, reliabilityScore, electedAt, clusterId, freeBytes, currentMemberCount)
        if (!sent) error("RelayWebSocketClient non connecté — REGISTER_PEER non envoyé")
        Log.d(TAG, "REGISTER_PEER envoyé : ip=$ip port=$port score=$reliabilityScore clusterId=${clusterId.take(8).ifEmpty { "(legacy)" }} freeBytes=$freeBytes memberCount=$currentMemberCount/$MAX_CLUSTER_SIZE")
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

    override suspend fun fetchActiveSuperPeerHints(): Result<List<SuperPeerHint>> = runCatching {
        _latestPeers.value
            .filter { it.isSuperPair }
            .map { it.toSuperPeerHint() }
    }
}
