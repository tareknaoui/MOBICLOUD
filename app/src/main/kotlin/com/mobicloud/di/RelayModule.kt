package com.mobicloud.di

import com.mobicloud.data.repository.RelayRepositoryImpl
import com.mobicloud.domain.repository.RelayRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RelayModule {
    @Binds
    @Singleton
    abstract fun bindRelayRepository(impl: RelayRepositoryImpl): RelayRepository
}
