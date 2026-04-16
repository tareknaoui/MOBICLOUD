package com.mobicloud.data.repository

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReliabilityScoreProviderImplTest {

    private lateinit var context: Context
    private lateinit var batteryIntent: Intent
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCapabilities: NetworkCapabilities

    @Before
    fun setUp() {
        context = mockk()
        batteryIntent = mockk()
        connectivityManager = mockk()
        networkCapabilities = mockk()

        mockkStatic(SystemClock::class)

        // Défauts : WiFi validé disponible (NET_CAPABILITY_VALIDATED = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns mockk()
        every { connectivityManager.getNetworkCapabilities(any()) } returns networkCapabilities
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `score avec batterie 0 pourcent est dans l intervalle valide et inferieur a 0_6`() = runTest {
        every { context.registerReceiver(null, any()) } returns batteryIntent
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 0
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        // Uptime = 1h → score uptime ≈ 0.042
        every { SystemClock.elapsedRealtime() } returns 3_600_000L

        val provider = ReliabilityScoreProviderImpl(context)
        val score = provider.getScore()

        // battery 0.0*0.4=0.0, uptime ~0.042*0.4≈0.017, wifi 1.0*0.2=0.2 → ~0.217
        assertTrue("Score doit être dans [0.0, 1.0]", score in 0.0f..1.0f)
        assertTrue("Score avec batterie 0% doit être < 0.6 (pas de contribution batterie)", score < 0.6f)
    }

    @Test
    fun `score maximal avec batterie 100 pourcent uptime 24h et wifi`() = runTest {
        every { context.registerReceiver(null, any()) } returns batteryIntent
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 100
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        // Uptime = 24h → score uptime = 1.0
        every { SystemClock.elapsedRealtime() } returns 86_400_000L

        val provider = ReliabilityScoreProviderImpl(context)
        val score = provider.getScore()

        // battery 1.0*0.4=0.4, uptime 1.0*0.4=0.4, wifi 1.0*0.2=0.2 → 1.0
        assertEquals(1.0, score.toDouble(), 0.001)
    }

    @Test
    fun `StaticMockTrustScore retourne le score fixe configure`() = runTest {
        val mock = StaticMockTrustScore(0.85f)
        assertEquals(0.85, mock.getScore().toDouble(), 0.001)
    }

    @Test
    fun `score toujours dans l intervalle valide avec reseau 4G`() = runTest {
        every { context.registerReceiver(null, any()) } returns batteryIntent
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 50
        every { batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        // Uptime = 12h → score uptime = 0.5
        every { SystemClock.elapsedRealtime() } returns 43_200_000L
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true

        val provider = ReliabilityScoreProviderImpl(context)
        val score = provider.getScore()

        // battery 0.5*0.4=0.2, uptime 0.5*0.4=0.2, 4G 0.7*0.2=0.14 → 0.54
        assertTrue("Score doit être dans [0.0, 1.0]", score in 0.0f..1.0f)
        assertEquals(0.54, score.toDouble(), 0.001)
    }
}
