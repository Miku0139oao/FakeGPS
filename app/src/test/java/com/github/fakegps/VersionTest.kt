package com.github.fakegps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun parsesPlainAndPrefixedVersions() {
        assertEquals(Version(2, 2, 2), Version.parse("2.2.2"))
        assertEquals(Version(2, 2, 2), Version.parse("v2.2.2"))
        assertEquals(Version(2, 2, 2), Version.parse(" V2.2.2 "))
    }

    @Test
    fun padsMissingPartsWithZero() {
        assertEquals(Version(2, 2, 0), Version.parse("2.2"))
        assertEquals(Version(3, 0, 0), Version.parse("3"))
    }

    @Test
    fun ignoresPreReleaseAndBuildSuffixes() {
        assertEquals(Version(2, 3, 0), Version.parse("v2.3.0-beta.1"))
        assertEquals(Version(2, 3, 0), Version.parse("2.3.0+build.5"))
    }

    @Test
    fun rejectsInvalidInput() {
        val invalid = listOf(null, "", "   ", "v", "abc", "2.x.3", "1.2.3.4", "-1.0.0", ".")
        for (raw in invalid) {
            assertNull("expected null for '$raw'", Version.parse(raw))
        }
    }

    @Test
    fun comparesNumericallyNotLexicographically() {
        assertTrue(Version.parse("2.2.10")!! > Version.parse("2.2.9")!!)
        assertTrue(Version.parse("2.3.0")!! > Version.parse("2.2.99")!!)
        assertTrue(Version.parse("3.0.0")!! > Version.parse("2.99.99")!!)
        assertTrue(Version.parse("2.2.2")!! < Version.parse("2.2.3")!!)
        assertEquals(0, Version.parse("v2.2.2")!!.compareTo(Version(2, 2, 2)))
    }

    @Test
    fun formatsBackToDottedString() {
        assertEquals("2.2.2", Version(2, 2, 2).toString())
        assertEquals("10.0.1", Version.parse("v10.0.1")!!.toString())
    }
}
