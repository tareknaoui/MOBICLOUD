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
 * État des numéros de mandat (terms), persisté pour survivre à un redémarrage :
 * un nœud qui oublierait le plus haut term vu pourrait de nouveau accepter un
 * Super-Pair périmé.
 */
data class TermState(
    /** Cluster auquel se rapporte [highestTerm]. */
    val clusterId: String = "",
    /** Plus haut term de mandat observé (COORDINATOR) pour [clusterId]. */
    val highestTerm: Long = 0L,
    /** Term du mandat local en cours, 0 si le nœud n'est pas Super-Pair. */
    val ownTerm: Long = 0L,
    /** Fin (ms) du retrait après rotation du mandat, 0 si aucun retrait. */
    val rotationYieldUntilMs: Long = 0L
)

interface TermStore {
    fun read(): TermState
    fun write(state: TermState)
}

class InMemoryTermStore : TermStore {
    private var state = TermState()
    override fun read(): TermState = state
    override fun write(state: TermState) { this.state = state }
}

/**
 * Gestionnaire d'état d'élection local.
 *
 * 1. Cooldown post-abdication (5 minutes) pendant lequel le nœud ne peut ni initier
 *    ni participer à une élection. Persisté via [CooldownStore].
 *
 * 2. Numéros de mandat (terms), à la manière de Raft, propres au cluster courant.
 *    Ils apportent la sûreté que le timeout seul ne peut pas garantir en asynchrone :
 *    - un candidat propose `plus haut term connu + 1` ;
 *    - un COORDINATOR dont le term est inférieur au plus haut term connu est périmé et
 *      rejeté (fencing) : les membres ne suivent jamais un Super-Pair remplacé ;
 *    - deux Super-Pairs concurrents se départagent par (term, score, nodeId).
 *    Deux Super-Pairs peuvent donc coexister brièvement avant stabilisation (GST),
 *    mais au plus un seul est suivi pour un term donné, et le plus récent l'emporte.
 *
 * [clockMs] est injectable pour les tests unitaires.
 */
@Singleton
class ElectionStateManager @Inject constructor(
    private val store: CooldownStore,
    private val termStore: TermStore
) {
    /** Constructeur sans dépendance pour les tests unitaires (in-memory pur). */
    constructor() : this(InMemoryCooldownStore(), InMemoryTermStore())

    /** Constructeur de compatibilité pour les tests qui ne fournissent que le cooldown. */
    constructor(store: CooldownStore) : this(store, InMemoryTermStore())

    /**
     * Source de temps utilisée pour le cooldown.
     * Remplaçable dans les tests : `electionStateManager.clockMs = { fixedTimeMs }`
     */
    @JvmField
    var clockMs: () -> Long = System::currentTimeMillis

    private var cooldownUntilMs: Long = store.read()
    private var terms: TermState = termStore.read()

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

    // ── Terms ────────────────────────────────────────────────────────────────

    /** Plus haut term de mandat connu pour [clusterId] (0 si inconnu ou autre cluster). */
    @Synchronized
    fun highestTerm(clusterId: String): Long =
        if (clusterId.isNotBlank() && clusterId == terms.clusterId) terms.highestTerm else 0L

    /** Term à proposer pour une nouvelle candidature dans [clusterId]. */
    @Synchronized
    fun nextCandidateTerm(clusterId: String): Long = highestTerm(clusterId) + 1

    /**
     * Vrai si un mandat de term [term] dans [clusterId] a été remplacé par un plus récent.
     * term = 0 (ancienne version, sans term) n'est jamais considéré périmé : compatibilité.
     */
    @Synchronized
    fun isStale(clusterId: String, term: Long): Boolean =
        term > 0L && term < highestTerm(clusterId)

    /** Enregistre un term de mandat observé pour [clusterId] (ignore un term plus ancien). */
    @Synchronized
    fun observeTerm(clusterId: String, term: Long) {
        if (clusterId.isBlank() || term <= 0L) return
        terms = when {
            clusterId != terms.clusterId -> terms.copy(clusterId = clusterId, highestTerm = term, ownTerm = 0L)
            term > terms.highestTerm -> terms.copy(highestTerm = term)
            else -> return
        }
        termStore.write(terms)
    }

    /** Le nœud local commence un mandat de Super-Pair de term [term] dans [clusterId]. */
    @Synchronized
    fun beginMandate(clusterId: String, term: Long) {
        if (clusterId.isBlank() || term <= 0L) return
        val highest = if (clusterId == terms.clusterId) maxOf(terms.highestTerm, term) else term
        terms = terms.copy(clusterId = clusterId, highestTerm = highest, ownTerm = term)
        termStore.write(terms)
    }

    /** Term du mandat local en cours pour [clusterId], 0 si le nœud n'y est pas Super-Pair. */
    @Synchronized
    fun ownTerm(clusterId: String): Long =
        if (clusterId.isNotBlank() && clusterId == terms.clusterId) terms.ownTerm else 0L

    // ── Rotation du mandat (voir MandateRotationPolicy) ─────────────────────

    /** Début du retrait après rotation : pas d'ALIVE, candidature retardée. */
    @Synchronized
    fun startRotationYield(durationMs: Long) {
        terms = terms.copy(rotationYieldUntilMs = clockMs() + durationMs)
        termStore.write(terms)
    }

    @Synchronized
    fun isInRotationYield(): Boolean = clockMs() < terms.rotationYieldUntilMs

    /** Fin du mandat local (abdication ou retrait au profit d'un autre Super-Pair). */
    @Synchronized
    fun endMandate() {
        if (terms.ownTerm == 0L) return
        terms = terms.copy(ownTerm = 0L)
        termStore.write(terms)
    }
}
