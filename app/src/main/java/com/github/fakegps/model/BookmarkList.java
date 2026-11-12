package com.github.fakegps.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain list state for the bookmark adapter, testable without RecyclerView.
 */
public final class BookmarkList {

    private final ArrayList<LocBookmark> mItems = new ArrayList<>();

    public void setItems(List<LocBookmark> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
    }

    public int size() {
        return mItems.size();
    }

    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    public LocBookmark get(int position) {
        return mItems.get(position);
    }
}
