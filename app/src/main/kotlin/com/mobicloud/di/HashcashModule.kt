package com.mobicloud.di

import com.mobicloud.data.local.metadata.LocalHashcashTokenRepositoryImpl
import com.mobicloud.domain.repository.HashcashTokenRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HashcashModule {

    @Binds
    @Singleton
    abstract fun bindHashcashTokenRepository(
        impl: LocalHashcashTokenRepositoryImpl
    ): HashcashTokenRepository
}
