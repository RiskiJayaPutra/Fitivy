package com.fitivy.app.sensor

import android.location.Location
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk

/**
 * Unit Tests untuk GpsPreFilter.
 * Menguji Pipeline 4 Layer + Layer 0 Mock + Edge Case kampus urban (Underpass/Tunnel).
 */
class GpsPreFilterTest {

    private lateinit var filter: GpsPreFilter
    private var baseTimeNs = 1_000_000_000_000L

    @BeforeEach
    fun setup() {
        filter = GpsPreFilter()
    }

    private fun createMockLocation(
        lat: Double = -6.2,
        lng: Double = 106.8,
        acc: Float = 10.0f,
        timeDeltaSec: Double = 0.0,
        provider: String = "gps",
        bearing: Float? = null,
        isMockLocation: Boolean = false
    ): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lng
        
        every { loc.hasAccuracy() } returns true
        every { loc.accuracy } returns acc
        
        baseTimeNs += (timeDeltaSec * 1_000_000_000L).toLong()
        every { loc.elapsedRealtimeNanos } returns baseTimeNs
        
        every { loc.provider } returns provider

        if (bearing != null) {
            every { loc.hasBearing() } returns true
            every { loc.bearing } returns bearing
        } else {
            every { loc.hasBearing() } returns false
        }

        // Mock untuk Layer 0 (Mock Provider)
        every { loc.isFromMockProvider } returns isMockLocation
        // Jika environment test API 31+, ini akan dipanggil:
        try {
            val method = Location::class.java.getMethod("isMock")
            every { method.invoke(loc) } returns isMockLocation
        } catch (e: Exception) {
            // Ignore jika test dijalankan di API < 31 SDK stub
        }

        return loc
    }

    @Test
    fun `TEST FIX 3 - Layer 0 menolak lokasi palsu dari mock provider (Fake GPS)`() {
        // Lokasi ini punya parameter yang valid (kecepatan & akurasi bagus), TAPI dihasilkan oleh Fake GPS
        val curr = createMockLocation(isMockLocation = true)
        
        val result = filter.filter(curr, null, isOutdoorSession = true)

        assertTrue(result is FilterResult.Rejected)
        assertEquals(1, filter.stats.rejectedByMock)
        assertTrue((result as FilterResult.Rejected).reason.contains("Layer 0"))
    }

    @Test
    fun `TEST FIX 3 - Layer 0 tidak menolak lokasi asli (bukan mock)`() {
        val curr = createMockLocation(isMockLocation = false)
        
        val result = filter.filter(curr, null, isOutdoorSession = true)

        assertTrue(result is FilterResult.Valid)
        assertEquals(0, filter.stats.rejectedByMock)
    }

    @Test
    fun `titik valid harus diterima oleh semua layer`() {
        val prev = createMockLocation(lat = -6.20000, timeDeltaSec = 0.0, bearing = 90f)
        val curr = createMockLocation(lat = -6.20025, timeDeltaSec = 5.0, bearing = 90f)
        every { prev.distanceTo(curr) } returns 25.0f

        val result = filter.filter(curr, prev, isOutdoorSession = true)

        assertTrue(result is FilterResult.Valid)
        assertEquals(0, filter.stats.totalRejected)
    }

    @Test
    fun `layer 1 menolak titik dengan akurasi lebih dari 20 meter`() {
        val curr = createMockLocation(acc = 25.0f) // > 20m
        val result = filter.filter(curr, null, isOutdoorSession = true)

        assertTrue(result is FilterResult.Rejected)
        assertEquals(1, filter.stats.rejectedByAccuracy)
    }

    @Test
    fun `layer 2 menolak titik jika kecepatan melebihi 12 meter per detik`() {
        val prev = createMockLocation(timeDeltaSec = 0.0)
        val curr = createMockLocation(timeDeltaSec = 1.0)
        every { prev.distanceTo(curr) } returns 15.0f // 15 m/s

        val result = filter.filter(curr, prev, isOutdoorSession = true)

        assertTrue(result is FilterResult.Rejected)
        assertEquals(1, filter.stats.rejectedBySpeed)
    }

    @Test
    fun `layer 3 menolak lonjakan bearing melebihi 120 derajat per detik`() {
        val prev = createMockLocation(timeDeltaSec = 0.0, bearing = 0f)
        val curr = createMockLocation(timeDeltaSec = 1.0, bearing = 180f)
        every { prev.distanceTo(curr) } returns 3.0f

        val result = filter.filter(curr, prev, isOutdoorSession = true)

        assertTrue(result is FilterResult.Rejected)
        assertEquals(1, filter.stats.rejectedByBearing)
    }

    @Test
    fun `layer 4 menolak provider network saat outdoor jika akurasi buruk`() {
        val curr = createMockLocation(provider = "network", acc = 55.0f)
        val result = filter.filter(curr, null, isOutdoorSession = true)

        assertTrue(result is FilterResult.Rejected)
    }

    @Test
    fun `edge case menolak titik pertama setelah gap sinyal lebih dari 5 detik`() {
        val prev = createMockLocation(timeDeltaSec = 0.0)
        val curr = createMockLocation(timeDeltaSec = 6.0) // Gap > 5s
        every { prev.distanceTo(curr) } returns 2.0f

        val result = filter.filter(curr, prev, isOutdoorSession = true)

        assertTrue(result is FilterResult.Rejected)
        assertEquals(1, filter.stats.rejectedByGap)
    }
}
