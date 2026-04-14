package com.mobicloud.di

import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.p2p.UdpHeartbeatBroadcaster
import com.mobicloud.data.p2p.UdpHeartbeatReceiver
import com.mobicloud.data.repository.PeerRepositoryImpl
import com.mobicloud.domain.repository.PeerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
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
        return MulticastSocket(7777).apply {
            reuseAddress = true
            joinGroup(InetAddress.getByName("239.255.255.250"))
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
            multicastAddress = "239.255.255.250",
            port = 7777
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
    fun providePeerRepository(
        peerDao: PeerDao,
        scope: CoroutineScope
    ): PeerRepository = PeerRepositoryImpl(peerDao, scope)
}
