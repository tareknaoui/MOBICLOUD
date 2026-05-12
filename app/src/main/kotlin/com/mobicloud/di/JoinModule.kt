package com.mobicloud.di

import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.dao.MemberSnapshotDao
import com.mobicloud.data.p2p.join.JoinNetworkClientImpl
import com.mobicloud.data.p2p.m11_join.MemberHeartbeatSenderImpl
import com.mobicloud.data.p2p.m11_join.RoomMemberRegistry
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.usecase.m11_join.MemberRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class JoinModule {

    @Binds
    @Singleton
    abstract fun bindJoinNetworkClient(
        impl: JoinNetworkClientImpl
    ): IJoinNetworkClient

    @Binds
    @Singleton
    abstract fun bindMemberRegistry(
        impl: RoomMemberRegistry
    ): MemberRegistry

    @Binds
    @Singleton
    abstract fun bindMemberHeartbeatSender(
        impl: MemberHeartbeatSenderImpl
    ): IMemberHeartbeatSender
}

@Module
@InstallIn(SingletonComponent::class)
object JoinDaoModule {

    @Provides
    @Singleton
    fun provideMemberDao(database: CatalogDatabase): MemberDao = database.memberDao()

    @Provides
    @Singleton
    fun provideMemberSnapshotDao(database: CatalogDatabase): MemberSnapshotDao = database.memberSnapshotDao()

    // Clock injectable pour MemberHeartbeatUseCase / MonitorMemberLivenessUseCase.
    // Sans ce @Provides, Hilt ne sait pas fournir `Function0<Long>` → l'app ne compile pas.
    // Les tests JVM purs surchargent via le paramètre constructeur direct (signature préservée).
    @Provides
    @Singleton
    fun provideSystemClock(): () -> Long = { System.currentTimeMillis() }
}
