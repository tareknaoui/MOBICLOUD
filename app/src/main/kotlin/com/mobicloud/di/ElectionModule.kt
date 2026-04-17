package com.mobicloud.di

import com.mobicloud.data.election.ReliabilityTrustScoreAdapter
import com.mobicloud.data.election.StubElectionNetworkClient
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ElectionBindingsModule {

    @Binds
    @Singleton
    abstract fun bindElectionNetworkClient(
        impl: StubElectionNetworkClient
    ): IElectionNetworkClient

    @Binds
    @Singleton
    abstract fun bindTrustScoreProvider(
        impl: ReliabilityTrustScoreAdapter
    ): ITrustScoreProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ElectionDispatcherModule {
    // Fournit un CoroutineDispatcher non qualifié pour RunBullyElectionUseCase.defaultDispatcher
    @Provides
    fun provideCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
