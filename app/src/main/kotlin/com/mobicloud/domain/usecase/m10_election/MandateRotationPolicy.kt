package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.toHexString

/**
 * Mandat tournant borné du Super-Pair (réponse à R2-3).
 *
 * À l'expiration du mandat ([MANDATE_DURATION_MS]) :
 *  - s'il existe au moins un membre actif du cluster pouvant lui succéder, le Super-Pair
 *    abdique et entre en **retrait** pendant [ROTATION_YIELD_MS] ;
 *  - sinon il renouvelle son mandat. Abdiquer sans successeur laissait le nœud seul en
 *    état « Searching » pendant tout le cooldown (bug « Searching après 30 min » qui
 *    avait conduit à désactiver la rotation).
 *
 * Retrait (≠ cooldown strict post-abdication manuelle) : le nœud ne répond pas ALIVE aux
 * ELECTION et retarde sa propre candidature de [ROTATION_CANDIDACY_DELAY_MS]. Tant qu'un
 * autre candidat existe, il ne peut donc pas être réélu ; s'il est seul, il reprend le rôle
 * après ce délai (pas de perte de disponibilité). Un retrait de 2 mandats répartit le rôle
 * sur au moins trois nœuds quand le cluster en compte assez, au lieu d'une alternance entre
 * les deux meilleurs scores.
 */
object MandateRotationPolicy {

    const val MANDATE_DURATION_MS = 30 * 60 * 1000L

    const val ROTATION_YIELD_MS = 2 * MANDATE_DURATION_MS

    /**
     * Délai supplémentaire avant candidature pendant le retrait. Supérieur à la durée d'une
     * élection normale (fenêtre de surveillance 6 s + attente ALIVE 8 s) pour qu'un autre
     * candidat gagne d'abord s'il existe.
     */
    const val ROTATION_CANDIDACY_DELAY_MS = 20_000L

    /**
     * Vrai si au moins un membre (rôle MEMBER) du snapshot du cluster est actuellement actif.
     *
     * @param activePeerIds nodeIds (hex) des pairs actifs, comparés sans tenir compte de la casse.
     */
    fun hasEligibleSuccessor(members: List<MemberInfo>, activePeerIds: Set<String>): Boolean {
        val active = activePeerIds.mapTo(HashSet()) { it.lowercase() }
        return members.any { it.role == MemberRole.MEMBER && it.nodeId.toHexString().lowercase() in active }
    }
}
