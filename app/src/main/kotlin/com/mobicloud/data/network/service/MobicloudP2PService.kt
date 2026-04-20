package com.mobicloud.data.network.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mobicloud.compose.R
import com.mobicloud.data.network.PublicIpFetcher
import com.mobicloud.data.p2p.tcp.TcpConnectionManager
import com.mobicloud.core.network.utils.NetworkUtils
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SignalingRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.usecase.m01_discovery.CalculateReliabilityScoreUseCase
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.ResolveDhtConflictUseCase
import com.mobicloud.domain.usecase.m10_election.RegisterSuperPeerUseCase
import com.mobicloud.domain.usecase.m10_election.RunBullyElectionUseCase
import com.mobicloud.domain.usecase.m10_election.AbdicateSuperPeerUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class MobicloudP2PService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var securityRepository: SecurityRepository
    @Inject lateinit var identityRepository: IdentityRepository
    @Inject lateinit var calculateReliabilityScoreUseCase: CalculateReliabilityScoreUseCase
    @Inject lateinit var peerRepository: PeerRepository
    @Inject lateinit var networkUtils: NetworkUtils
    @Inject lateinit var signalingRepository: SignalingRepository
    @Inject lateinit var tcpConnectionManager: TcpConnectionManager
    @Inject lateinit var publicIpFetcher: PublicIpFetcher
    @Inject lateinit var networkEventRepository: NetworkEventRepository
    @Inject lateinit var runBullyElectionUseCase: RunBullyElectionUseCase
    @Inject lateinit var registerSuperPeerUseCase: RegisterSuperPeerUseCase
    @Inject lateinit var abdicateSuperPeerUseCase: AbdicateSuperPeerUseCase
    @Inject lateinit var gossipSyncUseCase: GossipSyncUseCase
    @Inject lateinit var resolveDhtConflictUseCase: ResolveDhtConflictUseCase

    // Accessible uniquement via abdicate() — @Volatile garantit la visibilité inter-thread
    @Volatile
    private var superPeerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "mobicloud_p2p_channel"
        const val NOTIFICATION_ID = 404
        private const val PEER_TIMEOUT_MS = 15000L
        private const val EVICTION_CHECK_INTERVAL_MS = 1000L
        private const val FIREBASE_ANNOUNCE_TIMEOUT_MS = 10_000L
        private const val RELIABILITY_SCORE_INTERVAL_MS = 30_000L
        private const val LOGTAG = "MobicloudP2PService"
        /** Durée du mandat Super-Pair avant abdication automatique (testable via overrideAbdicationDelayMs). */
        const val ABDICATION_DELAY_MS = 30 * 60 * 1000L
        /** Délai inter-cycle du monitoring d'élection — évite le hot-loop en période de cooldown. */
        private const val ELECTION_RETRY_DELAY_MS = 5_000L
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

            // Démarrer le TCP server EN PREMIER pour obtenir le port avant d'annoncer sur Firebase
            val tcpPortResult = tcpConnectionManager.startServer()
            // P1: Si le TCP server échoue, on ne publie pas un port 0 inutilisable
            if (tcpPortResult.isFailure) {
                Log.e("MobicloudP2PService", "TCP server failed to start — aborting P2P loops", tcpPortResult.exceptionOrNull())
                stopSelf()
                return@launch
            }
            val tcpPort = tcpPortResult.getOrThrow()

            // Guard P7: brancher le handler Gossip APRÈS que le serveur TCP soit prêt
            tcpConnectionManager.gossipHandler = gossipSyncUseCase

            // AC#6: purge des tombstones expirés au démarrage du service
            serviceScope.launch {
                resolveDhtConflictUseCase.purgeExpiredTombstones()
                    .onSuccess { count -> if (count > 0) Log.i(LOGTAG, "[CRDT] $count tombstones expirés purgés") }
                    .onFailure { Log.w(LOGTAG, "[CRDT] Purge tombstones échouée : ${it.message}") }
            }

            // Firebase announce — publie l'IP publique sur Firebase pour la fédération inter-réseaux
            launch {
                // F11: ne pas publier 127.0.0.1 sur Firebase — inutilisable par les pairs distants
                val ipToAnnounce = publicIpFetcher.fetchPublicIp().getOrNull()
                if (ipToAnnounce == null || ipToAnnounce == "127.0.0.1") {
                    Log.w("MobicloudP2PService", "IP publique indisponible — announce Firebase ignorée")
                    return@launch
                }
                try {
                    withTimeout(FIREBASE_ANNOUNCE_TIMEOUT_MS) {
                        signalingRepository.registerNode(ipToAnnounce, tcpPort)
                            .onSuccess { networkEventRepository.pushEvent("[TRACKER] Enregistrement Firebase réussi") }
                            .onFailure {
                                Log.w("MobicloudP2PService", "Firebase registerNode échec", it)
                                networkEventRepository.pushEvent("[TRACKER] Firebase indisponible")
                            }
                    }
                } catch (e: Exception) {
                    Log.w("MobicloudP2PService", "Firebase announce timeout", e)
                    networkEventRepository.pushEvent("[TRACKER] Firebase indisponible")
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
                            ).onSuccess {
                                networkEventRepository.pushEvent("[FIREBASE] Pair distant découvert : ${peer.identity.nodeId.take(8)}")
                            }.onFailure { Log.e("MobicloudP2PService", "Failed to register Firebase peer", it) }
                            // D1: Handshake seulement si pas encore connecté et aucun job en cours pour ce nœud
                            val nodeId = peer.identity.nodeId
                            if (!tcpConnectionManager.isConnected(nodeId) &&
                                connectionJobs[nodeId]?.isActive != true) {
                                connectionJobs[nodeId] = launch {
                                    tcpConnectionManager.connectToPeer(peer)
                                    networkEventRepository.pushEvent("[TCP] Connexion établie avec ${nodeId.take(8)}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // F03: Firebase onCancelled propage une exception — le service P2P reste actif
                    Log.w("MobicloudP2PService", "Firebase discovery interrompue", e)
                }
            }

            // Loop 3: Eviction — marque INACTIVE (ne supprime pas)
            launch {
                var previousActivePeerIds = emptySet<String>()
                while (isActive) {
                    peerRepository.evictStalePeers(PEER_TIMEOUT_MS, SystemClock.elapsedRealtime())
                        .onFailure { Log.e("MobicloudP2PService", "Eviction failed", it) }
                    val currentActivePeerIds = peerRepository.peers.value
                        .filter { it.isActive }.map { it.identity.nodeId }.toSet()
                    val evictedIds = previousActivePeerIds - currentActivePeerIds
                    evictedIds.forEach { nodeId ->
                        networkEventRepository.pushEvent("[PEER] ${nodeId.take(8)} → INACTIVE")
                    }
                    previousActivePeerIds = currentActivePeerIds
                    delay(EVICTION_CHECK_INTERVAL_MS)
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
                        }
                        .onFailure { Log.w(LOGTAG, "Recalcul du score de fiabilité échoué", it) }
                }
            }

            // Loop Gossip: cycle épidémique toutes les 2s pour synchroniser la partition DHT (AC#2, NFR-01)
            launch {
                while (isActive) {
                    gossipSyncUseCase.runGossipCycle()
                        .onFailure { e ->
                            Log.w(LOGTAG, "Cycle Gossip échoué — service continue", e)
                        }
                    delay(2000L)
                }
            }

            // Loop 7: Monitoring Bully — relance automatiquement après chaque cycle (abdication incluse)
            // Le while(isActive) est essentiel pour déclencher une nouvelle élection après abdication (AC#3).
            // Un delay de 5s entre chaque cycle évite le hot-loop pendant la période de cooldown (5 min).
            launch {
                while (isActive) {
                    runBullyElectionUseCase().collect { result ->
                        result
                            .onSuccess { election ->
                                Log.i(LOGTAG, "Élection remportée — démarrage keepalive Super-Pair Firebase")
                                superPeerJob?.cancel()
                                superPeerJob = launch {
                                    launch {
                                        registerSuperPeerUseCase(tcpPort, election.electedAt).collect { regResult ->
                                            regResult.onFailure {
                                                Log.w(LOGTAG, "Enregistrement Super-Pair Firebase échoué — cluster isolé (aucun fallback de découverte)", it)
                                            }
                                        }
                                    }
                                    launch {
                                        Log.i(LOGTAG, "Timer d'abdication démarré (${ABDICATION_DELAY_MS / 60_000}min)")
                                        delay(ABDICATION_DELAY_MS)
                                        Log.i(LOGTAG, "Timer d'abdication expiré — exécution de l'abdication")
                                        // F-05 : log l'échec broadcast mais procède toujours à l'abdication
                                        abdicateSuperPeerUseCase()
                                            .onFailure { Log.w(LOGTAG, "Broadcast ABDICATION échoué — abdication tout de même exécutée", it) }
                                        networkEventRepository.pushEvent("[ELECTION] Abdication automatique après ${ABDICATION_DELAY_MS / 60_000}min")
                                        abdicate() // Annule superPeerJob (keepalive Firebase)
                                    }
                                }
                            }
                            .onFailure { Log.d(LOGTAG, "Cycle d'élection terminé : ${it.message}") }
                    }
                    // Pause inter-cycle — évite le hot-loop quand le nœud est en cooldown
                    delay(ELECTION_RETRY_DELAY_MS)
                }
            }
        }
    }

    /** Arrête le keepalive Super-Pair (utilisé par Story 3.3 — abdication). */
    fun abdicate() {
        superPeerJob?.cancel()
        superPeerJob = null
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

    override fun onDestroy() {
        loopsStarted = false
        tcpConnectionManager.stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
