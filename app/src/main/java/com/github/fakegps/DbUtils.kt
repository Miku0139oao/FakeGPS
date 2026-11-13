package com.github.fakegps

import android.content.Context
import com.github.fakegps.model.LocBookmark
import com.github.fakegps.model.LocPoint
import com.litesuits.orm.db.model.ConflictAlgorithm
import tiger.radio.loggerlibrary.Logger

/**
 * Bookmark persistence facade over lite-orm, plus the last-location preference.
 * The database file name, table name and preference keys are part of the
 * upgrade contract and must not change.
 */
object DbUtils {

    private const val TAG = "DbUtils"
    private const val SHARED_PREF_NAME = "FakeGPS"
    private const val KEY_LAST_LOC = "last_loc"

    @JvmStatic
    fun insertBookmark(bookmark: LocBookmark?): Long {
        if (bookmark == null) {
            return -1
        }
        return try {
            val id = FakeGpsApp.getLiteOrm().insert(bookmark, ConflictAlgorithm.Replace)
            if (id != -1L) {
                notifyBookmarkUpdate()
            }
            id
        } catch (e: RuntimeException) {
            Logger.e(TAG, "Unable to insert bookmark", e)
            -1
        }
    }

    @JvmStatic
    fun deleteBookmark(bookmark: LocBookmark?) {
        if (bookmark == null) return
        try {
            FakeGpsApp.getLiteOrm().delete(bookmark)
            notifyBookmarkUpdate()
        } catch (e: RuntimeException) {
            Logger.e(TAG, "Unable to delete bookmark", e)
        }
    }

    @JvmStatic
    fun getAllBookmark(): ArrayList<LocBookmark> =
        try {
            FakeGpsApp.getLiteOrm().query(LocBookmark::class.java) ?: ArrayList()
        } catch (e: RuntimeException) {
            Logger.e(TAG, "Unable to load bookmarks", e)
            ArrayList()
        }

    @JvmStatic
    fun saveLastLocPoint(context: Context, locPoint: LocPoint) {
        context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_LOC, locPoint.toString())
            .apply()
    }

    @JvmStatic
    fun getLastLocPoint(context: Context): String =
        context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_LOC, "") ?: ""

    @JvmStatic
    fun notifyBookmarkUpdate() {
        BookmarkEvents.notifyChanged()
    }
}
