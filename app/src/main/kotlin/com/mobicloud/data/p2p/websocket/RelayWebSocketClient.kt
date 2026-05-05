package com.mobicloud.data.p2p.websocket

import android.util.Log
import com.mobicloud.domain.models.RelayEvent
import com.mobicloud.domain.models.RelayPeer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

private const val TAG = "RelayWSClient"

// Nœud de configuration : liste des URLs des relais HA (hardcodée — Story 8.3 pourra rendre configurable)
internal val RELAY_SERVER_URLS = listOf(
    "wss://certainty-upstage-silly.ngrok-free.dev"  // PC local via ngrok (dev)
)

@Singleton
class RelayWebSocketClient @Inject constructor(
    private val authSigner: RelayAuthSigner
) {
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Référence à la WebSocket active — thread-safe via @Volatile
    @Volatile private var activeWebSocket: WebSocket? = null

    // Channel pour les uploadBlock() en attente d'ACK — clé = blockId
    private val pendingUploads = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Unit>>()

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

    /** Envoie REGISTER_PEER (0x03) avec les métadonnées du Super-Pair. */
    fun sendRegisterPeer(
        nodeId: String,
        ip: String,
        port: Int,
        reliabilityScore: Float,
        electedAt: Long,
        clusterId: String,
        freeBytes: Long
    ): Boolean {
        val ws = activeWebSocket ?: return false
        val json = org.json.JSONObject().apply {
            put("nodeId",           nodeId)
            put("ip",               ip)
            put("port",             port)
            put("reliabilityScore", reliabilityScore.toDouble())
            put("electedAt",        electedAt)
            put("clusterId",        clusterId)
            // Story 9.2 — capacité libre snapshot (allocated - used, ≥ 0). JSONObject sérialise
            // le Long en Number JSON (sûr tant que < 2^53, soit ~9 PB).
            put("freeBytes",        freeBytes)
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
                    nodeId           = obj.getString("nodeId"),
                    ip               = obj.getString("ip"),
                    port             = obj.getInt("port"),
                    reliabilityScore = obj.getDouble("reliabilityScore").toFloat(),
                    lastSeen         = obj.getLong("lastSeen"),
                    // Champ ajouté côté serveur Story Bully ; absent dans les anciennes réponses → false
                    isSuperPair      = obj.optBoolean("isSuperPair", false),
                    // Story 9.2 — defaults garantissent la rétrocompatibilité avec
                    // d'anciennes réponses serveur sans ces champs.
                    clusterId        = obj.optString("clusterId", ""),
                    freeBytes        = obj.optLong("freeBytes", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }
}
