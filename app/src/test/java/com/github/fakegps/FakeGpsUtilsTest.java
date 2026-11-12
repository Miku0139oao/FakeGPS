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
}
