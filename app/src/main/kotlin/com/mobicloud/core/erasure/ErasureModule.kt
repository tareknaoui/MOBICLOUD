package com.mobicloud.core.erasure

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ErasureModule {

    @Provides
    @Singleton
    fun provideErasureCodec(): ErasureCodec = ErasureCodingJni
}
