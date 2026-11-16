package com.github.fakegps

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateParserLocaleTest {

    @Test
    fun fullWidthPunctuationFromChineseImeIsAccepted() {
        val point = requireNotNull(CoordinateParser.parse("22。37139，114。17833"))
        assertEquals(22.37139, point.latitude, 1e-6)
        assertEquals(114.17833, point.longitude, 1e-6)
    }

    @Test
    fun fullWidthDigitsAndMinusAreAccepted() {
        val point = requireNotNull(CoordinateParser.parse("－３３．５，１５１．２５"))
        assertEquals(-33.5, point.latitude, 1e-6)
        assertEquals(151.25, point.longitude, 1e-6)
    }

    @Test
    fun fullWidthGeoUriIsAccepted() {
        val point = requireNotNull(CoordinateParser.parse("ｇｅｏ：22。37139，114。17833"))
        assertEquals(22.37139, point.latitude, 1e-6)
        assertEquals(114.17833, point.longitude, 1e-6)
    }

    @Test
    fun dmsParsingIsStableUnderTurkishLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val point = requireNotNull(CoordinateParser.parse("37°48'8.7\"N 122°24'6.4\"W"))
            assertEquals(37.802416, point.latitude, 1e-6)
            assertEquals(-122.401778, point.longitude, 1e-6)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
