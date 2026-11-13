package com.github.fakegps

import com.github.fakegps.model.LocPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationDeltaTest {

    private val epsilon = 1e-6

    @Test
    fun eastMovementReportsBearingAndSpeed() {
        val from = LocPoint(0.0, 0.0)
        val to = GeoUtils.offset(from, 0.0, 2.0)
        val delta = LocationDelta.between(from, to, 1000)

        assertTrue(delta.isMoving)
        assertEquals(2.0, delta.distanceMeters, 0.01)
        assertEquals(2.0, delta.speedMetersPerSecond, 0.01)
        assertEquals(90.0, delta.bearingDegrees, 1.0)
    }

    @Test
    fun northMovementReportsBearing() {
        val from = LocPoint(0.0, 0.0)
        val to = GeoUtils.offset(from, 2.0, 0.0)
        val delta = LocationDelta.between(from, to, 1000)

        assertEquals(0.0, delta.bearingDegrees, 1.0)
    }

    @Test
    fun stationaryPointIsNotMoving() {
        val point = LocPoint(37.0, -122.0)
        val delta = LocationDelta.between(point, point, 1000)

        assertFalse(delta.isMoving)
        assertEquals(0.0, delta.speedMetersPerSecond, epsilon)
    }

    @Test
    fun zeroElapsedIsNotMoving() {
        val from = LocPoint(0.0, 0.0)
        val to = GeoUtils.offset(from, 0.0, 10.0)
        val delta = LocationDelta.between(from, to, 0)

        assertFalse(delta.isMoving)
    }
}
