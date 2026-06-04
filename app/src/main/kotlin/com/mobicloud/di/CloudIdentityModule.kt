package com.mobicloud.di

import com.mobicloud.data.repository_impl.CloudIdentityRepositoryImpl
import com.mobicloud.domain.repository.CloudIdentityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudIdentityModule {
    @Binds
    @Singleton
    abstract fun bindCloudIdentityRepository(
        impl: CloudIdentityRepositoryImpl
    ): CloudIdentityRepository
}
