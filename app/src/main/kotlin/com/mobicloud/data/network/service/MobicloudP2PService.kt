package com.mobicloud.data.network.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mobicloud.compose.R
import com.mobicloud.data.network.PublicIpFetcher
import com.mobicloud.data.p2p.UdpHeartbeatBroadcaster
import com.mobicloud.data.p2p.UdpHeartbeatReceiver
import com.mobicloud.data.p2p.tcp.TcpConnectionManager
import com.mobicloud.core.network.utils.NetworkUtils
import com.mobicloud.domain.models.HeartbeatPayload
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SignalingRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m01_discovery.CalculateReliabilityScoreUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class MobicloudP2PService : Service() {

    private var multicastLock: WifiManager.MulticastLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var securityRepository: SecurityRepository
    @Inject lateinit var identityRepository: IdentityRepository
    @Inject lateinit var calculateReliabilityScoreUseCase: CalculateReliabilityScoreUseCase
    @Inject lateinit var heartbeatBroadcaster: UdpHeartbeatBroadcaster
    @Inject lateinit var heartbeatReceiver: UdpHeartbeatReceiver
    @Inject lateinit var peerRepository: PeerRepository
    @Inject lateinit var networkUtils: NetworkUtils
    @Inject lateinit var signalingRepository: SignalingRepository
    @Inject lateinit var tcpConnectionManager: TcpConnectionManager
    @Inject lateinit var publicIpFetcher: PublicIpFetcher

    companion object {
        const val CHANNEL_ID = "mobicloud_p2p_channel"
        const val NOTIFICATION_ID = 404
        const val MULTICAST_LOCK_TAG = "MobiCloud:P2PMulticastLock"
        private const val PEER_TIMEOUT_MS = 15000L
        private const val EVICTION_CHECK_INTERVAL_MS = 1000L
        private const val FIREBASE_ANNOUNCE_TIMEOUT_MS = 10_000L
        private const val RELIABILITY_SCORE_INTERVAL_MS = 30_000L
        private const val LOGTAG = "MobicloudP2PService"
    }

    // P3: Guard contre les appels multiples de onStartCommand (START_STICKY)
    @Volatile private var loopsStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireMulticastLock()

        // P3: Évite de lancer plusieurs boucles P2P si START_STICKY redémarre le service
        if (!loopsStarted) {
            loopsStarted = true
            startP2PNetworkLoops()
        }

        return START_STICKY
    }

    private fun startP2PNetworkLoops() {
        serviceScope.launch {
            val identityResult = securityRepository.generateIdentity()
            if (identityResult.isFailure) {
                Log.e("MobicloudP2PService", "Failed to retrieve identity: ${identityResult.exceptionOrNull()}")
                stopSelf()
                return@launch
            }

            val identity = identityResult.getOrThrow()

            // Démarrer le TCP server EN PREMIER pour obtenir le port avant de broadcaster
            val tcpPortResult = tcpConnectionManager.startServer()
            // P1: Si le TCP server échoue, on ne diffuse pas un port 0 inutilisable
            if (tcpPortResult.isFailure) {
                Log.e("MobicloudP2PService", "TCP server failed to start — aborting P2P loops", tcpPortResult.exceptionOrNull())
                stopSelf()
                return@launch
            }
            val tcpPort = tcpPortResult.getOrThrow()

            val heartbeatPayload = HeartbeatPayload(
                nodeId = identity.nodeId,
                publicKeyBytes = identity.publicKeyBytes,
                reliabilityScore = identity.reliabilityScore,
                tcpPort = tcpPort
            )
            // Score réactif : mis à jour par Loop 6 et consommé par le broadcaster à chaque cycle
            val reliabilityScoreFlow = MutableStateFlow(identity.reliabilityScore)

            // Firebase announce — délai 10s, seulement si aucun pair local actif (AC1)
            launch {
                delay(10_000L)
                if (peerRepository.peers.value.any { it.isActive }) {
                    Log.d("MobicloudP2PService", "Pairs locaux actifs détectés — announce Firebase ignorée")
                    return@launch
                }
                // F11: ne pas publier 127.0.0.1 sur Firebase — inutilisable par les pairs distants
                val ipToAnnounce = publicIpFetcher.fetchPublicIp().getOrNull()
                if (ipToAnnounce == null || ipToAnnounce == "127.0.0.1") {
                    Log.w("MobicloudP2PService", "IP publique indisponible — announce Firebase ignorée")
                    return@launch
                }
                try {
                    withTimeout(FIREBASE_ANNOUNCE_TIMEOUT_MS) {
                        signalingRepository.registerNode(ipToAnnounce, tcpPort)
                            .onFailure { Log.w("MobicloudP2PService", "Firebase registerNode échec — mode local seul", it) }
                    }
                } catch (e: Exception) {
                    Log.w("MobicloudP2PService", "Firebase announce timeout — mode local seul", e)
                }
            }

            // Firebase Discovery & TCP Handshake
            launch {
                // F02: jobs indexés par nodeId — évite de spawner plusieurs coroutines TCP pour le même pair
                val connectionJobs = mutableMapOf<String, Job>()
                try {
                    signalingRepository.observeRemoteNodes().collectLatest { peers ->
                        for (peer in peers) {
                            // P2: Normalise le timestamp Firebase vers elapsedRealtime pour cohérence avec l'éviction
                            peerRepository.registerOrUpdatePeer(
                                peer.identity,
                                SystemClock.elapsedRealtime(),
                                peer.source,
                                peer.ipAddress,
                                peer.port
                            ).onFailure { Log.e("MobicloudP2PService", "Failed to register Firebase peer", it) }
                            // D1: Handshake seulement si pas encore connecté et aucun job en cours pour ce nœud
                            val nodeId = peer.identity.nodeId
                            if (!tcpConnectionManager.isConnected(nodeId) &&
                                connectionJobs[nodeId]?.isActive != true) {
                                connectionJobs[nodeId] = launch {
                                    tcpConnectionManager.connectToPeer(peer)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // F03: Firebase onCancelled propage une exception — le service P2P reste actif (mode local seul)
                    Log.w("MobicloudP2PService", "Firebase discovery interrompue — mode local seul", e)
                }
            }

            // Loop 1: Broadcaster — passe HeartbeatPayload + flow réactif du score
            launch {
                val result = heartbeatBroadcaster.startBroadcasting(heartbeatPayload, reliabilityScoreFlow)
                if (result.isFailure) {
                    Log.w("MobicloudP2PService", "Broadcast failed", result.exceptionOrNull())
                }
            }

            // Loop 2: Receiver — utilise HeartbeatMessage avec senderIp et tcpPort
            launch {
                heartbeatReceiver.receiveHeartbeats().collect { result ->
                    if (result.isSuccess) {
                        val msg = result.getOrThrow()
                        if (msg.identity.nodeId != identity.nodeId) {
                            // F-09: elapsedRealtime() est monotone et insensible aux sauts NTP
                            peerRepository.registerOrUpdatePeer(
                                identity = msg.identity,
                                timestampMs = SystemClock.elapsedRealtime(),
                                ipAddress = msg.senderIp,
                                port = msg.tcpPort
                            ).onFailure { Log.e("MobicloudP2PService", "Failed to register peer", it) }
                        }
                    } else {
                        Log.w("MobicloudP2PService", "Error receiving heartbeat", result.exceptionOrNull())
                    }
                }
            }

            // Loop 3: Eviction — marque INACTIVE (ne supprime pas)
            launch {
                while (isActive) {
                    peerRepository.evictStalePeers(PEER_TIMEOUT_MS, SystemClock.elapsedRealtime())
                        .onFailure { Log.e("MobicloudP2PService", "Eviction failed", it) }
                    delay(EVICTION_CHECK_INTERVAL_MS)
                }
            }

            // Loop 4: Stability Monitor — filtre sur isActive
            launch {
                peerRepository.peers.collect { peers ->
                    val hasActivePeers = peers.any { it.isActive }
                    heartbeatBroadcaster.setStable(hasActivePeers)
                    if (!hasActivePeers) {
                        heartbeatBroadcaster.resetBackoff()
                    }
                }
            }

            // Loop 5: Network Monitoring
            launch {
                networkUtils.getCurrentState().collect {
                    heartbeatBroadcaster.resetBackoff()
                }
            }

            // Loop 6: Recalcul périodique du score de fiabilité (AC #1, #2, #3, #4)
            launch {
                while (isActive) {
                    delay(RELIABILITY_SCORE_INTERVAL_MS)
                    calculateReliabilityScoreUseCase()
                        .onSuccess { newScore ->
                            identityRepository.updateReliabilityScore(identity.nodeId, newScore)
                                .onFailure { Log.w(LOGTAG, "Persistance du score de fiabilité échouée", it) }
                            reliabilityScoreFlow.value = newScore
                        }
                        .onFailure { Log.w(LOGTAG, "Recalcul du score de fiabilité échoué", it) }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MobiCloud P2P Network",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains P2P network connectivity for MobiCloud"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MobiCloud Node Active")
            .setContentText("Listening for P2P network traffic")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
                ?: return
            multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG)
            multicastLock?.setReferenceCounted(false)
        }
        if (multicastLock?.isHeld == false) {
            multicastLock?.acquire()
        }
    }

    private fun releaseMulticastLock() {
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
        }
    }

    override fun onDestroy() {
        loopsStarted = false
        tcpConnectionManager.stopServer()
        serviceScope.cancel()
        releaseMulticastLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
