package com.mobicloud.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.mobicloud.domain.models.GpsCoordinate
import com.mobicloud.domain.repository.NetworkEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LocationRepositoryImplTest {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var networkEventRepo: NetworkEventRepository
    private lateinit var context: Context
    private lateinit var repo: LocationRepositoryImpl

    private val callbackSlot = slot<LocationCallback>()

    @Before
    fun setUp() {
        fusedClient = mockk(relaxed = true)
        networkEventRepo = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Par défaut, permission accordée
        every {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        every {
            fusedClient.requestLocationUpdates(any<LocationRequest>(), capture(callbackSlot), any<Looper>())
        } returns mockk()

        repo = LocationRepositoryImpl(fusedClient, networkEventRepo, context)
    }

    @Test
    fun `permission refusee au boot - currentLocation reste null apres start`() {
        every {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        repo.start()

        assertNull(repo.currentLocation.value)
        verify(exactly = 0) { fusedClient.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>()) }
    }

    @Test
    fun `permission refusee au boot - start peut etre rappele apres correction F1`() {
        // Vérifie le fix F1 : started doit être remis à false sur refus permission
        every {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        repo.start()
        assertNull(repo.currentLocation.value)

        // Simule la permission accordée ensuite
        every {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        repo.start()

        // start() doit re-enregistrer les updates (pas être bloqué par started=true)
        verify(exactly = 1) { fusedClient.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>()) }
    }

    @Test
    fun `premier callback LocationResult met a jour currentLocation`() {
        repo.start()

        val fakeLocation = mockk<Location>(relaxed = true)
        every { fakeLocation.latitude } returns 36.7538
        every { fakeLocation.longitude } returns 3.0588
        every { fakeLocation.accuracy } returns 50f
        every { fakeLocation.time } returns 1000L

        val locationResult = mockk<LocationResult>()
        every { locationResult.lastLocation } returns fakeLocation

        callbackSlot.captured.onLocationResult(locationResult)

        val coord = repo.currentLocation.value
        assertEquals(36.7538, coord?.latitude ?: 0.0, 1e-6)
        assertEquals(3.0588, coord?.longitude ?: 0.0, 1e-6)
        assertEquals(50f, coord?.accuracyMeters ?: 0f, 0f)
    }

    @Test
    fun `callback avec accuracy superieure a 100m est accepte en V1`() {
        // Pas de filtre accuracy en V1 — tout fix est accepté quelle que soit sa précision
        repo.start()

        val fakeLocation = mockk<Location>(relaxed = true)
        every { fakeLocation.latitude } returns 36.0
        every { fakeLocation.longitude } returns 3.0
        every { fakeLocation.accuracy } returns 200f
        every { fakeLocation.time } returns 2000L

        val locationResult = mockk<LocationResult>()
        every { locationResult.lastLocation } returns fakeLocation

        callbackSlot.captured.onLocationResult(locationResult)

        // Toujours stocké — le filtre accuracy est documenté comme perspective V2
        val coord = repo.currentLocation.value
        assertEquals(200f, coord?.accuracyMeters ?: 0f, 0f)
    }

    @Test
    fun `stop invoque removeLocationUpdates et conserve le cache`() {
        repo.start()

        val fakeLocation = mockk<Location>(relaxed = true)
        every { fakeLocation.latitude } returns 36.7538
        every { fakeLocation.longitude } returns 3.0588
        every { fakeLocation.accuracy } returns 50f
        every { fakeLocation.time } returns 1000L
        val locationResult = mockk<LocationResult>()
        every { locationResult.lastLocation } returns fakeLocation
        callbackSlot.captured.onLocationResult(locationResult)

        val cachedCoord: GpsCoordinate? = repo.currentLocation.value

        repo.stop()

        verify { fusedClient.removeLocationUpdates(any<LocationCallback>()) }
        // Cache RAM conservé après stop()
        assertEquals(cachedCoord, repo.currentLocation.value)
    }
}
