package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CircuitBreakerUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val networkEventRepository: NetworkEventRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    // Visible for testing
    internal var currentTimeProvider: () -> Long = { System.currentTimeMillis() }

    private val _isCircuitOpen = MutableStateFlow(false)
    val isCircuitOpen: StateFlow<Boolean> = _isCircuitOpen.asStateFlow()

    // P4 — Mutex protégeant toutes les structures mutables partagées entre coroutines
    private val mutex = Mutex()
    private val churnHistory = mutableListOf<Long>()
    private var previousPeersList: List<Peer> = emptyList()

    // P5 — Un seul Job de réévaluation actif à la fois
    private var reevaluationJob: Job? = null

    companion object {
        // P1 — Taille minimale du cluster pour éviter les faux positifs sur micro-clusters
        internal const val MIN_CLUSTER_SIZE = 3
        private const val CHURN_WINDOW_MS = 5 * 60 * 1000L
        private const val REEVALUATION_DELAY_MS = 2 * 60 * 1000L
        private const val CHURN_OPEN_THRESHOLD = 0.3
        private const val CHURN_CLOSE_THRESHOLD = 0.1
    }

    init {
        applicationScope.launch {
            peerRepository.peers.collect { currentPeers ->
                handlePeersUpdate(currentPeers)
            }
        }
    }

    private suspend fun handlePeersUpdate(currentPeers: List<Peer>) {
        val currentTime = currentTimeProvider()
        var shouldActivate = false

        mutex.withLock {
            // Détecter les pairs qui viennent de passer INACTIVE
            val previousActive = previousPeersList
                .filter { it.isActive }
                .associateBy { it.identity.nodeId }
            val newlyInactiveCount = currentPeers.count { peer ->
                !peer.isActive && previousActive[peer.identity.nodeId] != null
            }

            repeat(newlyInactiveCount) { churnHistory.add(currentTime) }

            // Nettoyer les événements hors-fenêtre (> 5 min)
            val windowStart = currentTime - CHURN_WINDOW_MS
            churnHistory.removeAll { it < windowStart }

            previousPeersList = currentPeers

            val totalPeers = currentPeers.size
            // P1 — Pas d'activation sur un micro-cluster (< 3 nœuds)
            if (totalPeers < MIN_CLUSTER_SIZE) return@withLock

            val churnRate = churnHistory.size.toDouble() / totalPeers.toDouble()

            if (!_isCircuitOpen.value && churnRate > CHURN_OPEN_THRESHOLD) {
                shouldActivate = true
            }
        }

        if (shouldActivate) {
            _isCircuitOpen.value = true
            val churnPct = mutex.withLock {
                val total = currentTimeProvider().let { now ->
                    previousPeersList.size.takeIf { it > 0 } ?: 1
                }
                (churnHistory.size.toDouble() / total * 100).toInt()
            }
            networkEventRepository.pushEvent(
                "WARNING: Réseau instable. Mode CIRCUIT_BREAKER activé — churn > 30% ($churnPct% sur 5 min)."
            )
            scheduleReevaluation()
        }
    }

    /**
     * P5 — Annule le job précédent avant d'en créer un nouveau pour éviter l'empilement de timers.
     */
    private fun scheduleReevaluation() {
        reevaluationJob?.cancel()
        reevaluationJob = applicationScope.launch {
            delay(REEVALUATION_DELAY_MS)
            reEvaluateCircuit()
        }
    }

    private suspend fun reEvaluateCircuit() {
        val currentTime = currentTimeProvider()
        var shouldDeactivate = false
        var shouldReschedule = false

        mutex.withLock {
            val windowStart = currentTime - CHURN_WINDOW_MS
            churnHistory.removeAll { it < windowStart }

            // P2 — Utiliser les pairs actifs comme dénominateur (capacité réelle du réseau)
            val totalActivePeers = previousPeersList.count { it.isActive }

            if (totalActivePeers < MIN_CLUSTER_SIZE) {
                // Réseau trop petit ou vide : désactiver prudemment
                shouldDeactivate = true
                return@withLock
            }

            val churnRate = churnHistory.size.toDouble() / totalActivePeers.toDouble()

            if (churnRate < CHURN_CLOSE_THRESHOLD) {
                shouldDeactivate = true
            } else {
                // Churn encore élevé : réévaluer dans 2 minutes supplémentaires
                shouldReschedule = true
            }
        }

        if (shouldDeactivate) {
            _isCircuitOpen.value = false
            networkEventRepository.pushEvent(
                "INFO: Réseau stabilisé. Mode CIRCUIT_BREAKER désactivé (churn < 10%)."
            )
        } else if (shouldReschedule) {
            scheduleReevaluation()
        }
    }
}
