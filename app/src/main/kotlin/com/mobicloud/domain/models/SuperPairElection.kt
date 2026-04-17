package com.mobicloud.domain.models

/**
 * Contient le résultat d'une élection de candidat au rôle de Super-Pair.
 *
 * @property electedNode L'identité du nœud qui a remporté l'élection.
 */
data class SuperPairElection(
    val electedNode: NodeIdentity,
    val electedAt: Long = System.currentTimeMillis()
)
