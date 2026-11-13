package com.github.fakegps.model

/**
 * Plain list state for the bookmark adapter, testable without RecyclerView.
 */
class BookmarkList {

    private val items = ArrayList<LocBookmark>()

    fun setItems(newItems: List<LocBookmark>?) {
        items.clear()
        if (newItems != null) {
            items.addAll(newItems)
        }
    }

    fun size(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    operator fun get(position: Int): LocBookmark = items[position]
}
