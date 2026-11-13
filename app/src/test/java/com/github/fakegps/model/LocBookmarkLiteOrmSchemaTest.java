package com.github.fakegps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.litesuits.orm.db.TableManager;
import com.litesuits.orm.db.annotation.Column;
import com.litesuits.orm.db.annotation.NotNull;
import com.litesuits.orm.db.annotation.PrimaryKey;
import com.litesuits.orm.db.enums.AssignType;
import com.litesuits.orm.db.model.EntityTable;

import org.junit.Test;

import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Guards the lite-orm schema of {@link LocBookmark}.
 *
 * <p>lite-orm is field-reflective. The table name, column names, primary key and
 * annotations are the on-disk schema of {@code fake_gps.db}; renaming a field or
 * changing the primary key can make every existing bookmark unreadable (or drop
 * the table). This test fails if the Kotlin migration changes any of them.</p>
 */
public class LocBookmarkLiteOrmSchemaTest {

    private static final long LEGACY_SERIAL_VERSION_UID = -1770575152720897666L;

    @Test
    public void tableMetadataMatchesLegacySchema() {
        EntityTable table = TableManager.getTable(LocBookmark.class);
        assertEquals("LocBookmark", table.name);
        assertNotNull("LocBookmark needs a primary key", table.key);
        assertEquals("_id", table.key.column);
        assertEquals(AssignType.AUTO_INCREMENT, table.key.assign);
        assertEquals(new HashSet<>(Arrays.asList("mName", "mLocPoint")), table.pmap.keySet());
    }

    @Test
    public void fieldAnnotationsMatchLegacySchema() throws Exception {
        Field id = LocBookmark.class.getDeclaredField("id");
        assertNotNull(id.getAnnotation(PrimaryKey.class));
        assertEquals(AssignType.AUTO_INCREMENT, id.getAnnotation(PrimaryKey.class).value());
        assertNotNull(id.getAnnotation(Column.class));
        assertEquals("_id", id.getAnnotation(Column.class).value());
        assertEquals(int.class, id.getType());

        Field name = LocBookmark.class.getDeclaredField("mName");
        assertEquals(String.class, name.getType());
        assertNotNull(name.getAnnotation(NotNull.class));

        Field locPoint = LocBookmark.class.getDeclaredField("mLocPoint");
        assertEquals(LocPoint.class, locPoint.getType());
    }

    @Test
    public void hasNoArgConstructorAndNullableLocPoint() throws Exception {
        assertNotNull(LocBookmark.class.getDeclaredConstructor());
        LocBookmark bookmark = LocBookmark.class.getDeclaredConstructor().newInstance();
        assertNull(bookmark.getLocPoint());
        assertNull(bookmark.getName());
        bookmark.setLocPoint(new LocPoint(1.5, 2.5));
        assertEquals(1.5, bookmark.getLocPoint().getLatitude(), 0.0);
    }

    @Test
    public void serialVersionUidMatchesLegacyValue() {
        assertEquals(LEGACY_SERIAL_VERSION_UID,
                ObjectStreamClass.lookup(LocBookmark.class).getSerialVersionUID());
    }
}
