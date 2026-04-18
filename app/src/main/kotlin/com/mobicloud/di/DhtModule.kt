package com.mobicloud.di

import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.dao.DhtDao
import com.mobicloud.data.repository.DhtRepositoryImpl
import com.mobicloud.domain.repository.DhtRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DhtModule {

    @Provides
    @Singleton
    fun provideDhtDao(database: CatalogDatabase): DhtDao {
        return database.dhtDao()
    }

    @Provides
    @Singleton
    fun provideDhtRepository(dhtRepositoryImpl: DhtRepositoryImpl): DhtRepository {
        return dhtRepositoryImpl
    }
}
