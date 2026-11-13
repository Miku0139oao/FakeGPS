package com.github.fakegps.model

import com.litesuits.orm.db.TableManager
import com.litesuits.orm.db.annotation.Column
import com.litesuits.orm.db.annotation.NotNull
import com.litesuits.orm.db.annotation.PrimaryKey
import com.litesuits.orm.db.enums.AssignType
import java.io.ObjectStreamClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the lite-orm schema of [LocBookmark].
 *
 * lite-orm is field-reflective. The table name, column names, primary key and
 * annotations are the on-disk schema of `fake_gps.db`; renaming a field or
 * changing the primary key can make every existing bookmark unreadable (or drop
 * the table). This test fails if the Kotlin migration changes any of them.
 */
class LocBookmarkLiteOrmSchemaTest {

    @Test
    fun tableMetadataMatchesLegacySchema() {
        val table = TableManager.getTable(LocBookmark::class.java)

        assertEquals("LocBookmark", table.name)
        assertNotNull("LocBookmark needs a primary key", table.key)
        assertEquals("_id", table.key.column)
        assertEquals(AssignType.AUTO_INCREMENT, table.key.assign)
        assertEquals(setOf("mName", "mLocPoint"), table.pmap.keys)
    }

    @Test
    fun fieldAnnotationsMatchLegacySchema() {
        val id = LocBookmark::class.java.getDeclaredField("id")
        val primaryKey = requireNotNull(id.getAnnotation(PrimaryKey::class.java))
        assertEquals(AssignType.AUTO_INCREMENT, primaryKey.value)
        assertEquals("_id", requireNotNull(id.getAnnotation(Column::class.java)).value)
        assertEquals(Int::class.javaPrimitiveType, id.type)

        val name = LocBookmark::class.java.getDeclaredField("mName")
        assertEquals(String::class.java, name.type)
        assertNotNull(name.getAnnotation(NotNull::class.java))

        val locPoint = LocBookmark::class.java.getDeclaredField("mLocPoint")
        assertEquals(LocPoint::class.java, locPoint.type)
    }

    @Test
    fun hasNoArgConstructorAndNullableLocPoint() {
        val bookmark = LocBookmark::class.java.getDeclaredConstructor().newInstance()

        assertNull(bookmark.locPoint)
        assertNull(bookmark.name)
        bookmark.locPoint = LocPoint(1.5, 2.5)
        assertEquals(1.5, requireNotNull(bookmark.locPoint).latitude, 0.0)
    }

    @Test
    fun serialVersionUidMatchesLegacyValue() {
        assertEquals(
            LEGACY_SERIAL_VERSION_UID,
            ObjectStreamClass.lookup(LocBookmark::class.java).serialVersionUID
        )
    }

    private companion object {
        private const val LEGACY_SERIAL_VERSION_UID = -1770575152720897666L
    }
}
