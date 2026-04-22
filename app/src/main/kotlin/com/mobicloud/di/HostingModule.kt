package com.mobicloud.di

import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.dao.HostedBlockDao
import com.mobicloud.data.repository_impl.HostedBlockRepositoryImpl
import com.mobicloud.domain.repository.HostedBlockRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HostingModule {

    @Binds
    @Singleton
    abstract fun bindHostedBlockRepository(
        impl: HostedBlockRepositoryImpl
    ): HostedBlockRepository

    companion object {
        @Provides
        @Singleton
        fun provideHostedBlockDao(database: CatalogDatabase): HostedBlockDao =
            database.hostedBlockDao()
    }
}
