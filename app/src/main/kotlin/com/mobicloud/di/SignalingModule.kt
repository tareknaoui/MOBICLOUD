package com.mobicloud.di

import com.mobicloud.data.repository.SignalingRepositoryImpl
import com.mobicloud.domain.repository.SignalingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SignalingModule {
    @Binds
    @Singleton
    abstract fun bindSignalingRepository(
        impl: SignalingRepositoryImpl
    ): SignalingRepository
}
