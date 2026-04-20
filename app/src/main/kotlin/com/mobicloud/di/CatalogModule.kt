package com.mobicloud.di

import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.data.repository_impl.CatalogRepositoryImpl
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.CalculateDhtRangeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {

    @Provides
    @Singleton
    fun provideCatalogDao(database: CatalogDatabase): CatalogDao =
        database.catalogDao()

    @Provides
    @Singleton
    fun provideCalculateDhtRangeUseCase(): CalculateDhtRangeUseCase =
        CalculateDhtRangeUseCase()

    @Provides
    @Singleton
    fun provideCatalogRepository(
        catalogDao: CatalogDao,
        calculateDhtRangeUseCase: CalculateDhtRangeUseCase
    ): CatalogRepository =
        CatalogRepositoryImpl(catalogDao, calculateDhtRangeUseCase)
}
