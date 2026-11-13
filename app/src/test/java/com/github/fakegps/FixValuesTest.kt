package com.github.fakegps

import com.github.fakegps.model.LocPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FixValuesTest {

    @Test
    fun stationaryFixHasNoSpeedOrBearing() {
        val values = FixValues.from(
            moving = false,
            delta = null,
            wallTimeMs = 123L,
            elapsedRealtimeNanos = 456L,
            api26OrNewer = true
        )

        assertEquals(50.0, values.altitudeMeters, 0.0)
        assertEquals(10.0f, values.accuracyMeters, 0.0f)
        assertEquals(123L, values.wallTimeMs)
        assertEquals(456L, values.elapsedRealtimeNanos)
        assertNull(values.speedMetersPerSecond)
        assertNull(values.bearingDegrees)
        assertEquals(1.0f, requireNotNull(values.verticalAccuracyMeters), 0.0f)
        assertNull(values.bearingAccuracyDegrees)
        assertNull(values.speedAccuracyMetersPerSecond)
    }

    @Test
    fun movingEastReportsSpeedAndBearing() {
        val from = LocPoint(0.0, 0.0)
        val to = GeoUtils.offset(from, 0.0, 2.0)
        val delta = LocationDelta.between(from, to, 1000)

        val values = FixValues.from(
            moving = true,
            delta = delta,
            wallTimeMs = 1L,
            elapsedRealtimeNanos = 2L,
            api26OrNewer = true
        )

        assertEquals(2.0f, requireNotNull(values.speedMetersPerSecond), 0.01f)
        assertEquals(90.0f, requireNotNull(values.bearingDegrees), 1.0f)
        assertEquals(1.0f, requireNotNull(values.verticalAccuracyMeters), 0.0f)
        assertEquals(5.0f, requireNotNull(values.bearingAccuracyDegrees), 0.0f)
        assertEquals(0.5f, requireNotNull(values.speedAccuracyMetersPerSecond), 0.0f)
    }

    @Test
    fun preApi26OmitsNewAccuracyFields() {
        val from = LocPoint(0.0, 0.0)
        val to = GeoUtils.offset(from, 2.0, 0.0)
        val delta = LocationDelta.between(from, to, 1000)

        val values = FixValues.from(
            moving = true,
            delta = delta,
            wallTimeMs = 1L,
            elapsedRealtimeNanos = 2L,
            api26OrNewer = false
        )

        assertEquals(2.0f, requireNotNull(values.speedMetersPerSecond), 0.01f)
        assertEquals(0.0f, requireNotNull(values.bearingDegrees), 1.0f)
        assertNull(values.verticalAccuracyMeters)
        assertNull(values.bearingAccuracyDegrees)
        assertNull(values.speedAccuracyMetersPerSecond)
    }
}
