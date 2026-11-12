package com.github.fakegps.ui;

import static org.junit.Assert.assertEquals;

import com.github.fakegps.model.LocBookmark;
import com.github.fakegps.model.LocPoint;

import org.junit.Test;

import java.util.ArrayList;

public class BookmarkAdapterTest {

    @Test
    public void settingEmptyList_clearsExistingItems() {
        BookmarkAdapter adapter = new BookmarkAdapter(null);
        ArrayList<LocBookmark> items = new ArrayList<>();
        items.add(new LocBookmark("home", new LocPoint(1.0, 2.0)));
        adapter.setLocBookmarkList(items);
        assertEquals(1, adapter.getCount());

        adapter.setLocBookmarkList(new ArrayList<LocBookmark>());
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void settingNullList_clearsExistingItems() {
        BookmarkAdapter adapter = new BookmarkAdapter(null);
        ArrayList<LocBookmark> items = new ArrayList<>();
        items.add(new LocBookmark("home", new LocPoint(1.0, 2.0)));
        adapter.setLocBookmarkList(items);

        adapter.setLocBookmarkList(null);
        assertEquals(0, adapter.getCount());
    }
}
