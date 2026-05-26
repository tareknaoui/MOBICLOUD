package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.ISOLATION_BACKOFF_MS
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.RejoinReason
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Machine à états centrale du protocole JOIN Epic 11.
 *
 * L'état initial au boot est `Undiscovered` (perspective : restaurer l'etat precedent depuis
 * Room cluster_members pour accelerer le rejoin apres redemarrage app — non implemente en V5).
 * Le dispatcher injecté permet l'injection d'un TestDispatcher dans les tests (coroutines-test).
 */
@Singleton
class JoinStateMachine @Inject constructor(
    private val networkEventRepository: NetworkEventRepository,
    // Lazy résout le cycle de dépendances : SendJoinRequestUseCase et MarkSelfAsSuperPairUseCase
    // dépendent eux-mêmes de JoinStateMachine (pour transition()). dagger.Lazy diffère
    // l'instanciation au premier .get().
    private val sendJoinRequestUseCaseLazy: Lazy<SendJoinRequestUseCase>,
    private val markSelfAsSuperPairUseCaseLazy: Lazy<MarkSelfAsSuperPairUseCase>,
    private val bullySoloElectionUseCaseLazy: Lazy<BullySoloElectionUseCase>,
    private val memberHeartbeatUseCaseLazy: Lazy<MemberHeartbeatUseCase>,
    private val monitorMemberLivenessUseCaseLazy: Lazy<MonitorMemberLivenessUseCase>,
    private val memberSnapshotCacheUseCaseLazy: Lazy<MemberSnapshotCacheUseCase>,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _currentState = MutableStateFlow<NodeJoinState>(NodeJoinState.Undiscovered)
    val currentState: StateFlow<NodeJoinState> = _currentState.asStateFlow()

    // SupervisorJob → un timer qui crash n'annule pas le scope entier.
    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private var isolationTimerJob: Job? = null

    // Sérialise les transitions : lecture state + branchement + write doivent être atomiques.
    private val transitionMutex = Mutex()

    /** Annule toutes les coroutines (timer isolation, etc.). À appeler depuis le service onDestroy. */
    fun close() {
        scope.cancel()
    }

    suspend fun transition(event: JoinEvent) = transitionMutex.withLock {
        val state = _currentState.value
        when {
            // Undiscovered + CoordinatorReceived → Joining
            state is NodeJoinState.Undiscovered && event is JoinEvent.CoordinatorReceived -> {
                val hint = event.toSuperPeerHint()
                _currentState.value = NodeJoinState.Joining(hint, 0)
                logStateChange(state, _currentState.value, event)
                scope.launch { sendJoinRequestUseCaseLazy.get().invoke(listOf(hint)).collect {} }
            }

            // Undiscovered + NewCandidateDetected → Joining
            state is NodeJoinState.Undiscovered && event is JoinEvent.NewCandidateDetected -> {
                _currentState.value = NodeJoinState.Joining(event.hint, 0)
                logStateChange(state, _currentState.value, event)
                scope.launch { sendJoinRequestUseCaseLazy.get().invoke(listOf(event.hint)).collect {} }
            }

            // Joining + JoinAcceptReceived → Member
            state is NodeJoinState.Joining && event is JoinEvent.JoinAcceptReceived -> {
                val accept = event.accept
                _currentState.value = NodeJoinState.Member(accept.clusterId, accept.superPairNodeId)
                logStateChange(state, _currentState.value, event)
                scope.launch {
                    // P4 — Story 12.1 review : persister le clusterId joint pour activer
                    // l'affinité de session (sticky cluster dans SendJoinRequestUseCase).
                    runCatching { nodeSettingsRepository.updateClusterId(accept.clusterId) }
                    memberSnapshotCacheUseCaseLazy.get()
                        .seedFromJoinAccept(accept.clusterId, accept.superPairNodeId, accept.memberSnapshot)
                    memberHeartbeatUseCaseLazy.get().start(accept.superPairNodeId)
                }
            }

            // Joining + BullyVictory : SP mort pendant la tentative JOIN → ce nœud a gagné l'élection.
            // On transite en SuperPair directement. Le flow sendJoinRequest orphelin expire seul
            // (AllCandidatesExhausted → else → ignoré depuis SuperPair).
            state is NodeJoinState.Joining && event is JoinEvent.BullyVictory -> {
                val newState = NodeJoinState.SuperPair(event.clusterId)
                _currentState.value = newState
                logStateChange(state, newState, event)
            }

            // Joining + CoordinatorReceived : nouveau SP annoncé pendant JOIN → mettre à jour la cible.
            // On ne relance pas sendJoinRequest (évite deux flows concurrents) ; le SP courant
            // sera découvert via latestPeers → NewCandidateDetected si JOIN_ACCEPT n'arrive pas.
            state is NodeJoinState.Joining && event is JoinEvent.CoordinatorReceived -> {
                val hint = event.toSuperPeerHint()
                val newState = NodeJoinState.Joining(hint, state.attemptIndex + 1)
                _currentState.value = newState
                logStateChange(state, newState, event)
            }

            // Joining + JoinRedirectReceived → Joining (next) ou Isolated
            // SendJoinRequestUseCase gère les alternatives dans sa boucle while interne —
            // pas besoin de relancer invoke() ici (ce serait une double tentative concurrente).
            state is NodeJoinState.Joining && event is JoinEvent.JoinRedirectReceived -> {
                val alts = event.redirect.alternativeSuperPeers
                if (alts.isNotEmpty()) {
                    val next = alts.first()
                    val newState = NodeJoinState.Joining(next, state.attemptIndex + 1)
                    _currentState.value = newState
                    logStateChange(state, newState, event)
                } else {
                    val iso = NodeJoinState.Isolated(1, System.currentTimeMillis())
                    _currentState.value = iso
                    logStateChange(state, iso, event)
                    startIsolationTimer()
                }
            }

            // Joining + AllCandidatesExhausted → Isolated
            state is NodeJoinState.Joining && event is JoinEvent.AllCandidatesExhausted -> {
                val iso = NodeJoinState.Isolated(
                    state.attemptIndex + 1,
                    System.currentTimeMillis()
                )
                _currentState.value = iso
                logStateChange(state, iso, event)
                startIsolationTimer()
            }

            // Isolated + NewCandidateDetected → Joining
            state is NodeJoinState.Isolated && event is JoinEvent.NewCandidateDetected -> {
                cancelIsolationTimer()
                val newState = NodeJoinState.Joining(event.hint, 0)
                _currentState.value = newState
                logStateChange(state, newState, event)
                scope.launch { sendJoinRequestUseCaseLazy.get().invoke(listOf(event.hint)).collect {} }
            }

            // Isolated + IsolationBackoffElapsed → SuperPair (BullySolo)
            // Lancé en scope.launch : BullySolo appelle markSelf qui re-emit transition() ;
            // l'exécuter inline ré-entrerait le mutex (deadlock).
            state is NodeJoinState.Isolated && event is JoinEvent.IsolationBackoffElapsed -> {
                networkEventRepository.pushEvent("[JOIN-FSM] Isolation backoff elapsed → BullySolo election")
                scope.launch { bullySoloElectionUseCaseLazy.get().invoke() }
            }

            // Undiscovered + BullyVictory → SuperPair (premier démarrage ou redémarrage propre :
            // RunBullyElectionUseCase gagne avant que la FSM atteigne Isolated)
            state is NodeJoinState.Undiscovered && event is JoinEvent.BullyVictory -> {
                val newState = NodeJoinState.SuperPair(event.clusterId)
                _currentState.value = newState
                logStateChange(state, newState, event)
            }

            // Isolated + BullyVictory → SuperPair (sortie d'isolement via BullySolo réussie)
            state is NodeJoinState.Isolated && event is JoinEvent.BullyVictory -> {
                val newState = NodeJoinState.SuperPair(event.clusterId)
                _currentState.value = newState
                logStateChange(state, newState, event)
                // Défensif : si un heartbeatJob traîne (ne devrait pas en Isolated), le couper.
                memberHeartbeatUseCaseLazy.get().stop()
            }

            // Member + SpTimeoutDetected → Rejoining(SP_TIMEOUT, clusterId préservé)
            state is NodeJoinState.Member && event is JoinEvent.SpTimeoutDetected -> {
                val newState = NodeJoinState.Rejoining(RejoinReason.SP_TIMEOUT, state.clusterId)
                _currentState.value = newState
                logStateChange(state, newState, event)
                // RunBullyElectionUseCase est déclenché par le service — pas de dépendance directe ici
            }

            // Rejoining + NewCandidateDetected → Joining
            // Fallback : le COORDINATOR du nouveau SP a été perdu via relay. Le nœud trouve
            // le nouveau SP dans le tracker (latestPeers) et tente un JOIN direct plutôt que
            // d'attendre un second COORDINATOR qui ne viendra peut-être jamais.
            state is NodeJoinState.Rejoining && event is JoinEvent.NewCandidateDetected -> {
                cancelIsolationTimer()
                val newState = NodeJoinState.Joining(event.hint, 0)
                _currentState.value = newState
                logStateChange(state, newState, event)
                memberHeartbeatUseCaseLazy.get().stop()
                scope.launch { sendJoinRequestUseCaseLazy.get().invoke(listOf(event.hint)).collect {} }
            }

            // Rejoining + BullyVictory → SuperPair
            // markSelf est appelé par RunBullyElectionUseCase AVANT d'émettre BullyVictory
            // (idem BullySoloElectionUseCase) ; rappeler markSelf ici provoquerait un re-entry
            // dans transition() → deadlock du mutex.
            state is NodeJoinState.Rejoining && event is JoinEvent.BullyVictory -> {
                // P13/D3 (review R2) : assert que `event.clusterId == state.clusterId` (les deux
                // doivent référencer le même cluster, sinon RunBullyElectionUseCase a divergé).
                // Log + utiliser state.clusterId comme source de vérité (préservée par H9).
                if (event.clusterId != state.clusterId) {
                    networkEventRepository.pushEvent(
                        "[JOIN-FSM] WARN BullyVictory clusterId mismatch state=${state.clusterId.take(8)} event=${event.clusterId.take(8)} — utilise state"
                    )
                }
                val newState = NodeJoinState.SuperPair(state.clusterId)
                _currentState.value = newState
                logStateChange(state, newState, event)
                // checkSpTimeout l'a normalement déjà stoppé, mais idempotent par sécurité.
                memberHeartbeatUseCaseLazy.get().stop()
            }

            // Rejoining + CoordinatorReceived (même cluster) → Member
            // Cas : un autre nœud a gagné Bully pendant que ce nœud était en Rejoining.
            // BullyLost n'est jamais émis par Loop 7 (RunBullyElectionUseCase émet seulement
            // Result.failure). Ce chemin via CoordinatorReceived est la vraie sortie de Rejoining
            // quand le nœud perd l'élection — sans ça, le nœud reste bloqué en Rejoining indéfiniment.
            state is NodeJoinState.Rejoining && event is JoinEvent.CoordinatorReceived
                && event.clusterId == state.clusterId -> {
                val newState = NodeJoinState.Member(state.clusterId, event.senderNodeId)
                _currentState.value = newState
                logStateChange(state, newState, event)
                memberHeartbeatUseCaseLazy.get().stop()
                memberHeartbeatUseCaseLazy.get().start(event.senderNodeId)
                memberHeartbeatUseCaseLazy.get().markSpSeen()
                networkEventRepository.pushEvent(
                    "[JOIN-FSM] Rejoining → Member via COORDINATOR ${event.senderNodeId.toHexString().take(8)} (nouveau SP élu)"
                )
            }

            // Rejoining + BullyLost → Member (nouveau SP, clusterId restauré depuis Rejoining)
            // Conservé pour compatibilité si BullyLost est émis à l'avenir.
            state is NodeJoinState.Rejoining && event is JoinEvent.BullyLost -> {
                val memberId = event.newSuperPairNodeId
                val newState = NodeJoinState.Member(state.clusterId, memberId)
                _currentState.value = newState
                logStateChange(state, newState, event)
                memberHeartbeatUseCaseLazy.get().stop()
                memberHeartbeatUseCaseLazy.get().start(memberId)
            }

            // SuperPair → Undiscovered (abdication volontaire Story 3.3)
            state is NodeJoinState.SuperPair && event is JoinEvent.AbdicationTriggered -> {
                monitorMemberLivenessUseCaseLazy.get().stop()
                _currentState.value = NodeJoinState.Undiscovered
                logStateChange(state, NodeJoinState.Undiscovered, event)
            }

            // SuperPair → Undiscovered (défaite Bully involontaire — concurrent SP a gagné)
            state is NodeJoinState.SuperPair && event is JoinEvent.BullyLost -> {
                // P8 (review R2) : stop le monitor — sinon le clusterId resté caché (M8) reste
                // référencé alors que le SP n'est plus le nôtre, et un futur start() ne re-init
                // pas la cache. Symétrique avec AbdicationTriggered (déjà stoppé).
                monitorMemberLivenessUseCaseLazy.get().stop()
                _currentState.value = NodeJoinState.Undiscovered
                logStateChange(state, NodeJoinState.Undiscovered, event)
            }

            // Member + BullyVictory : ce nœud a gagné l'élection déclenchée après SP_TIMEOUT.
            // markSelf est invoqué côté caller (RunBullyElectionUseCase), pas ici (anti-deadlock).
            state is NodeJoinState.Member && event is JoinEvent.BullyVictory -> {
                val newState = NodeJoinState.SuperPair(event.clusterId)
                _currentState.value = newState
                logStateChange(state, newState, event)
                // CRITIQUE : sans stop(), le nouveau SP continue d'envoyer des HB à lui-même
                // (ancien spNodeId capturé dans la coroutine sendOnce). Coroutine leak.
                memberHeartbeatUseCaseLazy.get().stop()
            }

            // Member + CoordinatorReceived (même clusterId, nouveau SP) → swap SP sans re-JOIN (FR-11.8)
            // Patch AC12 : branche manquante Story 11.2. COORDINATOR du nouveau SP post-Bully.
            state is NodeJoinState.Member && event is JoinEvent.CoordinatorReceived
                && event.clusterId == state.clusterId
                && !event.senderNodeId.contentEquals(state.superPairNodeId) -> {
                val oldSpHex = state.superPairNodeId.toHexString().take(8)
                val newSpHex = event.senderNodeId.toHexString().take(8)
                val newState = NodeJoinState.Member(state.clusterId, event.senderNodeId)
                _currentState.value = newState
                logStateChange(state, newState, event)
                memberHeartbeatUseCaseLazy.get().stop()
                memberHeartbeatUseCaseLazy.get().start(event.senderNodeId)
                memberHeartbeatUseCaseLazy.get().markSpSeen()
                networkEventRepository.pushEvent(
                    "[JOIN-FSM] SP changé sans re-JOIN: $oldSpHex → $newSpHex (continuité Bully post-mort)"
                )
            }

            else -> {
                val warn = "[JOIN-FSM] WARN Transition ignorée: ${state::class.simpleName} × ${event::class.simpleName}"
                networkEventRepository.pushEvent(warn)
                android.util.Log.w("JOIN_FSM", warn)
            }
        }
    }

    private fun startIsolationTimer() {
        cancelIsolationTimer()
        isolationTimerJob = scope.launch {
            delay(ISOLATION_BACKOFF_MS)
            // Vérifier que l'état est toujours Isolated avant de déclencher BullySolo
            if (_currentState.value is NodeJoinState.Isolated) {
                transition(JoinEvent.IsolationBackoffElapsed)
            }
        }
    }

    fun cancelIsolationTimer() {
        isolationTimerJob?.cancel()
        isolationTimerJob = null
    }

    private suspend fun logStateChange(old: NodeJoinState, new: NodeJoinState, event: JoinEvent) {
        val msg = "[JOIN-FSM] $old → $new (event=${event::class.simpleName})"
        networkEventRepository.pushEvent(msg)
        android.util.Log.d("JOIN_FSM", msg)
    }

    private fun JoinEvent.CoordinatorReceived.toSuperPeerHint(): com.mobicloud.domain.models.m11_join.SuperPeerHint =
        com.mobicloud.domain.models.m11_join.SuperPeerHint(
            nodeId = senderNodeId,
            clusterId = clusterId,
            ipAddress = "",
            port = 0,
            reliabilityScore = 0f,
            currentMemberCount = 0
        )
}
