package com.github.fakegps.model

import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.lang.reflect.Modifier
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the on-disk format of [LocPoint].
 *
 * Bookmarks store the point as a Java-serialized BLOB inside lite-orm's
 * `mLocPoint` column. The class name, serialVersionUID and field names are part
 * of that format: changing any of them makes existing bookmarks unreadable.
 * The golden fixture was produced by the Java implementation before the Kotlin
 * migration and must keep deserializing to the same coordinates.
 */
class LocPointSerializationCompatTest {

    @Test
    fun serialVersionUidMatchesLegacyValue() {
        assertEquals(
            LEGACY_SERIAL_VERSION_UID,
            ObjectStreamClass.lookup(LocPoint::class.java).serialVersionUID
        )
    }

    @Test
    fun declaresLegacyDoubleFields() {
        val instanceFields = LocPoint::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) }

        assertEquals(2, instanceFields.size)
        for (field in instanceFields) {
            assertEquals(java.lang.Double.TYPE, field.type)
        }
        assertTrue(instanceFields.any { it.name == "mLatitude" })
        assertTrue(instanceFields.any { it.name == "mLongitude" })
    }

    @Test
    fun deserializesGoldenBlobProducedByJavaImplementation() {
        val stream = javaClass.getResourceAsStream("/locpoint_legacy.ser")
        assertNotNull("missing golden serialization fixture", stream)
        val restored = ObjectInputStream(requireNotNull(stream)).use { it.readObject() }
        assertTrue(restored is LocPoint)
        val point = restored as LocPoint
        assertEquals(37.802406, point.latitude, 1e-12)
        assertEquals(-122.401779, point.longitude, 1e-12)
    }

    @Test
    fun toStringKeepsUsLocaleFormatting() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("(37.802406 , -122.401779)", LocPoint(37.802406, -122.401779).toString())
        } finally {
            Locale.setDefault(previous)
        }
    }

    private companion object {
        private const val LEGACY_SERIAL_VERSION_UID = -1770575152720897533L
    }
}
