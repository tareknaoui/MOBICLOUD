package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.ErasureParameters
import javax.inject.Inject

/**
 * Régénération **sans clé** de fragments perdus d'un fichier stocké en chiffrer-puis-encoder.
 *
 * Les fragments sont des morceaux de chiffré : à partir de k fragments quelconques, le décodage
 * Reed-Solomon restitue le chiffré complet, et un nouvel encodage redonne exactement les mêmes
 * k + n fragments (encodage déterministe, même bourrage). Le nœud qui répare (un Super-Pair par
 * exemple) ne voit que du chiffré et ne détient jamais K_F ; le fragment régénéré a le même
 * SHA-256, donc le même blockId, que l'original.
 *
 * Inapplicable à l'ancien format encoder-puis-chiffrer, où chaque fragment est chiffré avec sa
 * propre clé dérivée de K_F.
 *
 * Fourni comme primitive : le plan de réparation réseau actuel recopie un bloc existant
 * (ExecuteReplicationPlanUseCase) et ne l'utilise pas encore.
 */
class RegenerateFragmentsUseCase @Inject constructor(
    private val decoder: DecodeErasureFragmentsUseCase,
    private val encoder: EncodeErasureFragmentsUseCase
) {
    /**
     * @param available au moins k fragments de chiffré ; leur `originalFileSize` est la taille du
     *   chiffré (transmise aux hôtes avec chaque bloc).
     * @param missingIndices indices des fragments à régénérer, dans `[0, k + n)`.
     */
    suspend operator fun invoke(
        available: List<ErasureFragment>,
        params: ErasureParameters,
        missingIndices: Set<Int>
    ): Result<List<ErasureFragment>> = runCatching {
        require(missingIndices.all { it in 0 until params.k + params.n }) {
            "indices hors de [0, ${params.k + params.n}) : $missingIndices"
        }
        val ciphertext = decoder(available.distinctBy { it.index }, params).getOrThrow()
        encoder.encodeBytes(ciphertext, params).filter { it.index in missingIndices }
    }
}
