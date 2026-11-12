package com.github.fakegps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.github.fakegps.model.LocPoint;

import org.junit.Test;

public class CoordinateParserTest {

    private static final double EPSILON = 1e-6;

    @Test
    public void parsesPlainCoordinates() {
        LocPoint point = CoordinateParser.parse("(37.802406 , -122.401779)");

        assertEquals(37.802406, point.getLatitude(), EPSILON);
        assertEquals(-122.401779, point.getLongitude(), EPSILON);
    }

    @Test
    public void parsesGeoUri() {
        LocPoint point = CoordinateParser.parse("geo:37.802406,-122.401779?z=15");

        assertEquals(37.802406, point.getLatitude(), EPSILON);
        assertEquals(-122.401779, point.getLongitude(), EPSILON);
    }

    @Test
    public void parsesMapAtLink() {
        LocPoint point = CoordinateParser.parse(
                "https://www.google.com/maps/@37.802406,-122.401779,15z");

        assertEquals(37.802406, point.getLatitude(), EPSILON);
        assertEquals(-122.401779, point.getLongitude(), EPSILON);
    }

    @Test
    public void parsesMapQueryLink() {
        LocPoint point = CoordinateParser.parse(
                "https://maps.google.com/?q=37.802406,-122.401779");

        assertEquals(37.802406, point.getLatitude(), EPSILON);
        assertEquals(-122.401779, point.getLongitude(), EPSILON);
    }

    @Test
    public void parsesDms() {
        LocPoint point = CoordinateParser.parse("37°48'8.7\"N 122°24'6.4\"W");

        assertEquals(37.802416, point.getLatitude(), EPSILON);
        assertEquals(-122.401778, point.getLongitude(), EPSILON);
    }

    @Test
    public void rejectsInvalidValues() {
        assertNull(CoordinateParser.parse("NaN, 0"));
        assertNull(CoordinateParser.parse("91, 0"));
        assertNull(CoordinateParser.parse("0, 181"));
        assertNull(CoordinateParser.parse("not a coordinate"));
        assertNull(CoordinateParser.parse(""));
    }
}
