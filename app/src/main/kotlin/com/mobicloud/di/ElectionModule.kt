package com.mobicloud.di

import com.mobicloud.data.election.RelayElectionNetworkClient
import com.mobicloud.data.election.ReliabilityTrustScoreAdapter
import com.mobicloud.data.local.election.SharedPrefsCooldownStore
import com.mobicloud.data.local.election.SharedPrefsTermStore
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.usecase.m10_election.CooldownStore
import com.mobicloud.domain.usecase.m10_election.TermStore
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
        impl: RelayElectionNetworkClient
    ): IElectionNetworkClient

    @Binds
    @Singleton
    abstract fun bindTrustScoreProvider(
        impl: ReliabilityTrustScoreAdapter
    ): ITrustScoreProvider

    @Binds
    @Singleton
    abstract fun bindCooldownStore(
        impl: SharedPrefsCooldownStore
    ): CooldownStore

    @Binds
    @Singleton
    abstract fun bindTermStore(
        impl: SharedPrefsTermStore
    ): TermStore
}

@Module
@InstallIn(SingletonComponent::class)
object ElectionDispatcherModule {
    // Fournit un CoroutineDispatcher non qualifié pour RunBullyElectionUseCase.defaultDispatcher
    @Provides
    fun provideCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
