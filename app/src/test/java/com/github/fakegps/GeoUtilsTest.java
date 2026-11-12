package com.github.fakegps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.fakegps.model.LocPoint;

import org.junit.Test;

public class GeoUtilsTest {

    private static final double EPSILON = 1e-9;

    @Test
    public void northOffset_usesMeters() {
        LocPoint start = new LocPoint(0.0, 0.0);
        LocPoint moved = GeoUtils.offset(start, 111.32, 0.0);

        assertEquals(0.001, moved.getLatitude(), 1e-6);
        assertEquals(0.0, moved.getLongitude(), EPSILON);
    }

    @Test
    public void eastOffset_shrinksWithLatitude() {
        LocPoint equator = GeoUtils.offset(new LocPoint(0.0, 0.0), 0.0, 111.32);
        LocPoint highLatitude = GeoUtils.offset(new LocPoint(60.0, 0.0), 0.0, 111.32);

        assertEquals(0.001, equator.getLongitude(), 1e-6);
        assertEquals(0.002, highLatitude.getLongitude(), 1e-6);
    }

    @Test
    public void latitude_isClamped() {
        LocPoint moved = GeoUtils.offset(new LocPoint(89.9999, 0.0), 1000.0, 0.0);

        assertEquals(90.0, moved.getLatitude(), EPSILON);
    }

    @Test
    public void longitude_wrapsAtAntimeridian() {
        LocPoint moved = GeoUtils.offset(new LocPoint(0.0, 179.9999), 0.0, 1000.0);

        assertTrue(moved.getLongitude() < 0);
        assertTrue(moved.getLongitude() >= -180.0);
    }

    @Test
    public void wrapLongitude_normalizes() {
        assertEquals(-180.0, GeoUtils.wrapLongitude(180.0), EPSILON);
        assertEquals(-170.0, GeoUtils.wrapLongitude(190.0), EPSILON);
        assertEquals(170.0, GeoUtils.wrapLongitude(-190.0), EPSILON);
    }
}
