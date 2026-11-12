package com.github.fakegps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;

public class BookmarkListTest {

    @Test
    public void settingEmptyList_clearsItems() {
        BookmarkList list = new BookmarkList();
        ArrayList<LocBookmark> items = new ArrayList<>();
        items.add(new LocBookmark("home", new LocPoint(1.0, 2.0)));
        list.setItems(items);
        assertEquals(1, list.size());

        list.setItems(new ArrayList<LocBookmark>());
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
    }

    @Test
    public void settingNullList_clearsItems() {
        BookmarkList list = new BookmarkList();
        ArrayList<LocBookmark> items = new ArrayList<>();
        items.add(new LocBookmark("home", new LocPoint(1.0, 2.0)));
        list.setItems(items);

        list.setItems(null);
        assertEquals(0, list.size());
    }
}
