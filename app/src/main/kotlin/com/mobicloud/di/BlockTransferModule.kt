package com.mobicloud.di

import com.mobicloud.data.p2p.BlockDownloaderWithRelay
import com.mobicloud.data.p2p.BlockSenderWithRelay
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

    // Story 9.4 — wrapper qui décide direct (intra-cluster) vs relay-pull (inter-cluster) selon
    // la présence du nodeId dans peerRepository.peers actif. BlockDownloadClient reste le
    // backing direct, injecté transitivement par Hilt dans BlockDownloaderWithRelay.
    @Provides
    @Singleton
    fun provideBlockDownloader(wrapper: BlockDownloaderWithRelay): BlockDownloader = wrapper
}
