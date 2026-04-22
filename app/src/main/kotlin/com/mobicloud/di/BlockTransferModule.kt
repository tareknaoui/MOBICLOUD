package com.mobicloud.di

import com.mobicloud.data.p2p.tcp.BlockDownloadClient
import com.mobicloud.data.p2p.tcp.BlockTransferClient
import com.mobicloud.domain.repository.BlockDownloader
import com.mobicloud.domain.repository.BlockSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BlockTransferModule {

    @Provides
    @Singleton
    fun provideBlockSender(client: BlockTransferClient): BlockSender = client

    // Story 6.2 — bind BlockDownloader sur l'implémentation TCP.
    @Provides
    @Singleton
    fun provideBlockDownloader(client: BlockDownloadClient): BlockDownloader = client
}
