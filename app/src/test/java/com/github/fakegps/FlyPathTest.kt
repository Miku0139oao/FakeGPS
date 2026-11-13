package com.github.fakegps

import com.github.fakegps.model.LocPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlyPathTest {

    private val epsilon = 1e-9

    @Test
    fun pointAt_isLinear() {
        val path = FlyPath(LocPoint(0.0, 0.0), LocPoint(10.0, 20.0), 10_000L)

        assertEquals(0.0, path.pointAt(0).latitude, epsilon)
        assertEquals(2.5, path.pointAt(2_500).latitude, epsilon)
        assertEquals(5.0, path.pointAt(5_000).latitude, epsilon)
        assertEquals(7.5, path.pointAt(7_500).latitude, epsilon)
        assertEquals(10.0, path.pointAt(10_000).latitude, epsilon)
        assertEquals(10.0, path.pointAt(5_000).longitude, epsilon)
    }

    @Test
    fun pointAt_clampsAfterCompletion() {
        val path = FlyPath(LocPoint(1.0, 2.0), LocPoint(3.0, 4.0), 1_000L)

        assertEquals(3.0, path.pointAt(5_000).latitude, epsilon)
        assertEquals(4.0, path.pointAt(5_000).longitude, epsilon)
    }

    @Test
    fun pointAt_handlesNegativeElapsed() {
        val path = FlyPath(LocPoint(1.0, 2.0), LocPoint(3.0, 4.0), 1_000L)

        assertEquals(1.0, path.pointAt(-100).latitude, epsilon)
    }

    @Test
    fun zeroDuration_finishesImmediately() {
        val path = FlyPath(LocPoint(1.0, 2.0), LocPoint(3.0, 4.0), 0L)

        assertEquals(3.0, path.pointAt(0).latitude, epsilon)
        assertTrue(path.isComplete(0))
    }

    @Test
    fun isComplete_onlyAfterDuration() {
        val path = FlyPath(LocPoint(0.0, 0.0), LocPoint(1.0, 1.0), 2_000L)

        assertFalse(path.isComplete(1_999))
        assertTrue(path.isComplete(2_000))
    }
}
