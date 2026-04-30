package com.mobicloud.di

import android.content.Context
import androidx.room.Room
import com.mobicloud.core.security.KeystoreManager
import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.dao.IdentityDao
import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.repository_impl.IdentityRepositoryImpl
import com.mobicloud.domain.repository.IdentityRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IdentityBindingModule {

    @Binds
    @Singleton
    abstract fun bindIdentityRepository(
        impl: IdentityRepositoryImpl
    ): IdentityRepository
}

@Module
@InstallIn(SingletonComponent::class)
object IdentityModule {

    @Provides
    @Singleton
    fun provideCatalogDatabase(@ApplicationContext context: Context): CatalogDatabase {
        return Room.databaseBuilder(
            context,
            CatalogDatabase::class.java,
            "mobicloud_database.db"
        ).addMigrations(
            CatalogDatabase.MIGRATION_1_2,
            CatalogDatabase.MIGRATION_2_3,
            CatalogDatabase.MIGRATION_3_4,
            CatalogDatabase.MIGRATION_4_5,
            CatalogDatabase.MIGRATION_5_6,
            CatalogDatabase.MIGRATION_6_7,
            CatalogDatabase.MIGRATION_7_8,
            CatalogDatabase.MIGRATION_8_9,
            CatalogDatabase.MIGRATION_9_10
        ).build()
    }

    @Provides
    fun provideIdentityDao(database: CatalogDatabase): IdentityDao {
        return database.identityDao()
    }

    @Provides
    @Singleton
    fun providePeerDao(database: CatalogDatabase): PeerDao {
        return database.peerDao()
    }

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager {
        return KeystoreManager()
    }
}
