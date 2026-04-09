package com.mobicloud.di

import com.mobicloud.data.p2p.UdpHeartbeatBroadcaster
import com.mobicloud.data.p2p.UdpHeartbeatReceiver
import com.mobicloud.data.repository_impl.PeerRegistryImpl
import com.mobicloud.domain.repository.PeerRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object P2PModule {



    @Provides
    @Singleton
    @Named("MulticastSocket")
    fun provideMulticastSocket(): DatagramSocket {
        return MulticastSocket(50000).apply {
            reuseAddress = true
            joinGroup(InetAddress.getByName("224.0.0.1"))
        }
    }

    @Provides
    @Singleton
    fun provideUdpHeartbeatBroadcaster(
        protoBuf: ProtoBuf,
        @Named("MulticastSocket") socket: DatagramSocket
    ): UdpHeartbeatBroadcaster {
        return UdpHeartbeatBroadcaster(
            protoBuf = protoBuf,
            socket = socket,
            multicastAddress = "224.0.0.1",
            port = 50000
        )
    }

    @Provides
    @Singleton
    fun provideUdpHeartbeatReceiver(
        protoBuf: ProtoBuf,
        @Named("MulticastSocket") socket: DatagramSocket
    ): UdpHeartbeatReceiver {
        return UdpHeartbeatReceiver(
            protoBuf = protoBuf,
            socket = socket
        )
    }

    @Provides
    @Singleton
    fun providePeerRegistry(): PeerRegistry {
        return PeerRegistryImpl()
    }
}
