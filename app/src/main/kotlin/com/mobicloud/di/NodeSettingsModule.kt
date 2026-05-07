package com.mobicloud.di

import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.dao.NodeSettingsDao
import com.mobicloud.data.repository.NodeSettingsRepositoryImpl
import com.mobicloud.data.repository.WifiNetworkRepositoryImpl
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NodeSettingsBindingModule {

    @Binds
    @Singleton
    abstract fun bindNodeSettingsRepository(
        impl: NodeSettingsRepositoryImpl
    ): NodeSettingsRepository

    @Binds
    @Singleton
    abstract fun bindWifiNetworkRepository(
        impl: WifiNetworkRepositoryImpl
    ): WifiNetworkRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NodeSettingsModule {

    @Provides
    @Singleton
    fun provideNodeSettingsDao(database: CatalogDatabase): NodeSettingsDao =
        database.nodeSettingsDao()
}
