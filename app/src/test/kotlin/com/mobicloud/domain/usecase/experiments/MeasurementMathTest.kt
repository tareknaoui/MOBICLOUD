package com.mobicloud.domain.usecase.experiments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MeasurementMathTest {

    /** Courant constant pendant [seconds] secondes, un échantillon par seconde. */
    private fun constant(currentRaw: Long, mv: Int = 4000, seconds: Int = 10) =
        (0..seconds).map { EnergySample(timeMs = it * 1000L, currentRaw = currentRaw, voltageMilliVolts = mv) }

    @Test
    fun `constant draw integrates to power times duration`() {
        // 500 mA × 4 V = 2 W pendant 10 s = 20 J
        assertEquals(20_000.0, MeasurementMath.integrateEnergyMilliJoules(constant(500_000))!!, 1e-6)
    }

    @Test
    fun `devices reporting milliamps or negative discharge give the same energy`() {
        val reference = MeasurementMath.integrateEnergyMilliJoules(constant(500_000))!!
        assertEquals("mA", MeasurementMath.detectCurrentUnit(constant(500)))
        assertEquals(reference, MeasurementMath.integrateEnergyMilliJoules(constant(500))!!, 1e-6)
        assertEquals(reference, MeasurementMath.integrateEnergyMilliJoules(constant(-500_000))!!, 1e-6)
    }

    @Test
    fun `trapezoidal rule on a linear ramp is exact`() {
        // 0 → 1000 mA en 10 s à 4 V : P moyenne 2 W → 20 J
        val ramp = (0..10).map { EnergySample(it * 1000L, it * 100_000L, 4000) }
        assertEquals(20_000.0, MeasurementMath.integrateEnergyMilliJoules(ramp, unit = "uA")!!, 1e-6)
    }

    @Test
    fun `irregular sampling and duplicate timestamps are handled`() {
        val samples = listOf(
            EnergySample(0, 500_000, 4000), EnergySample(0, 500_000, 4000),
            EnergySample(300, 500_000, 4000), EnergySample(2_000, 500_000, 4000)
        )
        assertEquals(4_000.0, MeasurementMath.integrateEnergyMilliJoules(samples)!!, 1e-6)
    }

    @Test
    fun `fewer than two samples give no energy`() {
        assertNull(MeasurementMath.integrateEnergyMilliJoules(emptyList()))
        assertNull(MeasurementMath.integrateEnergyMilliJoules(constant(500_000, seconds = 0)))
        assertNull(MeasurementMath.averageCurrentMilliAmps(emptyList()))
    }

    @Test
    fun `reading combines energy, average current and charge counter`() {
        val r = MeasurementMath.reading(constant(250_000), chargeCounterDeltaMicroAh = 700, wasCharging = false)
        assertEquals(10_000.0, r.energyMilliJoules!!, 1e-6)
        assertEquals(250.0, r.avgCurrentMilliAmps!!, 1e-9)
        assertEquals(11, r.sampleCount)
        assertEquals("uA", r.currentUnit)
        assertEquals(700L, r.chargeCounterDeltaMicroAh)
    }

    @Test
    fun `throughput in megabits per second`() {
        assertEquals(10.0, MeasurementMath.megabitsPerSecond(10_000_000, 8_000)!!, 1e-9)
        assertNull(MeasurementMath.megabitsPerSecond(10, 0))
        assertNull(MeasurementMath.megabitsPerSecond(10, -1))
    }

    private fun record(label: String = "wifi_10mb", error: String = "") = MeasurementRecord(
        runId = "r1", label = label, repetition = 3, operation = ExperimentOperation.UPLOAD, timestampMs = 42,
        deviceModel = "Google Pixel 7", androidApi = 34, networkType = "WIFI", fileBytes = 10_000_000,
        k = 2, n = 1, wireBytes = 15_000_000, hostCount = 3, selectMs = 10, encodeMs = 300, encryptMs = 200,
        transferMs = 8_000, totalMs = 8_510,
        energy = EnergyReading(12_345.6, 410.25, 900, 41, "uA", false),
        batteryStartPct = 80, batteryEndPct = 79, success = error.isEmpty(), error = error
    )

    @Test
    fun `csv rows match the header and ignore the phone language`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE) // virgule décimale en français
            val row = MeasurementCsv.toRow(record())
            val cells = row.split(",")
            assertEquals(MeasurementCsv.HEADER.split(",").size, cells.size)
            val col = MeasurementCsv.HEADER.split(",").withIndex().associate { it.value to it.index }
            assertEquals("10.000", cells[col.getValue("goodput_mbps")])
            assertEquals("15.000", cells[col.getValue("wire_mbps")])
            assertEquals("12345.6", cells[col.getValue("energy_mj")])
            assertEquals("1234.6", cells[col.getValue("energy_mj_per_mb")])
            assertEquals("true", cells[col.getValue("success")])
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `free text is escaped so it cannot break columns`() {
        assertEquals("plain", MeasurementCsv.escape("plain"))
        assertEquals("\"a,b\"", MeasurementCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", MeasurementCsv.escape("say \"hi\""))
        val row = MeasurementCsv.toRow(record(error = "timeout, peer \"x\"\nretry"))
        assertTrue(row.endsWith("\"timeout, peer \"\"x\"\"\nretry\""))
    }

    @Test
    fun `missing measurements are left empty rather than zero`() {
        val row = MeasurementCsv.toRow(record().copy(energy = null, transferMs = -1))
        val cells = row.split(",")
        val col = MeasurementCsv.HEADER.split(",").withIndex().associate { it.value to it.index }
        assertEquals("", cells[col.getValue("goodput_mbps")])
        assertEquals("", cells[col.getValue("energy_mj")])
    }
}
