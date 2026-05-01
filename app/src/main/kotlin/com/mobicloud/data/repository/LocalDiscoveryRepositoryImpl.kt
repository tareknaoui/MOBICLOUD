package com.mobicloud.data.repository

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.core.security.KeystoreManager
import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.HelloMessage
import com.mobicloud.domain.models.HelloPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.LocalDiscoveryRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject

open class LocalDiscoveryRepositoryImpl @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val peerRepository: PeerRepository,
    private val networkEventRepository: NetworkEventRepository,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val externalScope: CoroutineScope
) : LocalDiscoveryRepository {

    companion object {
        private const val TAG = "LocalDiscoveryRepo"
        const val MULTICAST_GROUP = "239.255.42.99"
        const val MULTICAST_PORT = 48999
        const val HELLO_INTERVAL_MS = 5_000L
        const val MULTICAST_TIMEOUT_MS = 30_000L
        private const val BUFFER_SIZE = 2048
        private const val SOCKET_TIMEOUT_MS = 2_000
    }

    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var job: Job? = null
    // P-A1 — @Volatile garantit la visibilité de tcpPort entre start() (thread appelant) et broadcastLoop() (coroutine)
    @Volatile private var tcpPort: Int = 0
    private val startStopLock = Any()

    // P8 — mise en cache de la PrivateKeyEntry pour éviter une IPC KeyStore toutes les 5s
    private val cachedPrivateKeyEntry: KeyStore.PrivateKeyEntry by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getEntry(KeystoreManager.KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
    }

    // DN1 — tcpPort passé par le service après startServer()
    // P-A2 — synchronized(startStopLock) rend le guard check-then-act atomique
    // P-A8 — try/catch sur acquireMulticastLock() pour éviter un état incohérent si le lock échoue
    override fun start(tcpPort: Int) {
        synchronized(startStopLock) {
            if (job?.isActive == true) return
            this.tcpPort = tcpPort
            try {
                acquireMulticastLock()
            } catch (e: Exception) {
                Log.e(TAG, "Impossible d'acquérir le MulticastLock — découverte LAN désactivée", e)
                multicastLock = null
                return
            }
            job = externalScope.launch {
                launch { broadcastLoop() }
                launch { receiveLoop() }
            }
        }
    }

    override fun stop() {
        synchronized(startStopLock) {
            job?.cancel()
            job = null
            multicastLock?.release()
            multicastLock = null
        }
    }

    override fun updateTcpPort(port: Int) {
        this.tcpPort = port
    }

    private fun acquireMulticastLock() {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("mobicloud_discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun broadcastLoop() {
        val identityResult = identityRepository.getIdentity()
        if (identityResult.isFailure) {
            Log.e(TAG, "Impossible d'obtenir l'identité locale pour l'émission HELLO", identityResult.exceptionOrNull())
            return
        }
        val identity = identityResult.getOrThrow()
        val group = InetAddress.getByName(MULTICAST_GROUP)

        runCatching {
            // P4 — MulticastSocket avec TTL=1 pour confiner au lien local côté émission
            MulticastSocket().use { socket ->
                socket.timeToLive = 1
                // P5 — isActive (extension coroutine) est thread-safe, contrairement à job?.isActive
                while (currentCoroutineContext().isActive) {
                    runCatching {
                        val payload = HelloPayload(
                            nodeId = identity.nodeId,
                            publicKeyBytes = identity.publicKeyBytes,
                            tcpPort = tcpPort,
                            reliabilityScore = identity.reliabilityScore
                        )
                        val payloadBytes = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), payload)
                        val signature = signPayload(payloadBytes)
                        val message = HelloMessage(payload = payload, signature = signature)
                        val messageBytes = MobiCloudProtoBuf.encodeToByteArray(HelloMessage.serializer(), message)
                        val packet = DatagramPacket(messageBytes, messageBytes.size, group, MULTICAST_PORT)
                        socket.send(packet)
                        Log.i(TAG, "[DIAG] HELLO émis tcpPort=$tcpPort vers $MULTICAST_GROUP:$MULTICAST_PORT")
                    }.onFailure { e ->
                        // ENETUNREACH = appareil sur 4G, multicast indisponible — attendu, pas une erreur
                        if (e.message?.contains("ENETUNREACH") == true) {
                            Log.d(TAG, "Broadcast HELLO ignoré — pas de réseau multicast (4G ?)")
                        } else {
                            Log.e(TAG, "Échec émission HELLO", e)
                        }
                    }
                    delay(HELLO_INTERVAL_MS)
                }
            }
        }.onFailure { Log.e(TAG, "broadcastLoop terminée avec erreur", it) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun receiveLoop() {
        val identityResult = identityRepository.getIdentity()
        if (identityResult.isFailure) {
            Log.e(TAG, "Impossible d'obtenir l'identité locale pour la réception HELLO", identityResult.exceptionOrNull())
            return
        }
        val localNodeId = identityResult.getOrThrow().nodeId

        var lastValidHelloMs = SystemClock.elapsedRealtime()
        var fallbackLogged = false

        // P-A3 — socket construit avant le try pour garantir sa fermeture dans le finally même si bind()/joinGroup() lève
        // P7 — reuseAddress positionné avant bind()
        // P-A4 — timeToLive retiré du socket de réception (no-op sur un socket non émetteur)
        val socketGroup = InetAddress.getByName(MULTICAST_GROUP)
        val socket = MulticastSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(MULTICAST_PORT))
            socket.joinGroup(socketGroup)
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val buffer = ByteArray(BUFFER_SIZE)
            while (currentCoroutineContext().isActive) {
                fallbackLogged = logFallbackIfNeeded(lastValidHelloMs, fallbackLogged)

                val packet = DatagramPacket(buffer, buffer.size)
                runCatching { socket.receive(packet) }.onFailure {
                    return@onFailure
                }.onSuccess {
                    val bytes = packet.data.copyOf(packet.length)
                    // P10 — strip zone IPv6 ("%eth0") et protection null
                    val sourceAddress = (packet.address?.hostAddress ?: "").substringBefore('%')
                    val updated = processIncomingBytes(
                        bytes = bytes,
                        sourceAddress = sourceAddress,
                        localNodeId = localNodeId
                    )
                    if (updated) {
                        lastValidHelloMs = SystemClock.elapsedRealtime()
                        fallbackLogged = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "receiveLoop terminée avec erreur", e)
        } finally {
            // P6 — leaveGroup dans finally : exécuté même si la coroutine est annulée
            runCatching { socket.leaveGroup(socketGroup) }
            socket.close()
        }
    }

    // P3 helper — extrait pour être testable sans réseau
    internal open suspend fun logFallbackIfNeeded(lastValidHelloMs: Long, alreadyLogged: Boolean): Boolean {
        if (alreadyLogged) return true
        if (SystemClock.elapsedRealtime() - lastValidHelloMs > MULTICAST_TIMEOUT_MS) {
            networkEventRepository.pushEvent("Multicast indisponible — fallback Relais HA seul")
            return true
        }
        return false
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal suspend fun processIncomingBytes(
        bytes: ByteArray,
        sourceAddress: String,
        localNodeId: String
    ): Boolean {
        // P9 — rejet des datagrammes vides ou surdimensionnés avant désérialisation
        if (bytes.isEmpty() || bytes.size > BUFFER_SIZE) {
            Log.w(TAG, "Datagramme invalide : taille ${bytes.size} octets — ignoré")
            return false
        }
        // P-A6 — adresse source vide = packet.address était null (cas exotique Android) → ignorer
        if (sourceAddress.isEmpty()) {
            Log.w(TAG, "Adresse source manquante — datagramme ignoré")
            return false
        }
        return runCatching {
            val msg = MobiCloudProtoBuf.decodeFromByteArray(HelloMessage.serializer(), bytes)

            if (msg.payload.nodeId == localNodeId) return@runCatching false

            // DN3 — nodeId doit être le SHA-256(publicKeyBytes).take(8) — authenticité cryptographique
            val expectedNodeId = MessageDigest.getInstance("SHA-256")
                .digest(msg.payload.publicKeyBytes)
                .take(8).joinToString("") { "%02x".format(it) }
            if (msg.payload.nodeId != expectedNodeId) {
                Log.w(TAG, "nodeId ${msg.payload.nodeId.take(8)} ne correspond pas à publicKeyBytes — ignoré")
                return@runCatching false
            }

            val payloadBytes = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), msg.payload)
            if (!verifySignature(payloadBytes, msg.signature, msg.payload.publicKeyBytes)) {
                Log.w(TAG, "Signature invalide depuis ${msg.payload.nodeId.take(8)} — ignoré")
                return@runCatching false
            }

            val peerIdentity = NodeIdentity(
                nodeId = msg.payload.nodeId,
                publicKeyBytes = msg.payload.publicKeyBytes,
                reliabilityScore = msg.payload.reliabilityScore
            )
            // P-A5 — propager le succès/échec DB : retourner false si l'insertion échoue
            // pour ne pas réinitialiser le timer fallback sur un HELLO non persisté
            Log.i(TAG, "[DIAG] HELLO reçu de ${msg.payload.nodeId.take(8)}@$sourceAddress:${msg.payload.tcpPort}")
            val insertResult = peerRepository.registerOrUpdatePeer(
                identity = peerIdentity,
                timestampMs = SystemClock.elapsedRealtime(),
                source = DiscoverySource.LAN_MULTICAST,
                ipAddress = sourceAddress,
                port = msg.payload.tcpPort,
                isSuperPair = false
            )
            insertResult.onFailure { Log.e(TAG, "Échec insertion pair LAN", it) }
            if (insertResult.isSuccess) {
                Log.i(TAG, "[DIAG] Pair ${msg.payload.nodeId.take(8)} mis à jour en DB → port=${msg.payload.tcpPort}")
            }
            insertResult.isSuccess
        }.getOrElse { e ->
            Log.w(TAG, "Datagramme HELLO malformé — ignoré", e)
            false
        }
    }

    // P8 — utilise cachedPrivateKeyEntry au lieu d'ouvrir le KeyStore à chaque appel
    internal open suspend fun signPayload(payloadBytes: ByteArray): ByteArray {
        return Signature.getInstance("SHA256withECDSA").apply {
            initSign(cachedPrivateKeyEntry.privateKey)
            update(payloadBytes)
        }.sign()
    }

    internal fun verifySignature(payloadBytes: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(payloadBytes)
            }.verify(signature)
        } catch (e: Exception) {
            Log.w(TAG, "Signature invalide depuis pair", e)
            false
        }
    }
}
