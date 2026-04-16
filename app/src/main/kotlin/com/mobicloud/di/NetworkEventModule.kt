package com.mobicloud.di

import com.mobicloud.data.repository.NetworkEventRepositoryImpl
import com.mobicloud.domain.repository.NetworkEventRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkEventModule {
    @Binds
    @Singleton
    abstract fun bindNetworkEventRepository(impl: NetworkEventRepositoryImpl): NetworkEventRepository
}
