package com.mobicloud.data.p2p.tcp

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse
import com.mobicloud.domain.models.BlockRequestMessage
import com.mobicloud.domain.models.BlockResponseMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.DepartureNoticeMessage
import com.mobicloud.domain.models.DhtLookupRequestMessage
import com.mobicloud.domain.models.DhtLookupResponseMessage
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.PLAN_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.ReplicationPlanMessage
import com.mobicloud.domain.models.RevokeBlockMessage
import kotlin.math.abs
import com.mobicloud.domain.usecase.m06_m07_repair_migration.DepartureNoticeHandler
import com.mobicloud.domain.usecase.m06_m07_repair_migration.MigrationPlanHandler
import com.mobicloud.domain.usecase.m06_m07_repair_migration.ReplicationPlanHandler
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipIncomingHandler
import com.mobicloud.domain.usecase.m08_hosting.ReceiveAndHostBlockUseCase
import com.mobicloud.domain.usecase.m08_hosting.ReceiveBlockResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.PushbackInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Composant central pour l'échange TCP P2P.
 * Gère à la fois l'écoute entrante (Serveur) et la connexion sortante (Client).
 */
class TcpConnectionManager @Inject constructor(
    private val securityRepository: SecurityRepository
) {
    private var serverSocket: ServerSocket? = null

    /** Ensemble des nodeId avec lesquels un handshake TCP a déjà réussi. */
    private val handshaked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // F7: @Volatile garantit la visibilité JVM entre le thread service (write) et le thread accept (read)
    @Volatile
    var gossipHandler: GossipIncomingHandler? = null

    @Volatile
    var blockReceiverHandler: ReceiveAndHostBlockUseCase? = null

    @Volatile
    var dhtRelayHandler: DhtRepository? = null

    // Story 6.2 — provider local de blocs hébergés pour servir les BLOCK_REQUEST entrants
    @Volatile
    var hostedBlockProvider: HostedBlockRepository? = null

    // Story 7.1 — handler du Super-Pair pour traiter les DEPARTURE_NOTICE entrants (implémenté en 7.2)
    @Volatile
    var departureHandler: DepartureNoticeHandler? = null

    // Story 7.2 — handler du nœud partant qui exécute le plan de migration reçu du Super-Pair
    @Volatile
    var migrationPlanHandler: MigrationPlanHandler? = null

    // Story 7.3 — handler du nœud donneur qui exécute la directive de réplication reçue du Super-Pair
    @Volatile
    var replicationPlanHandler: ReplicationPlanHandler? = null

    // Identité locale mise en cache au démarrage du service pour éviter runBlocking sur le thread d'acceptation.
    @Volatile
    var localIdentity: com.mobicloud.domain.models.NodeIdentity? = null

    companion object {
        // F2: taille maximale d'un message Gossip pour prévenir les allocations OOM via pairs malveillants
        private const val MAX_GOSSIP_MESSAGE_BYTES = 1_000_000
        private const val INCOMING_READ_TIMEOUT_MS = 3000
        private val BLOCK_ID_REGEX = Regex("^[0-9a-f]{64}$")
    }

    /** Retourne true si un handshake TCP a déjà été complété avec ce nœud. */
    fun isConnected(nodeId: String): Boolean = nodeId in handshaked

    // F-03 [Review][Patch]: Référence stockée pour permettre interrupt() dans stopServer().
    private var serverThread: Thread? = null

    // Appelé quand le ServerSocket se ferme inopinément et qu'un rebind réussit sur un nouveau port.
    // Le service branche ce callback pour propager le nouveau port dans LocalDiscoveryRepository.
    @Volatile var onServerPortChanged: ((Int) -> Unit)? = null

    // [Review][Patch] Scope dédié aux handlers de connexion entrante. Chaque connexion est
    // traitée sur Dispatchers.IO pour ne plus bloquer le thread accept (anti-DoS).
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Démarre le serveur ServerSocket sur un port disponible et l'écoute en arrière-plan.
     * @return Le port assigné (annonce via relai HA, ou 0 en V5 architecture relay-only).
     */
    suspend fun startServer(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(0) // Port dynamique attribué par le système
            val port = serverSocket!!.localPort
            Log.i("MobiCloud:TCP", "Serveur TCP P2P démarré localement sur le port $port")

            // F-03 [Review][Patch]: Thread stocké pour permettre stopServer() de l'interrompre proprement.
            serverThread = Thread {
                while (!Thread.currentThread().isInterrupted) {
                    val sock = serverSocket
                    if (sock == null || sock.isClosed) {
                        // Fermeture inattendue (pas un stopServer) → rebind sur nouveau port
                        if (Thread.currentThread().isInterrupted) break
                        try {
                            val newSocket = ServerSocket(0)
                            serverSocket = newSocket
                            val newPort = newSocket.localPort
                            Log.i("MobiCloud:TCP", "ServerSocket rebind sur port $newPort après fermeture inattendue")
                            onServerPortChanged?.invoke(newPort)
                        } catch (rebindEx: Exception) {
                            Log.e("MobiCloud:TCP", "Auto-rebind échoué — thread accept terminé", rebindEx)
                            break
                        }
                        continue
                    }
                    try {
                        val clientSocket = sock.accept()
                        connectionScope.launch { handleIncomingConnection(clientSocket) }
                    } catch (e: Exception) {
                        if (sock.isClosed) {
                            Log.w("MobiCloud:TCP", "ServerSocket fermée — tentative rebind")
                            // isClosed sera détecté en tête de boucle
                        } else if (!Thread.currentThread().isInterrupted) {
                            Log.e("MobiCloud:TCP", "Erreur accept — ServerSocket toujours ouverte", e)
                        }
                    }
                }
                Log.w("MobiCloud:TCP", "Thread accept terminé — port $port n'est plus en écoute")
            }.also { it.start() }

            Result.success(port)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gère une connexion P2P entrante.
     * Lit le premier byte pour distinguer les messages Gossip du handshake legacy.
     *
     * F-01 [Review][Patch]: runBlocking supprimé — getIdentity() n'est pas suspend.
     * F-02 [Review][Patch]: getOrElse remplace getOrThrow() pour éviter un crash non géré.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingConnection(socket: Socket) {
        try {
            socket.soTimeout = INCOMING_READ_TIMEOUT_MS  // F11: éviter blocage indéfini sur pair lent
            val senderIp = socket.inetAddress.hostAddress ?: "unknown"
            val senderPort = socket.port
            val pushback = PushbackInputStream(socket.getInputStream(), 1)
            val firstByte = pushback.read()
            if (firstByte == -1) return

            when (firstByte.toByte()) {
                GossipChannel.GOSSIP_BLOOM -> handleIncomingBloomGossip(pushback, senderIp, senderPort)
                GossipChannel.GOSSIP_DELTA_REQ -> handleIncomingDeltaRequest(socket, pushback)
                BlockTransferChannel.BLOCK_TRANSFER -> handleIncomingBlockTransfer(pushback, socket)
                BlockTransferChannel.DHT_LOOKUP_REQ -> handleDhtLookupRelay(pushback, socket)
                BlockTransferChannel.BLOCK_REQUEST -> handleBlockRequest(pushback, socket)
                DepartureChannel.DEPARTURE_NOTICE -> handleIncomingDepartureNotice(pushback)
                DepartureChannel.MIGRATION_PLAN -> handleIncomingMigrationPlan(pushback)
                DepartureChannel.REPLICATE_PLAN -> handleIncomingReplicationPlan(pushback)
                DepartureChannel.REVOKE_BLOCK -> handleIncomingRevokeBlock(pushback)
                else -> {
                    // Legacy handshake — remettre le byte et traiter normalement
                    pushback.unread(firstByte)
                    handleLegacyHandshake(socket, pushback)
                }
            }
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lors du traitement de la connexion entrante", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun handleIncomingBloomGossip(
        inp: java.io.InputStream,  // F12: socket inutilisé — paramètre supprimé
        senderIp: String,
        senderPort: Int
    ) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            // F2: rejet des messages surdimensionnés avant allocation
            if (len <= 0 || len > MAX_GOSSIP_MESSAGE_BYTES) {
                Log.w("MobiCloud:TCP", "GOSSIP_BLOOM taille invalide: $len — connexion rejetée")
                return
            }
            val bytes = ByteArray(len)
            data.readFully(bytes)
            val msg = MobiCloudProtoBuf.decodeFromByteArray(BloomFilterGossip.serializer(), bytes)
            gossipHandler?.onBloomGossipReceived(msg)
                ?: Log.w("MobiCloud:TCP", "Bloom gossip reçu mais aucun handler configuré")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture GOSSIP_BLOOM", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun handleIncomingDeltaRequest(socket: Socket, inp: java.io.InputStream) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            // F2: rejet des messages surdimensionnés avant allocation
            if (len <= 0 || len > MAX_GOSSIP_MESSAGE_BYTES) {
                Log.w("MobiCloud:TCP", "GOSSIP_DELTA_REQ taille invalide: $len — connexion rejetée")
                return
            }
            val bytes = ByteArray(len)
            data.readFully(bytes)
            val req = MobiCloudProtoBuf.decodeFromByteArray(DeltaSyncRequest.serializer(), bytes)
            val response = gossipHandler?.onDeltaSyncRequestReceived(req)
            if (response != null) {
                val respBytes = MobiCloudProtoBuf.encodeToByteArray(DeltaSyncResponse.serializer(), response)
                val out = DataOutputStream(socket.getOutputStream())
                out.writeByte(GossipChannel.GOSSIP_DELTA_RESP.toInt())
                out.writeInt(respBytes.size)
                out.write(respBytes)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture GOSSIP_DELTA_REQ", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingBlockTransfer(inp: java.io.InputStream, socket: Socket) {
        try {
            val data = DataInputStream(inp)
            val msgLen = data.readInt()
            // AC#6 : payload oversize → connexion fermée immédiatement, PAS de NACK (protection mémoire).
            // La fermeture est assurée par le finally de handleIncomingConnection.
            if (msgLen <= 0 || msgLen > ReceiveAndHostBlockUseCase.MAX_BLOCK_PAYLOAD_BYTES) {
                Log.w("MobiCloud:TCP", "BLOCK_TRANSFER taille invalide: $msgLen — connexion fermée sans NACK")
                return
            }
            val msgBytes = ByteArray(msgLen)
            data.readFully(msgBytes)
            val message = MobiCloudProtoBuf.decodeFromByteArray(BlockTransferMessage.serializer(), msgBytes)

            val handler = blockReceiverHandler
            if (handler == null) {
                Log.w("MobiCloud:TCP", "BLOCK_TRANSFER reçu mais aucun handler configuré")
                sendNack(socket, BlockTransferChannel.NACK_UNKNOWN)
                return
            }

            when (val result = handler.receive(message)) {
                is ReceiveBlockResult.Success -> {
                    val out = DataOutputStream(socket.getOutputStream())
                    val ackBytes = MobiCloudProtoBuf.encodeToByteArray(
                        com.mobicloud.domain.models.BlockAckMessage.serializer(),
                        result.ack
                    )
                    out.writeByte(BlockTransferChannel.BLOCK_ACK.toInt())
                    out.writeInt(ackBytes.size)
                    out.write(ackBytes)
                    out.flush()
                }
                is ReceiveBlockResult.TooBig -> {
                    // AC#6 : fermeture immédiate sans NACK — pas de sendNack ici
                    socket.close()
                    return
                }
                is ReceiveBlockResult.StorageFull ->
                    sendNack(socket, BlockTransferChannel.NACK_STORAGE_FULL)
                is ReceiveBlockResult.HashMismatch ->
                    sendNack(socket, BlockTransferChannel.NACK_HASH_MISMATCH)
                is ReceiveBlockResult.IoError ->
                    sendNack(socket, BlockTransferChannel.NACK_IO_ERROR)
            }
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture BLOCK_TRANSFER", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleDhtLookupRelay(inp: InputStream, socket: Socket) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > 1024) { return }
            val reqBytes = ByteArray(len).also { data.readFully(it) }
            val req = ProtoBuf.decodeFromByteArray(DhtLookupRequestMessage.serializer(), reqBytes)
            if (!BLOCK_ID_REGEX.matches(req.blockId)) {
                Log.w("MobiCloud:TCP", "DHT_LOOKUP_REQ blockId invalide: ${req.blockId.take(16)}")
                return
            }
            val entry = dhtRelayHandler?.findByBlockId(req.blockId)?.getOrNull()
            val resp = if (entry != null) {
                DhtLookupResponseMessage(entry.blockId, entry.nodeId, entry.ipAddress, entry.port, found = true, entry.timestamp)
            } else {
                DhtLookupResponseMessage(blockId = req.blockId, found = false)
            }
            val respBytes = ProtoBuf.encodeToByteArray(DhtLookupResponseMessage.serializer(), resp)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(BlockTransferChannel.DHT_LOOKUP_RESP.toInt())
            out.writeInt(respBytes.size)
            out.write(respBytes)
            out.flush()
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur handleDhtLookupRelay", e)
        }
    }

    /**
     * Story 6.2 — sert un bloc hébergé localement.
     *
     * Lit `BlockRequestMessage`, récupère le bloc via `hostedBlockProvider`, répond `BLOCK_RESPONSE`
     * (+ Protobuf payload) ou `BLOCK_NOT_FOUND` si absent / provider non configuré / blockId invalide.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleBlockRequest(inp: InputStream, socket: Socket) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > BlockTransferChannel.MAX_REQUEST_PAYLOAD_BYTES) {
                Log.w("MobiCloud:TCP", "BLOCK_REQUEST taille invalide: $len — BLOCK_NOT_FOUND")
                // [Review][Patch] P3 — renvoyer BLOCK_NOT_FOUND pour libérer le client rapidement
                // au lieu de le laisser attendre son propre timeout.
                sendBlockNotFound(socket)
                return
            }
            val reqBytes = ByteArray(len).also { data.readFully(it) }
            val req = try {
                MobiCloudProtoBuf.decodeFromByteArray(BlockRequestMessage.serializer(), reqBytes)
            } catch (e: Exception) {
                // [Review][Patch] P3 — payload corrompu : répondre NOT_FOUND, pas de silence.
                Log.w("MobiCloud:TCP", "BLOCK_REQUEST decode échoué", e)
                sendBlockNotFound(socket)
                return
            }
            if (!BLOCK_ID_REGEX.matches(req.blockId)) {
                Log.w("MobiCloud:TCP", "BLOCK_REQUEST blockId invalide: ${req.blockId.take(16)}")
                sendBlockNotFound(socket)
                return
            }
            val provider = hostedBlockProvider
            if (provider == null) {
                Log.w("MobiCloud:TCP", "BLOCK_REQUEST reçu mais aucun provider configuré")
                sendBlockNotFound(socket)
                return
            }
            val payload = provider.getBlock(req.blockId).getOrNull()
            if (payload == null) {
                sendBlockNotFound(socket)
                return
            }
            // Story 6.3 — propagation IV depuis le stockage (HostedBlockEntity.iv)
            // jusqu'au client demandeur pour permettre le déchiffrement AES-GCM.
            val resp = BlockResponseMessage(
                blockId = payload.blockId,
                fragmentIndex = payload.fragmentIndex,
                isParity = payload.isParity,
                ciphertext = payload.ciphertext,
                iv = payload.iv
            )
            val respBytes = MobiCloudProtoBuf.encodeToByteArray(BlockResponseMessage.serializer(), resp)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(BlockTransferChannel.BLOCK_RESPONSE.toInt())
            out.writeInt(respBytes.size)
            out.write(respBytes)
            out.flush()
        } catch (e: Exception) {
            // [Review][Patch] P3 — best-effort NOT_FOUND avant de fermer, pour que le client
            // bascule immédiatement sur son fallback peer au lieu d'attendre un timeout.
            Log.e("MobiCloud:TCP", "Erreur handleBlockRequest", e)
            sendBlockNotFound(socket)
        }
    }

    private fun sendBlockNotFound(socket: Socket) {
        try {
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(BlockTransferChannel.BLOCK_NOT_FOUND.toInt())
            out.flush()
        } catch (_: Exception) {}
    }

    private fun sendNack(socket: Socket, code: Int) {
        try {
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(BlockTransferChannel.BLOCK_NACK.toInt())
            out.writeInt(code)
            out.flush()
        } catch (_: Exception) {}
    }

    private fun handleLegacyHandshake(socket: Socket, inp: java.io.InputStream) {
        try {
            val input = ObjectInputStream(inp)
            val remotePublicId = input.readUTF()

            val cachedIdentity = localIdentity ?: run {
                Log.e("MobiCloud:TCP", "Identité locale non initialisée — handshake refusé")
                return
            }

            val output = ObjectOutputStream(socket.getOutputStream())
            output.writeUTF(cachedIdentity.nodeId)
            output.flush()

            Log.i("MobiCloud:TCP", "Handshake TCP entrant réussi | pair=$remotePublicId | ip=${socket.inetAddress.hostAddress}")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lors du Handshake legacy entrant", e)
        }
    }

    /**
     * Initie une connexion TCP avec un Pair distant (IP/Port récupérés via le relai HA).
     *
     * F-04 [Review][Patch]: Même pattern getOrElse que handleIncomingConnection — cohérence unifiée.
     */
    suspend fun connectToPeer(peer: Peer) = withContext(Dispatchers.IO) {
        if (peer.ipAddress == null || peer.port == null) return@withContext
        var socket: Socket? = null
        try {
            // F-04 [Review][Patch]: Pattern unifié avec handleIncomingConnection.
            val localIdentity = securityRepository.getIdentity().getOrElse { error ->
                Log.e("MobiCloud:TCP", "Impossible de récupérer l'identité locale (connexion sortante)", error)
                return@withContext
            }
            Log.i("MobiCloud:TCP", "Tentative de connexion TCP sortante vers ${peer.ipAddress}:${peer.port}...")

            // Timeout à 5 secondes pour ne pas bloquer infiniment
            socket = Socket()
            socket.connect(java.net.InetSocketAddress(peer.ipAddress!!, peer.port!!), 5000)

            // Envoi localIdentity -> Output
            val output = ObjectOutputStream(socket.getOutputStream())
            output.writeUTF(localIdentity.nodeId)
            output.flush()

            // Réception remoteIdentity <- Input
            val input = ObjectInputStream(socket.getInputStream())
            val remoteId = input.readUTF()

            // F-05 [Review][Patch]: Logs TESTPOC remplacés par des logs structurés au niveau INFO.
            Log.i("MobiCloud:TCP", "Handshake TCP sortant réussi | pair=$remoteId | ip=${peer.ipAddress}")
            handshaked.add(peer.identity.nodeId)
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lors de la connexion sortante TCP vers ${peer.ipAddress}", e)
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingDepartureNotice(inp: InputStream) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > DepartureChannel.MAX_DEPARTURE_PAYLOAD_BYTES) {
                Log.w("MobiCloud:TCP", "DEPARTURE_NOTICE taille invalide: $len — ignoré")
                return
            }
            val bytes = ByteArray(len).also { data.readFully(it) }
            val notice = MobiCloudProtoBuf.decodeFromByteArray(DepartureNoticeMessage.serializer(), bytes)
            departureHandler?.onDepartureNoticeReceived(notice)
                ?: Log.w("MobiCloud:TCP", "DEPARTURE_NOTICE reçu mais aucun handler (Story 7.2)")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture DEPARTURE_NOTICE", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendDepartureNotice(
        notice: DepartureNoticeMessage,
        ip: String,
        port: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3_000)
                // Fix P7 : soTimeout couvre l'écriture — connect() ne protège que le handshake TCP
                socket.soTimeout = 3_000
                val out = DataOutputStream(socket.getOutputStream())
                val bytes = MobiCloudProtoBuf.encodeToByteArray(DepartureNoticeMessage.serializer(), notice)
                out.writeByte(DepartureChannel.DEPARTURE_NOTICE.toInt())
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingMigrationPlan(inp: InputStream) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > DepartureChannel.MAX_MIGRATION_PLAN_BYTES) {
                Log.w("MobiCloud:TCP", "MIGRATION_PLAN taille invalide: $len — ignoré")
                return
            }
            val bytes = ByteArray(len).also { data.readFully(it) }
            val plan = MobiCloudProtoBuf.decodeFromByteArray(MigrationPlanMessage.serializer(), bytes)
            migrationPlanHandler?.onMigrationPlanReceived(plan)
                ?: Log.w("MobiCloud:TCP", "MIGRATION_PLAN reçu mais aucun handler")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture MIGRATION_PLAN", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendMigrationPlan(
        plan: MigrationPlanMessage,
        ip: String,
        port: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3_000)
                socket.soTimeout = 3_000
                val out = DataOutputStream(socket.getOutputStream())
                val bytes = MobiCloudProtoBuf.encodeToByteArray(MigrationPlanMessage.serializer(), plan)
                out.writeByte(DepartureChannel.MIGRATION_PLAN.toInt())
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingReplicationPlan(inp: InputStream) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > DepartureChannel.MAX_REPLICATE_PLAN_BYTES) {
                Log.w("MobiCloud:TCP", "REPLICATE_PLAN taille invalide: $len — ignoré")
                return
            }
            val bytes = ByteArray(len).also { data.readFully(it) }
            val plan = MobiCloudProtoBuf.decodeFromByteArray(ReplicationPlanMessage.serializer(), bytes)
            replicationPlanHandler?.onReplicationPlanReceived(plan)
                ?: Log.w("MobiCloud:TCP", "REPLICATE_PLAN reçu mais aucun handler")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // respect de l'annulation coopérative (scope teardown)
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture REPLICATE_PLAN", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendReplicationPlan(
        plan: ReplicationPlanMessage,
        ip: String,
        port: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3_000)
                socket.soTimeout = 3_000
                val out = DataOutputStream(socket.getOutputStream())
                val bytes = MobiCloudProtoBuf.encodeToByteArray(ReplicationPlanMessage.serializer(), plan)
                out.writeByte(DepartureChannel.REPLICATE_PLAN.toInt())
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingRevokeBlock(inp: InputStream) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > DepartureChannel.MAX_REVOKE_PAYLOAD_BYTES) {
                Log.w("MobiCloud:TCP", "REVOKE_BLOCK taille invalide: $len — ignoré")
                return
            }
            val bytes = ByteArray(len).also { data.readFully(it) }
            val msg = MobiCloudProtoBuf.decodeFromByteArray(RevokeBlockMessage.serializer(), bytes)

            // Anti-replay ±30s
            if (abs(System.currentTimeMillis() - msg.timestampMs) > PLAN_TIMESTAMP_WINDOW_MS) {
                Log.w("MobiCloud:TCP", "REVOKE_BLOCK hors fenêtre temporelle — ignoré")
                return
            }
            if (!BLOCK_ID_REGEX.matches(msg.blockId)) {
                Log.w("MobiCloud:TCP", "REVOKE_BLOCK blockId invalide — ignoré")
                return
            }

            // Vérification signature : "revoke:<blockId>|ts=<timestamp>"
            val payload = "revoke:${msg.blockId}|ts=${msg.timestampMs}".toByteArray()
            val valid = securityRepository.verifySignature(
                data = payload,
                signature = msg.signatureBytes,
                publicKey = msg.ownerPublicKeyBytes
            ).getOrDefault(false)
            if (!valid) {
                Log.w("MobiCloud:TCP", "REVOKE_BLOCK signature invalide — ignoré")
                return
            }

            val provider = hostedBlockProvider ?: return
            // Vérifier que le bloc appartient bien à cet owner avant de supprimer
            val block = provider.getBlock(msg.blockId).getOrNull() ?: return
            if (block.ownerId != msg.ownerNodeId) {
                Log.w("MobiCloud:TCP", "REVOKE_BLOCK ownerId mismatch — ignoré")
                return
            }

            provider.deleteBlock(msg.blockId)
            Log.i("MobiCloud:TCP", "REVOKE_BLOCK exécuté — bloc ${msg.blockId.take(16)} supprimé")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture REVOKE_BLOCK", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendRevokeBlock(
        msg: RevokeBlockMessage,
        ip: String,
        port: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3_000)
                socket.soTimeout = 3_000
                val out = DataOutputStream(socket.getOutputStream())
                val bytes = MobiCloudProtoBuf.encodeToByteArray(RevokeBlockMessage.serializer(), msg)
                out.writeByte(DepartureChannel.REVOKE_BLOCK.toInt())
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
        }
    }

    fun stopServer() {
        // F-03 [Review][Patch]: Interruption du thread avant fermeture de la socket.
        serverThread?.interrupt()
        serverThread = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        // [Review][Patch] Annulation des handlers en cours pour éviter les fuites de coroutines.
        connectionScope.cancel()
        handshaked.clear()
        // [Review][Patch] Clear de l'identité cachée — évite qu'une identité périmée survive
        // à un redémarrage de service si la regénération échoue au prochain start.
        localIdentity = null
    }
}
