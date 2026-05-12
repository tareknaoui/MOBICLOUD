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
import com.mobicloud.domain.repository.NodeSettingsRepository
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
import com.mobicloud.domain.repository.LocalDiscoveryRepository
import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.p2p.join.JoinNetworkClientImpl
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.JoinSubType
import com.mobicloud.domain.models.m11_join.Leave
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.memberUpdateSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.usecase.m10_election.RegisterSuperPeerUseCase
import com.mobicloud.domain.usecase.m10_election.RunBullyElectionUseCase
import com.mobicloud.domain.usecase.m10_election.AbdicateSuperPeerUseCase
import com.mobicloud.domain.usecase.m11_join.JoinStateMachine
import com.mobicloud.domain.usecase.m11_join.MemberHeartbeatUseCase
import com.mobicloud.domain.usecase.m11_join.MemberSnapshotCacheUseCase
import com.mobicloud.domain.usecase.m11_join.ProcessHeartbeatUseCase
import com.mobicloud.domain.usecase.m11_join.ProcessJoinRequestUseCase
import com.mobicloud.domain.usecase.m11_join.ProcessLeaveUseCase
import com.mobicloud.domain.usecase.m11_join.ProcessMemberUpdateUseCase
import com.mobicloud.domain.usecase.m11_join.SendLeaveUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
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
    @Inject lateinit var localDiscoveryRepository: LocalDiscoveryRepository
    @Inject lateinit var nodeSettingsRepository: NodeSettingsRepository
    @Inject lateinit var wifiNetworkRepository: com.mobicloud.domain.repository.WifiNetworkRepository
    @Inject lateinit var joinStateMachine: JoinStateMachine
    @Inject lateinit var joinNetworkClientImpl: JoinNetworkClientImpl
    @Inject lateinit var processJoinRequestUseCase: ProcessJoinRequestUseCase
    @Inject lateinit var relayWebSocketClient: RelayWebSocketClient
    @Inject lateinit var processHeartbeatUseCase: ProcessHeartbeatUseCase
    @Inject lateinit var processLeaveUseCase: ProcessLeaveUseCase
    @Inject lateinit var processMemberUpdateUseCase: ProcessMemberUpdateUseCase
    @Inject lateinit var sendLeaveUseCase: SendLeaveUseCase
    @Inject lateinit var memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase
    @Inject lateinit var memberHeartbeatUseCase: MemberHeartbeatUseCase
    @Inject lateinit var memberDao: MemberDao

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
            // Story 11.3 — purge des membres dont lastSeen > 24h (anti-fuite registres orphelins)
            serviceScope.launch {
                val now = System.currentTimeMillis()
                @Suppress("DEPRECATION")
                memberDao.purgeStale(
                    activeCutoffMs = now - 24 * 3600_000L,    // 24h pour ACTIVE
                    evictedCutoffMs = now - 1 * 3600_000L     // 1h pour EVICTED (spec AC2)
                )
            }
            // Story 11.3 — chargement snapshot mémoire du cluster (survit au crash, no-op si vide)
            serviceScope.launch {
                val clusterId = nodeSettingsRepository.observeSettings().first()?.clusterId ?: ""
                if (clusterId.isNotBlank()) {
                    val loaded = memberSnapshotCacheUseCase.loadFromDisk(clusterId)
                    if (loaded != null) {
                        networkEventRepository.pushEvent(
                            "[SNAPSHOT] Loaded ${loaded.size} membres depuis disque (clusterId=${clusterId.take(8)})"
                        )
                    } else {
                        Log.i(LOGTAG, "[SNAPSHOT] aucun snapshot pour clusterId=${clusterId.take(8)} — fresh boot")
                    }
                }
            }
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

            val identity = identityResult.getOrNull() ?: return@launch

            // [Review][Patch] Brancher les handlers AVANT startServer() pour éliminer la race
            // window où une connexion entrante arriverait pendant que handler == null.
            tcpConnectionManager.localIdentity = identity
            tcpConnectionManager.gossipHandler = gossipSyncUseCase
            tcpConnectionManager.blockReceiverHandler = receiveAndHostBlockUseCase

            // Epic 11 — Les use cases JOIN sont injectés par Hilt via dagger.Lazy dans
            // JoinStateMachine (cycle de dépendances résolu côté DI). Aucune construction
            // manuelle ici : un new RamMemberRegistry() créerait un registre orphelin
            // distinct du @Singleton bound dans JoinModule.

            // Collecter joinIncomingFlow du relai et dispatcher vers JoinNetworkClientImpl.
            // Côté SP : pour un JOIN_REQUEST, on délègue à ProcessJoinRequestUseCase puis on
            // renvoie la JoinResponse via sendJoinResponse (boucle SP→candidat).
            // P12/D2 (review R2) : on attend que le collector soit subscribed avant d'autoriser
            // toute init WS aval. `subscriptionCount.first { it > 0 }` est l'API officielle pour
            // attendre la subscription d'une SharedFlow → garantit qu'aucun emit JOIN/HB/MEMBER_UPDATE
            // n'est perdu entre le démarrage du collector et le AUTH_OK WebSocket.
            val collectorJob = launch {
                relayWebSocketClient.joinIncomingFlow.collect { msg ->
                    // M23 : un throw dans onRelayMessage annulait tout le collector → tous les
                    // messages JOIN/HB/MEMBER_UPDATE suivants étaient perdus jusqu'au redémarrage
                    // du service. runCatching isole la faute par message.
                    runCatching { joinNetworkClientImpl.onRelayMessage(msg) }
                        .onFailure { Log.w(LOGTAG, "[JOIN] onRelayMessage failed (msg ignoré)", it) }
                    when (msg.subTypeByte) {
                        JoinSubType.JOIN_REQUEST.byte -> {
                            runCatching {
                                val request = ProtoBuf.decodeFromByteArray(
                                    com.mobicloud.domain.models.m11_join.JoinRequest.serializer(),
                                    msg.payload
                                )
                                val response = processJoinRequestUseCase(request)
                                joinNetworkClientImpl.sendJoinResponse(msg.fromNodeId, response)
                                    .onFailure { Log.w(LOGTAG, "[JOIN-SP] sendJoinResponse échoué", it) }
                            }.onFailure { Log.w(LOGTAG, "[JOIN-SP] JOIN_REQUEST decode/process échoué", it) }
                        }
                        JoinSubType.HEARTBEAT.byte -> {
                            runCatching {
                                val hb = ProtoBuf.decodeFromByteArray<Heartbeat>(msg.payload)
                                processHeartbeatUseCase(hb)
                                    .onFailure { Log.w(LOGTAG, "[HB-SP] traitement heartbeat échoué", it) }
                            }.onFailure { Log.w(LOGTAG, "[HB-SP] decode heartbeat échoué", it) }
                        }
                        JoinSubType.MEMBER_UPDATE.byte -> {
                            runCatching {
                                val update = ProtoBuf.decodeFromByteArray<MemberUpdate>(msg.payload)
                                // T1 : validation AC14 extraite dans ProcessMemberUpdateUseCase
                                // pour permettre les tests JVM purs.
                                val outcome = processMemberUpdateUseCase(msg.fromNodeId, update)
                                if (outcome is ProcessMemberUpdateUseCase.Result.Applied) {
                                    memberHeartbeatUseCase.markSpSeen()
                                }
                            }.onFailure { Log.w(LOGTAG, "[MEMBER-UPDATE-RX] decode/process échoué", it) }
                        }
                        JoinSubType.LEAVE.byte -> {
                            runCatching {
                                val leave = ProtoBuf.decodeFromByteArray<Leave>(msg.payload)
                                processLeaveUseCase(leave)
                                    .onFailure { Log.w(LOGTAG, "[LEAVE-SP] traitement leave échoué", it) }
                            }.onFailure { Log.w(LOGTAG, "[LEAVE-SP] decode leave échoué", it) }
                        }
                    }
                }
            }
            // P12/D2 (review R2) : on bloque le reste de l'init aval jusqu'à ce que le collector
            // soit effectivement subscribed au SharedFlow. `subscriptionCount.first { it > 0 }`
            // ne suspend que jusqu'à la première subscription (typiquement < 1 ms).
            relayWebSocketClient.joinIncomingFlow.subscriptionCount.first { it > 0 }
            tcpConnectionManager.dhtRelayHandler = dhtRepository
            tcpConnectionManager.hostedBlockProvider = hostedBlockRepository
            // Story 7.2 — câblage des handlers de migration proactive
            tcpConnectionManager.departureHandler = orchestrateBlockMigrationUseCase
            tcpConnectionManager.migrationPlanHandler = executeMigrationPlanUseCase
            // Story 7.3 — handler du donneur qui exécute une directive de réplication reçue
            tcpConnectionManager.replicationPlanHandler = executeReplicationPlanUseCase

            // Story 7.1 — observation des changements réseau (4G ↔ WiFi) toujours utile pour
            // les évènements applicatifs en aval. Story 12.1 : plus de dérivation clusterId depuis
            // le SSID — le clusterId vient désormais de BullySolo (élection) ou JOIN_ACCEPT (admission).
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

            // Story 2.0 — démarrer la découverte locale LAN après startServer() pour émettre le bon tcpPort
            tcpConnectionManager.onServerPortChanged = { newPort ->
                localDiscoveryRepository.updateTcpPort(newPort)
            }
            launch { localDiscoveryRepository.start(tcpPort) }

            // Inter-réseau : annonce ce nœud comme PARTICIPANT (pas Super-Pair) sur le relais HA
            // dès le démarrage. Permet la découverte cross-network (GET_PEERS) sans bloquer Bully.
            // Le statut Super-Pair sera revendiqué via REGISTER_PEER UNIQUEMENT après victoire Bully.
            // Keepalive toutes les 30s pour rester avant l'expiration du TTL serveur (60s).
            launch {
                delay(3_000L) // attendre AUTH_OK WebSocket
                // FIX GOSSIP TCP : annoncer l'IP LAN reelle (WiFi/hotspot) au lieu
                // de "0.0.0.0". Sinon les pairs sur le meme LAN ne peuvent pas se
                // joindre directement (resolution localhost -> ECONNREFUSED en boucle).
                // Si pas d'IP LAN (4G pur derriere CGNAT), fallback "0.0.0.0" :
                // les pairs WAN passeront par le relay, le Gossip TCP direct sera
                // skip cote GossipChannel.
                suspend fun joinAndFetch() {
                    val announcedIp = wifiNetworkRepository.getLocalIpAddress() ?: "0.0.0.0"
                    signalingRepository.joinAsParticipant(
                        nodeId = identity.nodeId,
                        ip = announcedIp,
                        port = tcpPort,
                        reliabilityScore = identity.reliabilityScore
                    ).onFailure { Log.w(LOGTAG, "auto-join relais échoué", it) }
                    signalingRepository.fetchActiveSuperPeers()
                        .onFailure { Log.w(LOGTAG, "fetchActiveSuperPeers échoué", it) }
                }

                // Hook : sur reconnexion WebSocket, refait JOIN + GET_PEERS immédiatement
                (signalingRepository as? com.mobicloud.data.repository.SignalingRepositoryImpl)
                    ?.onConnectedHook = { joinAndFetch() }

                while (isActive) {
                    joinAndFetch()
                    delay(30_000L)
                }
            }

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
            // On update via le nodeId d'identityRepository (source DB-backed Room = source de
            // vérité pour l'UPDATE). securityRepository partage désormais le même KEY_ALIAS donc
            // produit le même nodeId, mais on garde ce chemin pour rester aligné sur l'entité Room.
            launch {
                val dbNodeId = identityRepository.getIdentity().getOrNull()?.nodeId
                if (dbNodeId == null) {
                    Log.w(LOGTAG, "[SCORE-LOOP] identityRepository.getIdentity() a échoué — recalcul désactivé")
                    return@launch
                }
                while (isActive) {
                    delay(RELIABILITY_SCORE_INTERVAL_MS)
                    calculateReliabilityScoreUseCase()
                        .onSuccess { newScore ->
                            identityRepository.updateReliabilityScore(dbNodeId, newScore)
                                .onFailure { Log.w(LOGTAG, "Persistance du score de fiabilité échouée", it) }
                            Log.i(LOGTAG, "[SCORE-LOOP] score recalculé = $newScore pour ${dbNodeId.take(8)}")
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

            // Inter-réseau : interroge le relais HA toutes les 10s pour récupérer la liste des
            // Super-Pairs enregistrés et les insérer dans peer_nodes (source RELAY_HA).
            // Sans ça, les pairs sur des réseaux différents (pas de HELLO LAN commun) restent
            // invisibles → distribution échoue avec "Aucun nœud actif".
            launch {
                while (isActive) {
                    signalingRepository.fetchActiveSuperPeers()
                        .onFailure { Log.w(LOGTAG, "fetchActiveSuperPeers échoué", it) }
                    delay(10_000L)
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
                                localDiscoveryRepository.updateSuperPairStatus(true)
                                superPeerJob?.cancel()
                                superPeerJob = launch {
                                    launch {
                                        registerSuperPeerUseCase(tcpPort, election.electedAt).collect { regResult ->
                                            regResult.onFailure {
                                                Log.w(LOGTAG, "Enregistrement Super-Pair Relais HA échoué — cluster isolé (mode P2P local)", it)
                                            }
                                        }
                                    }
                                    // Story 12.1 — push currentMemberCount vers LocalDiscoveryRepo pour que
                                    // les HELLO multicast reflètent la charge réelle du cluster.
                                    // P5 : clusterId re-lu à chaque tick (changement possible via JOIN_ACCEPT/Bully).
                                    // P6 : garde clusterId.isBlank() pour éviter un faux memberCount=0 broadcast.
                                    launch {
                                        while (isActive) {
                                            val clusterId = nodeSettingsRepository.getClusterIdOnce()
                                            if (clusterId.isNotBlank()) {
                                                // P21 : cutoff aligné SP_TIMEOUT_MS pour exclure membres dont le HB est expiré.
                                                val cutoff = System.currentTimeMillis() - com.mobicloud.domain.models.m11_join.SP_TIMEOUT_MS
                                                val count = runCatching { memberDao.countActiveByClusterId(clusterId, cutoff) }.getOrDefault(0)
                                                localDiscoveryRepository.updateCurrentMemberCount(count)
                                            }
                                            delay(30_000L)
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
        localDiscoveryRepository.updateSuperPairStatus(false)
        superPeerJob?.cancel()
        superPeerJob = null
        // Notifier la FSM JOIN : sans ça, JoinStateMachine reste en SuperPair alors
        // que côté réseau on a abdiqué → désync FSM/réalité.
        serviceScope.launch {
            joinStateMachine.transition(com.mobicloud.domain.models.m11_join.JoinEvent.AbdicationTriggered)
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

    override fun onDestroy() {
        loopsStarted = false
        // H25 : envoyer LEAVE gracieusement avant de tuer le scope.
        // runBlocking + withContext(NonCancellable) garantit que le LEAVE part avant
        // que serviceScope.cancel() ne le coupe à mi-route. SendLeaveUseCase est no-op
        // si l'état FSM n'est pas Member, donc safe à appeler systématiquement.
        runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    kotlinx.coroutines.withTimeoutOrNull(1500L) {
                        sendLeaveUseCase.invoke()
                    }
                }
            }
        }
        localDiscoveryRepository.stop()
        networkChangeObserver.unregister()
        tcpConnectionManager.stopServer()
        joinStateMachine.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
