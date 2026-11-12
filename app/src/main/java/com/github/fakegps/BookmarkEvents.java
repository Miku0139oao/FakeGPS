package com.github.fakegps;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process bookmark change notifications. Replaces the deprecated
 * LocalBroadcastManager without adding a dependency.
 */
public final class BookmarkEvents {

    public interface Listener {
        void onBookmarksChanged();
    }

    private static final CopyOnWriteArrayList<Listener> sListeners =
            new CopyOnWriteArrayList<>();

    private BookmarkEvents() {
    }

    public static void addListener(Listener listener) {
        if (listener != null) {
            sListeners.addIfAbsent(listener);
        }
    }

    public static void removeListener(Listener listener) {
        sListeners.remove(listener);
    }

    public static void notifyChanged() {
        for (Listener listener : sListeners) {
            listener.onBookmarksChanged();
        }
    }
}
