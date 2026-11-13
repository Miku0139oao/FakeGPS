package com.github.fakegps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    private val epsilon = 1e-6

    @Test
    fun parsesPlainCoordinates() {
        val point = requireNotNull(CoordinateParser.parse("(37.802406 , -122.401779)"))

        assertEquals(37.802406, point.latitude, epsilon)
        assertEquals(-122.401779, point.longitude, epsilon)
    }

    @Test
    fun parsesGeoUri() {
        val point = requireNotNull(CoordinateParser.parse("geo:37.802406,-122.401779?z=15"))

        assertEquals(37.802406, point.latitude, epsilon)
        assertEquals(-122.401779, point.longitude, epsilon)
    }

    @Test
    fun parsesMapAtLink() {
        val point = requireNotNull(
            CoordinateParser.parse("https://www.google.com/maps/@37.802406,-122.401779,15z")
        )

        assertEquals(37.802406, point.latitude, epsilon)
        assertEquals(-122.401779, point.longitude, epsilon)
    }

    @Test
    fun parsesMapQueryLink() {
        val point = requireNotNull(
            CoordinateParser.parse("https://maps.google.com/?q=37.802406,-122.401779")
        )

        assertEquals(37.802406, point.latitude, epsilon)
        assertEquals(-122.401779, point.longitude, epsilon)
    }

    @Test
    fun parsesMapLlAndCenterLinks() {
        val ll = requireNotNull(
            CoordinateParser.parse("https://maps.google.com/?ll=37.802406,-122.401779")
        )
        val center = requireNotNull(
            CoordinateParser.parse("https://example.com/?center=37.802406,-122.401779")
        )

        assertEquals(37.802406, ll.latitude, epsilon)
        assertEquals(-122.401779, center.longitude, epsilon)
    }

    @Test
    fun parsesDms() {
        val point = requireNotNull(CoordinateParser.parse("37°48'8.7\"N 122°24'6.4\"W"))

        assertEquals(37.802416, point.latitude, epsilon)
        assertEquals(-122.401778, point.longitude, epsilon)
    }

    @Test
    fun parsesDmsWithLongitudeFirst() {
        val point = requireNotNull(CoordinateParser.parse("122°24'6.4\"W 37°48'8.7\"N"))

        assertEquals(37.802416, point.latitude, epsilon)
        assertEquals(-122.401778, point.longitude, epsilon)
    }

    @Test
    fun rejectsInvalidValues() {
        assertNull(CoordinateParser.parse("NaN, 0"))
        assertNull(CoordinateParser.parse("91, 0"))
        assertNull(CoordinateParser.parse("0, 181"))
        assertNull(CoordinateParser.parse("not a coordinate"))
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse(null))
    }
}
