package com.mobicloud.di

import com.mobicloud.data.local.security.KeystoreSecurityRepositoryImpl
import com.mobicloud.domain.repository.SecurityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecurityRepository(
        impl: KeystoreSecurityRepositoryImpl
    ): SecurityRepository
}
