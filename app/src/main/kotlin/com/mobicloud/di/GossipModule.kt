package com.mobicloud.di

import com.mobicloud.data.p2p.relay.GossipRelayChannel
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipOutboundPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GossipModule {

    @Provides
    @Singleton
    fun provideGossipOutboundPort(channel: GossipRelayChannel): GossipOutboundPort = channel
}
