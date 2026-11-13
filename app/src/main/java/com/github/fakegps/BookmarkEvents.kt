package com.github.fakegps

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process bookmark change notifications. Replaces the deprecated
 * LocalBroadcastManager without adding a dependency.
 */
object BookmarkEvents {

    fun interface Listener {
        fun onBookmarksChanged()
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    @JvmStatic
    fun addListener(listener: Listener?) {
        if (listener != null) {
            listeners.addIfAbsent(listener)
        }
    }

    @JvmStatic
    fun removeListener(listener: Listener?) {
        listeners.remove(listener)
    }

    @JvmStatic
    fun notifyChanged() {
        for (listener in listeners) {
            listener.onBookmarksChanged()
        }
    }
}
