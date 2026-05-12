package com.mobicloud.di

import com.mobicloud.data.p2p.join.JoinNetworkClientImpl
import com.mobicloud.domain.repository.IJoinNetworkClient
import com.mobicloud.domain.usecase.m11_join.MemberRegistry
import com.mobicloud.domain.usecase.m11_join.RamMemberRegistry
import dagger.Binds
import dagger.Module
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
        impl: RamMemberRegistry
    ): MemberRegistry
}
