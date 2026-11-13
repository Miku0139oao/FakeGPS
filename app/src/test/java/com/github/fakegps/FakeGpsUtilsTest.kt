package com.github.fakegps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeGpsUtilsTest {

    @Test
    fun latitude_acceptsValidRange() {
        assertTrue(FakeGpsUtils.isValidLatitude(-90.0))
        assertTrue(FakeGpsUtils.isValidLatitude(0.0))
        assertTrue(FakeGpsUtils.isValidLatitude(90.0))
    }

    @Test
    fun latitude_rejectsInvalidValues() {
        assertFalse(FakeGpsUtils.isValidLatitude(90.0001))
        assertFalse(FakeGpsUtils.isValidLatitude(-90.0001))
        assertFalse(FakeGpsUtils.isValidLatitude(Double.NaN))
        assertFalse(FakeGpsUtils.isValidLatitude(Double.POSITIVE_INFINITY))
    }

    @Test
    fun longitude_acceptsValidRange() {
        assertTrue(FakeGpsUtils.isValidLongitude(-180.0))
        assertTrue(FakeGpsUtils.isValidLongitude(180.0))
    }

    @Test
    fun longitude_rejectsInvalidValues() {
        assertFalse(FakeGpsUtils.isValidLongitude(180.0001))
        assertFalse(FakeGpsUtils.isValidLongitude(Double.NaN))
        assertFalse(FakeGpsUtils.isValidLongitude(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun moveStep_mustBeFiniteAndPositive() {
        assertTrue(FakeGpsUtils.isValidMoveStep(0.00002))
        assertFalse(FakeGpsUtils.isValidMoveStep(0.0))
        assertFalse(FakeGpsUtils.isValidMoveStep(-1.0))
        assertFalse(FakeGpsUtils.isValidMoveStep(Double.NaN))
        assertFalse(FakeGpsUtils.isValidMoveStep(Double.POSITIVE_INFINITY))
    }

    @Test
    fun parseMoveStep_handlesText() {
        assertEquals(2.0, FakeGpsUtils.parseMoveStep("2"), 0.0)
        assertEquals(2.5, FakeGpsUtils.parseMoveStep(" 2.5 "), 0.0)
        assertEquals(0.0, FakeGpsUtils.parseMoveStep("0"), 0.0)
        assertEquals(0.0, FakeGpsUtils.parseMoveStep("-1"), 0.0)
        assertEquals(0.0, FakeGpsUtils.parseMoveStep("NaN"), 0.0)
        assertEquals(0.0, FakeGpsUtils.parseMoveStep("abc"), 0.0)
        assertEquals(0.0, FakeGpsUtils.parseMoveStep(""), 0.0)
    }

    @Test
    fun parseIntValue_handlesText() {
        assertEquals(60, FakeGpsUtils.parseIntValue("60"))
        assertEquals(60, FakeGpsUtils.parseIntValue(" 60 "))
        assertEquals(0, FakeGpsUtils.parseIntValue("60.5"))
        assertEquals(0, FakeGpsUtils.parseIntValue(""))
        assertEquals(0, FakeGpsUtils.parseIntValue("999999999999"))
    }

    @Test
    fun parseLocation_roundTripsAndRejectsInvalid() {
        assertNotNull(FakeGpsUtils.parseLocation("37.802406, -122.401779"))
        assertNotNull(FakeGpsUtils.parseLocation("(37.802406 , -122.401779)"))
        assertNull(FakeGpsUtils.parseLocation("NaN, 0"))
        assertNull(FakeGpsUtils.parseLocation(null))
    }
}
