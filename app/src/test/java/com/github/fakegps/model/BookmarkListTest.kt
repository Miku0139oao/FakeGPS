package com.github.fakegps.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkListTest {

    @Test
    fun settingEmptyList_clearsItems() {
        val list = BookmarkList()
        val items = ArrayList<LocBookmark>()
        items.add(LocBookmark("home", LocPoint(1.0, 2.0)))
        list.setItems(items)
        assertEquals(1, list.size())

        list.setItems(ArrayList())
        assertEquals(0, list.size())
        assertTrue(list.isEmpty())
    }

    @Test
    fun settingNullList_clearsItems() {
        val list = BookmarkList()
        val items = ArrayList<LocBookmark>()
        items.add(LocBookmark("home", LocPoint(1.0, 2.0)))
        list.setItems(items)

        list.setItems(null)
        assertEquals(0, list.size())
    }
}
