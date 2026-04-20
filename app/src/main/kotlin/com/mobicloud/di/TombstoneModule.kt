package com.mobicloud.di

import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.dao.TombstoneDao
import com.mobicloud.data.repository.TombstoneRepositoryImpl
import com.mobicloud.domain.repository.TombstoneRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TombstoneModule {

    @Binds
    @Singleton
    abstract fun bindTombstoneRepository(impl: TombstoneRepositoryImpl): TombstoneRepository

    companion object {
        @Provides
        @Singleton
        fun provideTombstoneDao(database: CatalogDatabase): TombstoneDao =
            database.tombstoneDao()
    }
}
