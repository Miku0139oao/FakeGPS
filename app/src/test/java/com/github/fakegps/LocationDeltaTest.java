package com.github.fakegps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.fakegps.model.LocPoint;

import org.junit.Test;

public class LocationDeltaTest {

    private static final double EPSILON = 1e-6;

    @Test
    public void eastMovementReportsBearingAndSpeed() {
        LocPoint from = new LocPoint(0.0, 0.0);
        LocPoint to = GeoUtils.offset(from, 0.0, 2.0);
        LocationDelta delta = LocationDelta.between(from, to, 1000);

        assertTrue(delta.isMoving());
        assertEquals(2.0, delta.getDistanceMeters(), 0.01);
        assertEquals(2.0, delta.getSpeedMetersPerSecond(), 0.01);
        assertEquals(90.0, delta.getBearingDegrees(), 1.0);
    }

    @Test
    public void northMovementReportsBearing() {
        LocPoint from = new LocPoint(0.0, 0.0);
        LocPoint to = GeoUtils.offset(from, 2.0, 0.0);
        LocationDelta delta = LocationDelta.between(from, to, 1000);

        assertEquals(0.0, delta.getBearingDegrees(), 1.0);
    }

    @Test
    public void stationaryPointIsNotMoving() {
        LocPoint point = new LocPoint(37.0, -122.0);
        LocationDelta delta = LocationDelta.between(point, point, 1000);

        assertFalse(delta.isMoving());
        assertEquals(0.0, delta.getSpeedMetersPerSecond(), EPSILON);
    }

    @Test
    public void zeroElapsedIsNotMoving() {
        LocPoint from = new LocPoint(0.0, 0.0);
        LocPoint to = GeoUtils.offset(from, 0.0, 10.0);
        LocationDelta delta = LocationDelta.between(from, to, 0);

        assertFalse(delta.isMoving());
    }
}
