package com.mobicloud.di

import com.mobicloud.data.repository.ReliabilityScoreProviderImpl
import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReliabilityModule {
    @Binds
    @Singleton
    abstract fun bindTrustScoreProvider(
        impl: ReliabilityScoreProviderImpl
    ): ITrustScoreProvider
}
