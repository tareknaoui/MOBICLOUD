package com.mobicloud.data.p2p.websocket

import android.util.Log
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.models.m11_join.JoinSubType
import com.mobicloud.domain.models.m11_join.toJoinSubType
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.JoinIncomingMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

private const val TAG = "RelayWSClient"
// Magic byte préfixant un payload FORWARD JOIN — voir JoinNetworkClientImpl.JOIN_MAGIC.
private const val JOIN_MAGIC: Byte = 0xFF.toByte()

// Instance unique : le multi-instance sans store partage (Redis/pub-sub) fragmente
// l'annuaire des pairs entre les instances -- chaque device ne voit qu'un sous-ensemble
// du reseau, declenchant des elections Bully en parallele et plusieurs super-pairs dans
// le meme cluster. HA reel = chantier separe (store partage requis).
internal val RELAY_SERVER_URLS = listOf(
    "wss://mobicloud-relay-3.onrender.com"  // Render Frankfurt (EU)
)

@Singleton
class RelayWebSocketClient @Inject constructor(
    private val authSigner: RelayAuthSigner
) {
    // P14 (incident 2026-05-17) : pingInterval désactivé — le relais Render ne répond pas
    // aux PING WebSocket natifs (RFC 6455) → OkHttp tuait la connexion toutes les 60s
    // (30s interval + 30s timeout) → boucle reconnect infinie qui empêchait toute formation
    // de cluster stable. Le trafic applicatif (REGISTER_PEER keepalive 10s, heartbeats 30s,
    // GET_PEERS 10-30s) maintient déjà la connexion TCP vivante côté NAT/firewall.
    private val okHttpClient = OkHttpClient.Builder()
        .build()

    // Référence à la WebSocket active — thread-safe via @Volatile
    @Volatile private var activeWebSocket: WebSocket? = null

    // Channel pour les uploadBlock() en attente d'ACK — clé = blockId
    private val pendingUploads = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    // Dispatch SIGNAL_RECEIVED (0x0F) : Pair<fromNodeId, data> — utilisé par GossipRelayChannel.
    internal val incomingSignals = MutableSharedFlow<Pair<String, ByteArray>>(replay = 0, extraBufferCapacity = 64)

    // Dispatch ELECTION_RECEIVED (0x11) : payload JSON brut — consommé par RelayElectionNetworkClient.
    internal val incomingElectionMessages = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)

    // Dispatch JOIN (Epic 11) : SharedFlow vers JoinNetworkClientImpl.
    // Exposé internal pour que JoinNetworkClientImpl puisse s'y abonner.
    // P12/D2 (review R2) : `replay = 0` (retour à la valeur initiale) — le replay=16 ouvrait une
    // race de duplicate-processing sur reconnect collector dans le même process : 16 messages
    // anciens réinjectés → double `applyUpdate` + `markSpSeen` artificiel. La fenêtre boot AUTH_OK
    // est désormais protégée par `subscriptionCount.first { it > 0 }` côté `MobicloudP2PService`
    // AVANT le déclenchement de toute connexion WS.
    internal val joinIncomingFlow = MutableSharedFlow<JoinIncomingMessage>(replay = 0, extraBufferCapacity = 64)

    // Bus partagé : tous les RelayEvent émis par connect() y sont broadcastés.
    // Permet à RelayRepositoryImpl de s'abonner sans ouvrir une 2ème connexion WebSocket
    // (deux connect() en parallèle = deux AUTH_OK = activeWebSocket race = PEERS réponses
    // routées vers le mauvais callbackFlow → processPeerList jamais appelé → join bloqué).
    private val _eventBus = MutableSharedFlow<RelayEvent>(replay = 0, extraBufferCapacity = 128)
    internal val eventBus: SharedFlow<RelayEvent> = _eventBus.asSharedFlow()

    /**
     * Ouvre une connexion WSS persistante vers relayUrl.
     * Sur déconnexion, reconnecte avec backoff exponentiel (1s → 2s → 4s → 8s → max 30s).
     * Après 5 tentatives consécutives, bascule sur le prochain serveur de RELAY_SERVER_URLS.
     * Collectez ce Flow dans un CoroutineScope lié au Foreground Service.
     */
    fun connect(relayUrl: String): Flow<RelayEvent> = flow {
        var serverIndex = RELAY_SERVER_URLS.indexOfFirst { it == relayUrl }
            .takeIf { it >= 0 } ?: 0
        var attemptsOnCurrentServer = 0

        while (currentCoroutineContext().isActive) {
            val currentUrl = RELAY_SERVER_URLS[serverIndex % RELAY_SERVER_URLS.size]
            Log.i(TAG, "Connexion à $currentUrl (tentative ${attemptsOnCurrentServer + 1})")

            try {
                connectSingle(currentUrl).collect { event ->
                    emit(event)
                    _eventBus.tryEmit(event)
                    if (event is RelayEvent.Connected) {
                        attemptsOnCurrentServer = 0 // réinitialise le compteur
                    }
                    if (event is RelayEvent.Disconnected) {
                        throw RelayDisconnectedException(event.reason)
                    }
                }
            } catch (e: CancellationException) {
                throw e // ne pas absorber l'annulation de coroutine
            } catch (e: Exception) {
                attemptsOnCurrentServer++
                if (attemptsOnCurrentServer >= 5) {
                    Log.w(TAG, "5 tentatives échouées sur $currentUrl — failover vers instance suivante")
                    serverIndex++
                    attemptsOnCurrentServer = 0
                }
                val delayMs = min(1L shl (attemptsOnCurrentServer.coerceAtLeast(1) - 1), 30L) * 1000L
                Log.d(TAG, "Reconnexion dans ${delayMs}ms...")
                delay(delayMs)
            }
        }
    }.flowOn(Dispatchers.IO)

    private class RelayDisconnectedException(reason: String?) : Exception(reason)

    // internal pour les tests JVM (MockWebServer) — pas exposé dans le module domain
    internal fun connectSingle(url: String): Flow<RelayEvent> = callbackFlow {
        val request = Request.Builder().url(url).build()
        val flowScope: CoroutineScope = this

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // activeWebSocket est volontairement NON positionné ici :
                // tout envoi (REGISTER_PEER, uploadBlock…) doit attendre AUTH_OK
                // pour éviter que le relais rejette silencieusement des frames pré-auth.
                flowScope.launch {
                    try {
                        val authPayload = authSigner.buildAuthPayload()
                        val authFrame = RelayFraming.buildFrame(RelayMsg.AUTH, authPayload)
                        webSocket.send(authFrame.toByteString())
                    } catch (e: Exception) {
                        runCatching { Log.e(TAG, "Échec envoi AUTH : ${e.message}") }
                        close(e)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { Log.w(TAG, "Frame texte inattendue ignorée (${text.length} chars)") }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = RelayFraming.parseFrame(bytes.toByteArray()) ?: return
                val (type, payload) = frame

                when (type) {
                    RelayMsg.AUTH_OK -> {
                        activeWebSocket = webSocket // positionné après confirmation d'auth
                        trySend(RelayEvent.Connected)
                        runCatching { Log.i(TAG, "AUTH_OK — connecté à $url") }
                    }
                    RelayMsg.FORWARD -> {
                        val parsed = RelayFraming.parseForwardPayload(payload) ?: return
                        val (fromNodeId, blockId, data) = parsed
                        // Early-dispatch Epic 11 : préfixe magic 0xFF + JoinSubType (2 bytes) pour
                        // éliminer la collision avec BlockReceived dont data[0] peut accidentellement
                        // tomber dans 0x01-0x06.
                        if (data.size >= 2 && data[0] == JOIN_MAGIC && data[1] in JoinSubType.bytes) {
                            flowScope.launch {
                                joinIncomingFlow.emit(JoinIncomingMessage(fromNodeId, data[1], data.drop(2).toByteArray()))
                            }
                            return
                        }
                        trySend(RelayEvent.BlockReceived(fromNodeId, blockId, data))
                    }
                    RelayMsg.ACK -> {
                        val json = payload.toString(Charsets.UTF_8)
                        val blockId = Regex(""""blockId"\s*:\s*"([^"]+)"""")
                            .find(json)?.groupValues?.getOrNull(1) ?: return
                        pendingUploads.remove(blockId)?.complete(Unit)
                        trySend(RelayEvent.Ack(blockId))
                    }
                    RelayMsg.PEERS -> {
                        val peers = parsePeersPayload(payload)
                        trySend(RelayEvent.PeerList(peers))
                    }
                    RelayMsg.REQUEST_BLOCK_FORWARDED -> {
                        // Story 9.4 — un pair distant me demande un bloc. On émet l'événement ;
                        // le routing métier (lookup HostedBlock + push réponse) est délégué au use-case.
                        val parsed = RelayFraming.parseRequestBlockForwardedPayload(payload) ?: return
                        val (fromNodeId, blockId) = parsed
                        trySend(RelayEvent.BlockRequestForwarded(fromNodeId, blockId))
                    }
                    RelayMsg.SIGNAL_RECEIVED -> {
                        val parsed = RelayFraming.parseSignalReceivedPayload(payload) ?: return
                        val (fromNodeId, data) = parsed
                        flowScope.launch { incomingSignals.emit(Pair(fromNodeId, data)) }
                    }
                    RelayMsg.ELECTION_RECEIVED -> {
                        flowScope.launch { incomingElectionMessages.emit(payload) }
                    }
                    RelayMsg.PONG -> { /* keepalive applicatif — ignoré, OkHttp gère le ping natif */ }
                    RelayMsg.ERROR -> {
                        val msg = payload.toString(Charsets.UTF_8)
                        runCatching { Log.e(TAG, "ERROR serveur : $msg") }
                        trySend(RelayEvent.Error(msg))
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runCatching { Log.w(TAG, "WebSocket failure : ${t.message}") }
                if (activeWebSocket === webSocket) activeWebSocket = null
                trySend(RelayEvent.Disconnected(t.message))
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runCatching { Log.i(TAG, "WebSocket fermée : code=$code reason=$reason") }
                if (activeWebSocket === webSocket) activeWebSocket = null
                trySend(RelayEvent.Disconnected(reason))
                close()
            }
        }

        val ws = okHttpClient.newWebSocket(request, listener)
        awaitClose {
            // cancel() ferme le socket immédiatement (sans handshake CLOSE),
            // ce qui permet à MockWebServer.shutdown() de se terminer proprement
            // dans les tests JVM et évite un race condition sur le handshake.
            // En production, le serveur de relais traite toute fermeture TCP comme
            // une déconnexion — le handshake CLOSE n'est pas requis pour la correction.
            ws.cancel()
            if (activeWebSocket === ws) activeWebSocket = null
        }
    }

    /**
     * Upload un bloc chiffré vers destNodeId via le relais HA actif.
     * Attend l'ACK du serveur (timeout 30s).
     * Retourne Result.failure si la connexion est absente ou si l'ACK n'arrive pas.
     */
    suspend fun uploadBlock(destNodeId: String, blockId: String, data: ByteArray): Result<Unit> = runCatching {
        val ws = activeWebSocket
            ?: return Result.failure(IllegalStateException("RelayWebSocketClient : aucune connexion active"))

        val deferred = CompletableDeferred<Unit>()
        if (pendingUploads.putIfAbsent(blockId, deferred) != null) {
            return Result.failure(IllegalStateException("Upload déjà en cours pour blockId=$blockId"))
        }

        val uploadPayload = RelayFraming.buildUploadPayload(destNodeId, blockId, data)
        val frame = RelayFraming.buildFrame(RelayMsg.UPLOAD, uploadPayload)

        val sent = ws.send(frame.toByteString())
        if (!sent) {
            pendingUploads.remove(blockId)
            error("WebSocket.send() a retourné false — socket fermée")
        }

        withTimeout(30_000L) { deferred.await() }
    }.also { result ->
        if (result.isFailure) pendingUploads.remove(blockId)
    }

    /**
     * Envoie SIGNAL (0x0E) — forwarding P2P éphémère vers destNodeId (Gossip DHT, etc.).
     * Fire-and-forget : pas d'ACK attendu du serveur.
     */
    fun sendSignal(destNodeId: String, data: ByteArray): Result<Unit> {
        val ws = activeWebSocket
            ?: return Result.failure(IllegalStateException("No active relay connection"))
        val payload = RelayFraming.buildSignalPayload(destNodeId, data)
        return if (ws.send(RelayFraming.buildFrame(RelayMsg.SIGNAL, payload).toByteString())) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("WebSocket.send() retourné false"))
        }
    }

    /**
     * Envoie ELECTION_BROADCAST (0x10) — forward le payload JSON à tous les nœuds connectés
     * sauf l'émetteur. Fire-and-forget, pas d'ACK. Retourne false si WebSocket inactive.
     */
    fun sendElectionBroadcast(jsonPayload: ByteArray): Boolean {
        val ws = activeWebSocket ?: return false
        return ws.send(RelayFraming.buildFrame(RelayMsg.ELECTION_BROADCAST, jsonPayload).toByteString())
    }

    /**
     * Story 9.4 — envoie REQUEST_BLOCK (0x0C). Demande au Super-Pair distant [destNodeId] de
     * me servir le bloc [blockId]. Pas d'attente d'ACK ici (c'est le use-case côté repository
     * qui gère le `pendingBlockRequests` deferred et le timeout — la réponse arrive via FORWARD).
     * Retourne `false` si la WebSocket n'est pas active.
     */
    fun sendRequestBlock(destNodeId: String, blockId: String): Boolean {
        val ws = activeWebSocket ?: return false
        val payload = RelayFraming.buildRequestBlockPayload(destNodeId, blockId)
        return ws.send(RelayFraming.buildFrame(RelayMsg.REQUEST_BLOCK, payload).toByteString())
    }

    /** Envoie GET_PEERS (0x04) — payload vide. Réponse émise via Flow<RelayEvent.PeerList>. */
    fun sendGetPeers(): Boolean {
        val ws = activeWebSocket ?: return false
        return ws.send(RelayFraming.buildFrame(RelayMsg.GET_PEERS).toByteString())
    }

    /**
     * Envoie JOIN (0x0B) — déclare la simple présence du nœud sur le relais, sans revendiquer
     * le statut Super-Pair. Permet à l'élection Bully de se déclencher (sinon, tous les nœuds
     * connectés s'auto-déclarent Super-Pair via REGISTER_PEER et l'élection ne fire jamais).
     * ip/port sont optionnels — pertinents uniquement pour les nœuds joignables directement.
     */
    fun sendJoin(nodeId: String, ip: String?, port: Int?, reliabilityScore: Float): Boolean {
        val ws = activeWebSocket ?: return false
        val json = org.json.JSONObject().apply {
            if (ip != null) put("ip", ip)
            if (port != null) put("port", port)
            put("reliabilityScore", reliabilityScore.toDouble())
        }
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        return ws.send(RelayFraming.buildFrame(RelayMsg.JOIN, payload).toByteString())
    }

    /** Envoie REGISTER_PEER (0x03) avec les métadonnées du Super-Pair. Story 12.1 : currentMemberCount remplace GPS. */
    fun sendRegisterPeer(
        nodeId: String,
        ip: String,
        port: Int,
        reliabilityScore: Float,
        electedAt: Long,
        clusterId: String,
        freeBytes: Long,
        currentMemberCount: Int
    ): Boolean {
        val ws = activeWebSocket ?: return false
        val json = org.json.JSONObject().apply {
            put("nodeId",             nodeId)
            put("ip",                 ip)
            put("port",               port)
            put("reliabilityScore",   reliabilityScore.toDouble())
            put("electedAt",          electedAt)
            put("clusterId",          clusterId)
            put("freeBytes",          freeBytes)
            put("currentMemberCount", currentMemberCount)
        }
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        return ws.send(RelayFraming.buildFrame(RelayMsg.REGISTER_PEER, payload).toByteString())
    }

    private fun parsePeersPayload(payload: ByteArray): List<RelayPeer> {
        return runCatching {
            val arr = JSONArray(payload.toString(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RelayPeer(
                    nodeId             = obj.getString("nodeId"),
                    ip                 = obj.getString("ip"),
                    port               = obj.getInt("port"),
                    reliabilityScore   = obj.getDouble("reliabilityScore").toFloat(),
                    lastSeen           = obj.getLong("lastSeen"),
                    isSuperPair        = obj.optBoolean("isSuperPair", false),
                    clusterId          = if (obj.isNull("clusterId")) "" else obj.optString("clusterId", ""),
                    freeBytes          = if (obj.isNull("freeBytes")) 0L else obj.optLong("freeBytes", 0L),
                    pubKeySpkiDerB64   = if (obj.isNull("pubKeySpkiDerB64")) ""
                                         else obj.optString("pubKeySpkiDerB64", ""),
                    // Story 12.1 — charge cluster du SP distant (load balancing admission).
                    // P8 review : clamp [0, MAX_CLUSTER_SIZE] pour neutraliser un SP byzantin ou buggé
                    // qui annoncerait une valeur négative (Int.MIN_VALUE capterait tout le trafic via
                    // sortedBy { currentMemberCount }) ou > MAX.
                    currentMemberCount = obj.optInt("currentMemberCount", 0)
                        .coerceIn(0, com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE)
                )
            }
        }.getOrDefault(emptyList())
    }
}
