package com.github.fakegps

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateParserLocaleTest {

    @Test
    fun dmsParsingIsStableUnderTurkishLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val point = requireNotNull(CoordinateParser.parse("37°48'8.7\"N 122°24'6.4\"W"))
            assertEquals(37.802416, point.latitude, 1e-6)
            assertEquals(-122.401778, point.longitude, 1e-6)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
