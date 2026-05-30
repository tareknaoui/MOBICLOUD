package com.mobicloud.di

import android.content.Context
import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.repository.LocalDiscoveryRepositoryImpl
import com.mobicloud.data.repository.PeerRepositoryImpl
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.LocalDiscoveryRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

import com.mobicloud.core.preferences.data.UserPreferencesDataSource

@Module
@InstallIn(SingletonComponent::class)
object P2PModule {

    @Provides
    @Singleton
    fun providePeerRepository(
        peerDao: PeerDao,
        @ApplicationScope scope: CoroutineScope
    ): PeerRepository = PeerRepositoryImpl(peerDao, scope)

    @Provides
    @Singleton
    fun provideLocalDiscoveryRepository(
        identityRepository: IdentityRepository,
        peerRepository: PeerRepository,
        networkEventRepository: NetworkEventRepository,
        userPreferencesDataSource: UserPreferencesDataSource,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): LocalDiscoveryRepository = LocalDiscoveryRepositoryImpl(
        identityRepository,
        peerRepository,
        networkEventRepository,
        userPreferencesDataSource,
        context,
        scope
    )
}
