package com.mobicloud.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FormatModule {

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideProtobuf(): ProtoBuf {
        return ProtoBuf {
            // Protobuf in kotlinx.serialization natively ignores unknown fields
            // by default when decoding from a ByteArray, so no extra flags like
            // ignoreUnknownKeys are required here.
        }
    }
}
