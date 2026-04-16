package com.mobicloud.data.repository

import androidx.annotation.VisibleForTesting
import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider

/**
 * Mock injectable via Hilt pour les tests unitaires.
 * Retourne un score fixe configurable via le constructeur.
 * NE PAS utiliser en production — réservé aux tests uniquement.
 */
@VisibleForTesting
class StaticMockTrustScore(private val fixedScore: Float = 0.85f) : ITrustScoreProvider {
    override suspend fun getScore(): Float = fixedScore
}
