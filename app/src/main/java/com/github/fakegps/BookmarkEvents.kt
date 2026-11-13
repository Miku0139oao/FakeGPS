package com.github.fakegps

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process bookmark change notifications. Replaces the deprecated
 * LocalBroadcastManager without adding a dependency.
 *
 * <p>Listeners are Activities, so notifications are always delivered on the
 * main thread even when a write triggered them from a background dispatcher.</p>
 */
object BookmarkEvents {

    fun interface Listener {
        fun onBookmarksChanged()
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyNow()
        } else {
            mainHandler.post { notifyNow() }
        }
    }

    private fun notifyNow() {
        for (listener in listeners) {
            listener.onBookmarksChanged()
        }
    }
}
