package com.mobicloud.di

import com.mobicloud.data.p2p.tcp.GossipChannel
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipOutboundPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// F4: GossipChannel (data) implémente GossipOutboundPort (domain) — binding explicite pour Hilt
// F8: BloomFilter singleton supprimé (dead code — GossipSyncUseCase crée une instance par cycle)
@Module
@InstallIn(SingletonComponent::class)
object GossipModule {

    @Provides
    @Singleton
    fun provideGossipOutboundPort(channel: GossipChannel): GossipOutboundPort = channel
}
