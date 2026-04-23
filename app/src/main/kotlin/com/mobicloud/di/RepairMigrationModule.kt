package com.mobicloud.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// SendDepartureNoticeUseCase et NetworkChangeObserver sont @Singleton @Inject constructor
// — Hilt les résout automatiquement sans @Provides.
@Module
@InstallIn(SingletonComponent::class)
object RepairMigrationModule
