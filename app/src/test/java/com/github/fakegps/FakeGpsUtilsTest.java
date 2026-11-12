package com.github.fakegps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FakeGpsUtilsTest {

    @Test
    public void latitude_acceptsValidRange() {
        assertTrue(FakeGpsUtils.isValidLatitude(-90.0));
        assertTrue(FakeGpsUtils.isValidLatitude(0.0));
        assertTrue(FakeGpsUtils.isValidLatitude(90.0));
    }

    @Test
    public void latitude_rejectsInvalidValues() {
        assertFalse(FakeGpsUtils.isValidLatitude(90.0001));
        assertFalse(FakeGpsUtils.isValidLatitude(-90.0001));
        assertFalse(FakeGpsUtils.isValidLatitude(Double.NaN));
        assertFalse(FakeGpsUtils.isValidLatitude(Double.POSITIVE_INFINITY));
    }

    @Test
    public void longitude_acceptsValidRange() {
        assertTrue(FakeGpsUtils.isValidLongitude(-180.0));
        assertTrue(FakeGpsUtils.isValidLongitude(180.0));
    }

    @Test
    public void longitude_rejectsInvalidValues() {
        assertFalse(FakeGpsUtils.isValidLongitude(180.0001));
        assertFalse(FakeGpsUtils.isValidLongitude(Double.NaN));
        assertFalse(FakeGpsUtils.isValidLongitude(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void moveStep_mustBeFiniteAndPositive() {
        assertTrue(FakeGpsUtils.isValidMoveStep(0.00002));
        assertFalse(FakeGpsUtils.isValidMoveStep(0));
        assertFalse(FakeGpsUtils.isValidMoveStep(-1));
        assertFalse(FakeGpsUtils.isValidMoveStep(Double.NaN));
        assertFalse(FakeGpsUtils.isValidMoveStep(Double.POSITIVE_INFINITY));
    }

    @Test
    public void parseMoveStep_handlesText() {
        assertTrue(FakeGpsUtils.parseMoveStep("2") == 2.0);
        assertTrue(FakeGpsUtils.parseMoveStep(" 2.5 ") == 2.5);
        assertTrue(FakeGpsUtils.parseMoveStep("0") == 0.0);
        assertTrue(FakeGpsUtils.parseMoveStep("-1") == 0.0);
        assertTrue(FakeGpsUtils.parseMoveStep("NaN") == 0.0);
        assertTrue(FakeGpsUtils.parseMoveStep("abc") == 0.0);
        assertTrue(FakeGpsUtils.parseMoveStep("") == 0.0);
    }

    @Test
    public void parseIntValue_handlesText() {
        assertTrue(FakeGpsUtils.parseIntValue("60") == 60);
        assertTrue(FakeGpsUtils.parseIntValue(" 60 ") == 60);
        assertTrue(FakeGpsUtils.parseIntValue("60.5") == 0);
        assertTrue(FakeGpsUtils.parseIntValue("") == 0);
        assertTrue(FakeGpsUtils.parseIntValue("999999999999") == 0);
    }

    @Test
    public void parseLocation_roundTripsAndRejectsInvalid() {
        assertTrue(FakeGpsUtils.parseLocation("37.802406, -122.401779") != null);
        assertTrue(FakeGpsUtils.parseLocation("(37.802406 , -122.401779)") != null);
        assertTrue(FakeGpsUtils.parseLocation("NaN, 0") == null);
    }
}
