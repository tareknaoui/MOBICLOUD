package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.ClusterInvite
import com.mobicloud.domain.models.m11_join.JoinMetrics
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SignalingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Rejoint le cluster d'un ami via un lien/QR d'invitation.
 *
 * Réutilise volontairement le mécanisme "sticky cluster" déjà présent dans
 * [SendJoinRequestUseCase] plutôt que de réinventer une sélection/retry/signature dédiée :
 * marquer [ClusterInvite.clusterId] comme dernier cluster connu le fait remonter en tête de
 * liste des candidats. Si ce cluster n'apparaît plus dans l'annuaire du relais (rare — la
 * fenêtre entre génération et usage du lien est courte), [SendJoinRequestUseCase] retombe sur
 * sa logique générique existante (candidat le moins chargé) plutôt que d'échouer sec —
 * [InviteJoinResult.joinedIntendedCluster] permet à l'UI de distinguer les deux cas.
 */
class JoinClusterViaInviteUseCase @Inject constructor(
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val signalingRepository: SignalingRepository,
    private val sendJoinRequestUseCase: SendJoinRequestUseCase
) {
    data class InviteJoinResult(
        val metrics: JoinMetrics,
        val joinedIntendedCluster: Boolean
    )

    suspend operator fun invoke(invite: ClusterInvite): Flow<Result<InviteJoinResult>> {
        nodeSettingsRepository.updateClusterId(invite.clusterId)

        val hints = signalingRepository.fetchActiveSuperPeerHints().getOrElse { err ->
            return flow { emit(Result.failure(err)) }
        }

        return flow {
            sendJoinRequestUseCase(hints).collect { result ->
                emit(
                    result.map { metrics ->
                        val actualClusterId = nodeSettingsRepository.getClusterIdOnce()
                        InviteJoinResult(
                            metrics = metrics,
                            joinedIntendedCluster = actualClusterId == invite.clusterId
                        )
                    }
                )
            }
        }
    }
}
