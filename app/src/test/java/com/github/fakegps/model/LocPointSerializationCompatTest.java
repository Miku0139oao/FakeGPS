package com.github.fakegps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Guards the on-disk format of {@link LocPoint}.
 *
 * <p>Bookmarks store the point as a Java-serialized BLOB inside lite-orm's
 * {@code mLocPoint} column. The class name, serialVersionUID and field names are
 * part of that format: changing any of them makes existing bookmarks unreadable.
 * The golden fixture was produced by the Java implementation before the Kotlin
 * migration and must keep deserializing to the same coordinates.</p>
 */
public class LocPointSerializationCompatTest {

    private static final long LEGACY_SERIAL_VERSION_UID = -1770575152720897533L;

    @Test
    public void serialVersionUidMatchesLegacyValue() {
        assertEquals(LEGACY_SERIAL_VERSION_UID,
                ObjectStreamClass.lookup(LocPoint.class).getSerialVersionUID());
    }

    @Test
    public void declaresLegacyDoubleFields() {
        List<Field> instanceFields = new ArrayList<>();
        for (Field field : LocPoint.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                instanceFields.add(field);
            }
        }
        assertEquals("LocPoint must keep exactly two instance fields", 2, instanceFields.size());
        for (Field field : instanceFields) {
            assertEquals(double.class, field.getType());
        }
        assertTrue(hasField(instanceFields, "mLatitude"));
        assertTrue(hasField(instanceFields, "mLongitude"));
    }

    @Test
    public void deserializesGoldenBlobProducedByJavaImplementation() throws Exception {
        try (InputStream in = LocPointSerializationCompatTest.class
                .getResourceAsStream("/locpoint_legacy.ser")) {
            assertNotNull("missing golden serialization fixture", in);
            Object restored = new ObjectInputStream(in).readObject();
            assertTrue(restored instanceof LocPoint);
            LocPoint point = (LocPoint) restored;
            assertEquals(37.802406, point.getLatitude(), 1e-12);
            assertEquals(-122.401779, point.getLongitude(), 1e-12);
        }
    }

    @Test
    public void toStringKeepsUsLocaleFormatting() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("(37.802406 , -122.401779)",
                    new LocPoint(37.802406, -122.401779).toString());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static boolean hasField(List<Field> fields, String name) {
        for (Field field : fields) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
