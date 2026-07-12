package com.mobicloud.domain.models.m11_join

import android.net.Uri

private const val INVITE_SCHEME = "mobicloud"
private const val INVITE_HOST = "join"

/**
 * Invitation à rejoindre un cluster existant, encodée dans un lien partageable / QR code.
 *
 * [hintedSpNodeId] n'est qu'une optimisation d'affichage/debug — la jointure ne cible jamais
 * ce nodeId directement, car le Super-Pair peut avoir changé (failover Bully) entre la
 * génération du lien et son usage. Seul [clusterId] compte : [JoinClusterViaInviteUseCase]
 * le marque comme "dernier cluster connu", ce qui le fait remonter en tête de liste des
 * candidats via le mécanisme sticky déjà présent dans SendJoinRequestUseCase.
 */
data class ClusterInvite(
    val clusterId: String,
    val hintedSpNodeId: String = ""
) {
    fun toUri(): String {
        val spParam = if (hintedSpNodeId.isNotEmpty()) "&sp=$hintedSpNodeId" else ""
        return "$INVITE_SCHEME://$INVITE_HOST?cid=$clusterId$spParam"
    }

    companion object {
        fun fromUri(uriString: String): ClusterInvite? {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
            if (uri.scheme != INVITE_SCHEME || uri.host != INVITE_HOST) return null
            val clusterId = uri.getQueryParameter("cid")?.takeIf { it.isNotBlank() } ?: return null
            val sp = uri.getQueryParameter("sp").orEmpty()
            return ClusterInvite(clusterId, sp)
        }
    }
}
