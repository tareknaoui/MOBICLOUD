package com.mobicloud.di

import com.mobicloud.data.network.service.NetworkServiceControllerImpl
import com.mobicloud.domain.repository.NetworkServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindNetworkServiceController(
        networkServiceControllerImpl: NetworkServiceControllerImpl
    ): NetworkServiceController
}
