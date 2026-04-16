package com.mobicloud.domain.usecase.m01_discovery

interface ITrustScoreProvider {
    /** Retourne le score de fiabilité normalisé entre 0.0 et 1.0. */
    suspend fun getScore(): Float
}
