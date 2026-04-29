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
import androidx.core.app.ServiceCompat
import com.mobicloud.compose.R
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
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.ExecuteMigrationPlanUseCase
import com.mobicloud.domain.usecase.m06_m07_repair_migration.ExecuteReplicationPlanUseCase
import com.mobicloud.domain.usecase.m06_m07_repair_migration.OrchestrateBlockMigrationUseCase
import com.mobicloud.domain.usecase.m06_m07_repair_migration.TriggerAutoRepairUseCase
import com.mobicloud.domain.usecase.m08_hosting.ReceiveAndHostBlockUseCase
import com.mobicloud.core.network.NetworkChangeObserver
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
    @Inject lateinit var networkEventRepository: NetworkEventRepository
    @Inject lateinit var runBullyElectionUseCase: RunBullyElectionUseCase
    @Inject lateinit var registerSuperPeerUseCase: RegisterSuperPeerUseCase
    @Inject lateinit var abdicateSuperPeerUseCase: AbdicateSuperPeerUseCase
    @Inject lateinit var gossipSyncUseCase: GossipSyncUseCase
    @Inject lateinit var resolveDhtConflictUseCase: ResolveDhtConflictUseCase
    @Inject lateinit var receiveAndHostBlockUseCase: ReceiveAndHostBlockUseCase
    @Inject lateinit var dhtRepository: DhtRepository
    @Inject lateinit var hostedBlockRepository: HostedBlockRepository
    @Inject lateinit var networkChangeObserver: NetworkChangeObserver
    @Inject lateinit var orchestrateBlockMigrationUseCase: OrchestrateBlockMigrationUseCase
    @Inject lateinit var executeMigrationPlanUseCase: ExecuteMigrationPlanUseCase
    @Inject lateinit var triggerAutoRepairUseCase: TriggerAutoRepairUseCase
    @Inject lateinit var executeReplicationPlanUseCase: ExecuteReplicationPlanUseCase

    // Accessible uniquement via abdicate() — @Volatile garantit la visibilité inter-thread
    @Volatile
    private var superPeerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "mobicloud_p2p_channel"
        const val NOTIFICATION_ID = 404
        private const val PEER_TIMEOUT_MS = 15000L
        private const val EVICTION_CHECK_INTERVAL_MS = 1000L
        private const val RELIABILITY_SCORE_INTERVAL_MS = 30_000L
        private const val LOGTAG = "MobicloudP2PService"
        /** Durée du mandat Super-Pair avant abdication automatique (testable via overrideAbdicationDelayMs). */
        const val ABDICATION_DELAY_MS = 30 * 60 * 1000L
        /** Délai inter-cycle du monitoring d'élection — évite le hot-loop en période de cooldown. */
        private const val ELECTION_RETRY_DELAY_MS = 5_000L
        /** Story 7.3 — intervalle du scan auto-réparation (compromis détection vs thrash). */
        private const val AUTO_REPAIR_SCAN_INTERVAL_MS = 10_000L
    }

    // P3: Guard contre les appels multiples de onStartCommand (START_STICKY)
    @Volatile private var loopsStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else
                0
        )

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

            // [Review][Patch] Brancher les handlers AVANT startServer() pour éliminer la race
            // window où une connexion entrante arriverait pendant que handler == null.
            tcpConnectionManager.gossipHandler = gossipSyncUseCase
            tcpConnectionManager.blockReceiverHandler = receiveAndHostBlockUseCase
            tcpConnectionManager.dhtRelayHandler = dhtRepository
            tcpConnectionManager.hostedBlockProvider = hostedBlockRepository
            // Story 7.2 — câblage des handlers de migration proactive
            tcpConnectionManager.departureHandler = orchestrateBlockMigrationUseCase
            tcpConnectionManager.migrationPlanHandler = executeMigrationPlanUseCase
            // Story 7.3 — handler du donneur qui exécute une directive de réplication reçue
            tcpConnectionManager.replicationPlanHandler = executeReplicationPlanUseCase

            // Story 7.1 — démarrer l'observation des changements réseau WiFi → 4G
            networkChangeObserver.register()

            // Démarrer le TCP server EN PREMIER pour obtenir le port avant d'annoncer sur Firebase
            val tcpPortResult = tcpConnectionManager.startServer()
            // P1: Si le TCP server échoue, on ne publie pas un port 0 inutilisable
            if (tcpPortResult.isFailure) {
                Log.e("MobicloudP2PService", "TCP server failed to start — aborting P2P loops", tcpPortResult.exceptionOrNull())
                stopSelf()
                return@launch
            }
            val tcpPort = tcpPortResult.getOrThrow()

            // AC#6: purge des tombstones expirés au démarrage du service
            serviceScope.launch {
                resolveDhtConflictUseCase.purgeExpiredTombstones()
                    .onSuccess { count -> if (count > 0) Log.i(LOGTAG, "[CRDT] $count tombstones expirés purgés") }
                    .onFailure { Log.w(LOGTAG, "[CRDT] Purge tombstones échouée : ${it.message}") }
            }

            // Discovery périodique via Relais HA — déclenche GET_PEERS toutes les 30s
            launch {
                delay(3_000L) // laisser la connexion WSS s'établir avant le premier GET_PEERS
                while (isActive) {
                    signalingRepository.fetchActiveSuperPeers()
                        .onSuccess { networkEventRepository.pushEvent("[HA] GET_PEERS envoyé — réponse attendue") }
                        .onFailure { Log.w("MobicloudP2PService", "fetchActiveSuperPeers échoué", it) }
                    delay(30_000L)
                }
            }

            // TCP Handshake — se connecter aux Super-Pairs découverts (toutes sources)
            launch {
                val connectionJobs = mutableMapOf<String, Job>()
                peerRepository.peers.collect { allPeers ->
                    val activeSuperPeers = allPeers.filter { it.isSuperPair && it.isActive }
                    for (peer in activeSuperPeers) {
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

            // Story 7.3 — Loop Auto-Repair : scan périodique des blocs sous-répliqués.
            // No-op silencieux si le nœud n'est pas Super-Pair (garde-fou interne).
            launch {
                while (isActive) {
                    triggerAutoRepairUseCase.scanAndRepair()
                        .onFailure { Log.w(LOGTAG, "Scan auto-réparation échoué", it) }
                    delay(AUTO_REPAIR_SCAN_INTERVAL_MS)
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
                                Log.i(LOGTAG, "Élection remportée — démarrage keepalive Super-Pair Relais HA")
                                superPeerJob?.cancel()
                                superPeerJob = launch {
                                    launch {
                                        registerSuperPeerUseCase(tcpPort, election.electedAt).collect { regResult ->
                                            regResult.onFailure {
                                                Log.w(LOGTAG, "Enregistrement Super-Pair Relais HA échoué — cluster isolé (mode P2P local)", it)
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
                                        abdicate() // Annule superPeerJob (keepalive Relais HA)
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
        networkChangeObserver.unregister()
        tcpConnectionManager.stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
