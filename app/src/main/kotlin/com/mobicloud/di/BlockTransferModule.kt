package com.mobicloud.di

import com.mobicloud.data.p2p.BlockSenderWithRelay
import com.mobicloud.data.p2p.tcp.BlockDownloadClient
import com.mobicloud.domain.models.TransferChannelState
import com.mobicloud.domain.repository.BlockDownloader
import com.mobicloud.domain.repository.BlockSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BlockTransferModule {

    @Provides
    @Singleton
    fun provideBlockSender(sender: BlockSenderWithRelay): BlockSender = sender

    @Provides
    @Singleton
    @Named("transfer_channel_state")
    fun provideTransferChannelState(sender: BlockSenderWithRelay): StateFlow<TransferChannelState> =
        sender.transferChannelState

    // Story 6.2 — bind BlockDownloader sur l'implémentation TCP.
    @Provides
    @Singleton
    fun provideBlockDownloader(client: BlockDownloadClient): BlockDownloader = client
}
