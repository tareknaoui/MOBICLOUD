package com.mobicloud.domain.models.gossip

import kotlinx.serialization.Serializable

/**
 * Requête d'échange différentiel.
 *
 * - v0 (legacy) : [missingBlockIds] = identifiants demandés au répondeur.
 *   Défaut historique : le demandeur y plaçait les entrées qui manquaient au *répondeur*,
 *   qui ne pouvait donc rien renvoyer. Conservé uniquement pour les anciennes versions.
 * - v1 (push-pull) : en un seul aller-retour, le demandeur
 *   1. pousse [pushedEntries] = ses entrées absentes du résumé du répondeur ;
 *   2. joint son propre résumé, [requesterDigest] (Bloom salé) ou, lors d'un round
 *      d'anti-entropy, [requesterKnownKeys] (liste exacte des clés versionnées) ;
 *   le répondeur fusionne les entrées poussées et renvoie celles qui manquent au demandeur.
 *
 * Champs v1 en fin de classe avec valeur par défaut (compatibilité wire, voir BloomFilterGossip).
 */
@Serializable
data class DeltaSyncRequest(
    val requesterNodeId: String,
    val missingBlockIds: List<String>,
    val timestamp: Long,
    // ── v1 ──
    val pushedEntries: List<DhtEntryDto> = emptyList(),
    val requesterDigest: BloomDigest? = null,
    val requesterKnownKeys: List<String> = emptyList(),
    val antiEntropy: Boolean = false
) {
    val isV1: Boolean
        get() = requesterDigest != null || antiEntropy
}
