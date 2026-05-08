package com.mobicloud.domain.usecase.m10_election

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stratégie de persistance du cooldown post-abdication.
 *
 * En production, [SharedPrefsCooldownStore] sauve le timestamp sur disque pour
 * survivre à un kill de l'app (sinon un attaquant pourrait abdiquer puis kill+restart
 * pour bypass la pénalité de 5 min — voir review #4).
 *
 * En test, [InMemoryCooldownStore] garde la valeur en RAM.
 */
interface CooldownStore {
    fun read(): Long
    fun write(value: Long)
}

class InMemoryCooldownStore : CooldownStore {
    private var value: Long = 0L
    override fun read(): Long = value
    override fun write(value: Long) { this.value = value }
}

/**
 * Gestionnaire d'état d'élection local.
 *
 * Suit la période de cooldown post-abdication (5 minutes) pendant laquelle le nœud
 * ne peut ni initier ni participer à une élection. Persisté via [CooldownStore]
 * pour résister à un kill+restart de l'app.
 *
 * [clockMs] est injectable pour les tests unitaires.
 * En production, utilise [System.currentTimeMillis] par défaut.
 */
@Singleton
class ElectionStateManager @Inject constructor(
    private val store: CooldownStore
) {
    /** Constructeur sans dépendance pour les tests unitaires (in-memory pur). */
    constructor() : this(InMemoryCooldownStore())

    /**
     * Source de temps utilisée pour le cooldown.
     * Remplaçable dans les tests : `electionStateManager.clockMs = { fixedTimeMs }`
     */
    @JvmField
    var clockMs: () -> Long = System::currentTimeMillis

    private var cooldownUntilMs: Long = store.read()

    @Synchronized
    fun setCooldown(durationMs: Long) {
        cooldownUntilMs = clockMs() + durationMs
        store.write(cooldownUntilMs)
    }

    @Synchronized
    fun isInCooldown(): Boolean {
        return clockMs() < cooldownUntilMs
    }

    /** Retourne le timestamp (ms) jusqu'auquel le nœud est en cooldown (0 si pas en cooldown). */
    @Synchronized
    fun cooldownUntil(): Long = cooldownUntilMs
}
