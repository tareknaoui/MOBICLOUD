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
import android.os.SystemClock  // F-09: Préférer elapsedRealtime() vs currentTimeMillis() (insensible aux adjustments NTP)
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mobicloud.compose.R
import com.mobicloud.data.p2p.UdpHeartbeatBroadcaster
import com.mobicloud.data.p2p.UdpHeartbeatReceiver
import com.mobicloud.core.network.utils.NetworkUtils
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.BootstrapRepository
import com.mobicloud.domain.repository.PeerRegistry
import com.mobicloud.domain.repository.SecurityRepository
import dagger.hilt.android.AndroidEntryPoint
import com.mobicloud.data.p2p.tcp.TcpConnectionManager
import com.mobicloud.data.network.PublicIpFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MobicloudP2PService : Service() {

    private var multicastLock: WifiManager.MulticastLock? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var securityRepository: SecurityRepository
    @Inject lateinit var heartbeatBroadcaster: UdpHeartbeatBroadcaster
    @Inject lateinit var heartbeatReceiver: UdpHeartbeatReceiver
    @Inject lateinit var peerRegistry: PeerRegistry
    @Inject lateinit var networkUtils: NetworkUtils
    @Inject lateinit var bootstrapRepository: BootstrapRepository
    @Inject lateinit var tcpConnectionManager: TcpConnectionManager
    @Inject lateinit var publicIpFetcher: PublicIpFetcher

    companion object {
        const val CHANNEL_ID = "mobicloud_p2p_channel"
        const val NOTIFICATION_ID = 404
        const val MULTICAST_LOCK_TAG = "MobiCloud:P2PMulticastLock"
        // F-10: HEARTBEAT_INTERVAL_MS supprimée (constante morte — la valeur normalIntervalMs
        // est configurée directement dans le module DI qui instancie UdpHeartbeatBroadcaster).
        private const val PEER_TIMEOUT_MS = 5000L
        private const val EVICTION_CHECK_INTERVAL_MS = 1000L
    }

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
        
        startP2PNetworkLoops()

        return START_STICKY
    }

    private fun startP2PNetworkLoops() {
        serviceScope.launch {
            val identityResult = securityRepository.getIdentity()
            if (identityResult.isFailure) {
                Log.e("MobicloudP2PService", "Failed to retrieve identity: ${identityResult.exceptionOrNull()}")
                stopSelf()
                return@launch
            }
            
            val identity = identityResult.getOrThrow()
            
            // --- NOUVEAU: TCP Server & Firebase Announce ---
            launch {
                val tcpPortResult = tcpConnectionManager.startServer()
                if (tcpPortResult.isSuccess) {
                    val port = tcpPortResult.getOrThrow()
                    val ipResult = publicIpFetcher.fetchPublicIp()
                    
                    val ipToAnnounce = ipResult.getOrElse { "127.0.0.1" }
                    bootstrapRepository.announcePresence(ipToAnnounce, port)
                }
            }

            // --- NOUVEAU: Firebase Discovery & TCP Handshake ---
            launch {
                bootstrapRepository.observeActivePeers().collectLatest { peers ->
                    for (peer in peers) {
                        if (peer.identity.nodeId != identity.nodeId) {
                            peerRegistry.registerOrUpdatePeer(
                                peer.identity, 
                                peer.lastSeenTimestampMs,
                                peer.source,
                                peer.ipAddress,
                                peer.port
                            )
                            // Handshake (Fire & Forget)
                            launch {
                                tcpConnectionManager.connectToPeer(peer)
                            }
                        }
                    }
                }
            }

            // Loop 1: Broadcaster
            launch {
                val result = heartbeatBroadcaster.startBroadcasting(identity)
                if (result.isFailure) {
                    Log.w("MobicloudP2PService", "Broadcast failed", result.exceptionOrNull())
                }
            }
            
            // Loop 2: Receiver
            launch {
                heartbeatReceiver.receiveHeartbeats().collect { result ->
                    if (result.isSuccess) {
                        val peerIdentity = result.getOrThrow()
                        // Filter out loopback (our own heartbeats)
                        if (peerIdentity.nodeId != identity.nodeId) {
                            // F-09: elapsedRealtime() est monotone et insensible aux sauts NTP
                            peerRegistry.registerOrUpdatePeer(peerIdentity, SystemClock.elapsedRealtime())
                        }
                    } else {
                        Log.w("MobicloudP2PService", "Error receiving heartbeat", result.exceptionOrNull())
                    }
                }
            }
            
            // Loop 3: Eviction
            launch {
                while (isActive) {
                    // F-09: Cohérence avec registerOrUpdatePeer — même référence temporelle monotone
                    peerRegistry.evictStalePeers(PEER_TIMEOUT_MS, SystemClock.elapsedRealtime())
                    delay(EVICTION_CHECK_INTERVAL_MS)
                }
            }
            
            // Loop 4: Stability Monitor
            launch {
                peerRegistry.activePeers.collect { peers ->
                    heartbeatBroadcaster.setStable(peers.isNotEmpty())
                    if (peers.isEmpty()) {
                        // Prospect if zero peers
                        heartbeatBroadcaster.resetBackoff()
                    }
                }
            }

            // Loop 5: Network Monitoring
            launch {
                networkUtils.getCurrentState().collect {
                    // Any network state change might mean a disconnection or a new network
                    // We need to actively prospect peers by resetting backoff
                    heartbeatBroadcaster.resetBackoff()
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
        tcpConnectionManager.stopServer()
        serviceScope.cancel()
        releaseMulticastLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
