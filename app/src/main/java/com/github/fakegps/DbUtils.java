package com.github.fakegps;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.fakegps.model.LocBookmark;
import com.github.fakegps.model.LocPoint;
import com.litesuits.orm.db.model.ConflictAlgorithm;

import java.util.ArrayList;

import tiger.radio.loggerlibrary.Logger;

/**
 * Created by tiger on 7/23/16.
 */
public final class DbUtils {

    private static final String TAG = "DbUtils";
    private static final String SHARED_PREF_NAME = "FakeGPS";
    private static final String KEY_LAST_LOC = "last_loc";

    private DbUtils() {
    }


    public static long insertBookmark(LocBookmark bookmark) {
        if (bookmark == null) {
            return -1;
        }
        try {
            long id = FakeGpsApp.getLiteOrm().insert(bookmark, ConflictAlgorithm.Replace);
            if (id != -1) {
                notifyBookmarkUpdate();
            }
            return id;
        } catch (RuntimeException e) {
            Logger.e(TAG, "Unable to insert bookmark", e);
            return -1;
        }
    }

    public static void deleteBookmark(LocBookmark bookmark) {
        if (bookmark == null) return;
        try {
            FakeGpsApp.getLiteOrm().delete(bookmark);
            notifyBookmarkUpdate();
        } catch (RuntimeException e) {
            Logger.e(TAG, "Unable to delete bookmark", e);
        }
    }

    public static ArrayList<LocBookmark> getAllBookmark() {
        try {
            ArrayList<LocBookmark> bookmarks = FakeGpsApp.getLiteOrm().query(LocBookmark.class);
            return bookmarks == null ? new ArrayList<LocBookmark>() : bookmarks;
        } catch (RuntimeException e) {
            Logger.e(TAG, "Unable to load bookmarks", e);
            return new ArrayList<>();
        }
    }

    public static void saveLastLocPoint(@NonNull Context context, @NonNull LocPoint locPoint) {
        context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_LOC, locPoint.toString())
                .apply();
    }

    public static String getLastLocPoint(@NonNull Context context) {
        return context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_LOC, "");
    }

    public static void notifyBookmarkUpdate() {
        BookmarkEvents.notifyChanged();
    }

}
