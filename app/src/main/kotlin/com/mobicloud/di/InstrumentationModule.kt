package com.mobicloud.di

import com.mobicloud.data.instrumentation.AndroidExperimentEnvironment
import com.mobicloud.data.instrumentation.BatteryEnergyMeter
import com.mobicloud.data.instrumentation.CsvMeasurementSink
import com.mobicloud.domain.usecase.experiments.EnergyMeter
import com.mobicloud.domain.usecase.experiments.ExperimentEnvironment
import com.mobicloud.domain.usecase.experiments.MeasurementSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Instrumentation des mesures physiques (R2-8, R2-11). */
@Module
@InstallIn(SingletonComponent::class)
abstract class InstrumentationModule {
    @Binds abstract fun bindEnergyMeter(impl: BatteryEnergyMeter): EnergyMeter
    @Binds abstract fun bindMeasurementSink(impl: CsvMeasurementSink): MeasurementSink
    @Binds abstract fun bindExperimentEnvironment(impl: AndroidExperimentEnvironment): ExperimentEnvironment
}
