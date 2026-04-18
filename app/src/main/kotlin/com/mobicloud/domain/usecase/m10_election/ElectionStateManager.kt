package com.mobicloud.domain.usecase.m10_election

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire d'état d'élection local (in-memory).
 *
 * Suit la période de cooldown post-abdication (5 minutes) pendant laquelle le nœud
 * ne peut ni initier ni participer à une élection.
 *
 * [clockMs] est injectable pour les tests unitaires (via [advanceTimeBy] ou un fake clock).
 * En production, utilise [System.currentTimeMillis] par défaut.
 */
@Singleton
class ElectionStateManager @Inject constructor() {

    /**
     * Source de temps utilisée pour le cooldown.
     * Remplaçable dans les tests : `electionStateManager.clockMs = { fixedTimeMs }`
     */
    @JvmField
    var clockMs: () -> Long = System::currentTimeMillis

    private var cooldownUntilMs: Long = 0L

    @Synchronized
    fun setCooldown(durationMs: Long) {
        cooldownUntilMs = clockMs() + durationMs
    }

    @Synchronized
    fun isInCooldown(): Boolean {
        return clockMs() < cooldownUntilMs
    }

    /** Retourne le timestamp (ms) jusqu'auquel le nœud est en cooldown (0 si pas en cooldown). */
    @Synchronized
    fun cooldownUntil(): Long = cooldownUntilMs
}
