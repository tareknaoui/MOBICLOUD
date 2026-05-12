package com.mobicloud.di

import com.mobicloud.data.repository.MockLocationRepositoryImpl
import com.mobicloud.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Remplace [LocationModule] en variante debug.
 * En release, le binding production (LocationRepositoryImpl réel) s'applique.
 * Utilisé pour les simulations multi-device (Test 5 — plan-tests-soutenance).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MockLocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: MockLocationRepositoryImpl): LocationRepository
}
