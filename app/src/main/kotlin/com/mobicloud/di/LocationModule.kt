package com.mobicloud.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fournit FusedLocationProviderClient pour toutes les variantes de build.
 * Le binding LocationRepository → impl est fourni par variant :
 *   - debug   : MockLocationModule  (position simulable)
 *   - release : ReleaseLocationModule (LocationRepositoryImpl réel)
 */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext ctx: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
}
