package com.github.fakegps

import com.github.fakegps.model.LocPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    private val epsilon = 1e-9

    @Test
    fun northOffset_usesMeters() {
        val start = LocPoint(0.0, 0.0)
        val moved = GeoUtils.offset(start, 111.32, 0.0)

        assertEquals(0.001, moved.latitude, 1e-6)
        assertEquals(0.0, moved.longitude, epsilon)
    }

    @Test
    fun eastOffset_shrinksWithLatitude() {
        val equator = GeoUtils.offset(LocPoint(0.0, 0.0), 0.0, 111.32)
        val highLatitude = GeoUtils.offset(LocPoint(60.0, 0.0), 0.0, 111.32)

        assertEquals(0.001, equator.longitude, 1e-6)
        assertEquals(0.002, highLatitude.longitude, 1e-6)
    }

    @Test
    fun latitude_isClamped() {
        val moved = GeoUtils.offset(LocPoint(89.9999, 0.0), 1000.0, 0.0)

        assertEquals(90.0, moved.latitude, epsilon)
    }

    @Test
    fun longitude_wrapsAtAntimeridian() {
        val moved = GeoUtils.offset(LocPoint(0.0, 179.9999), 0.0, 1000.0)

        assertTrue(moved.longitude < 0)
        assertTrue(moved.longitude >= -180.0)
    }

    @Test
    fun wrapLongitude_normalizes() {
        assertEquals(-180.0, GeoUtils.wrapLongitude(180.0), epsilon)
        assertEquals(-170.0, GeoUtils.wrapLongitude(190.0), epsilon)
        assertEquals(170.0, GeoUtils.wrapLongitude(-190.0), epsilon)
    }
}
