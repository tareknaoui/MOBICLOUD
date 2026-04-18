package com.mobicloud.di

import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.repository.PeerRepositoryImpl
import com.mobicloud.domain.repository.PeerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object P2PModule {

    @Provides
    @Singleton
    fun providePeerRepository(
        peerDao: PeerDao,
        @ApplicationScope scope: CoroutineScope
    ): PeerRepository = PeerRepositoryImpl(peerDao, scope)
}
