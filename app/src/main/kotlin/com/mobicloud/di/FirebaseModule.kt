package com.mobicloud.di

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.mobicloud.data.repository_impl.FirebaseBootstrapRepositoryImpl
import com.mobicloud.domain.repository.BootstrapRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseProvidesModule {
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return Firebase.database
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseBindsModule {
    @Binds
    @Singleton
    abstract fun bindBootstrapRepository(
        impl: FirebaseBootstrapRepositoryImpl
    ): BootstrapRepository
}
