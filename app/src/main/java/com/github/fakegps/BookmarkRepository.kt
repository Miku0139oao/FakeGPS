package com.github.fakegps

import com.github.fakegps.model.LocBookmark
import com.github.fakegps.model.LocPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Serializes lite-orm access on a background dispatcher.
 *
 * <p>Reads run on [Dispatchers.IO] so they never touch the main thread. Writes
 * use [NonCancellable] because the Activity that triggered them may finish
 * before the insert/delete completes; dropping the write would lose data.</p>
 */
object BookmarkRepository {

    /** Result of [savePoint]: an existing row was found at the same location. */
    const val DUPLICATE = 0L

    private val dbMutex = Mutex()

    suspend fun loadAll(): List<LocBookmark> = withContext(Dispatchers.IO) {
        dbMutex.withLock { DbUtils.getAllBookmark() }
    }

    suspend fun insert(bookmark: LocBookmark): Long =
        withContext(Dispatchers.IO + NonCancellable) {
            dbMutex.withLock { DbUtils.insertBookmark(bookmark) }
        }

    /**
     * Saves a location bookmark in one step, skipping locations that are
     * already bookmarked.
     *
     * @return the new row id, [DUPLICATE] when the location is already saved,
     *         or {@code -1} when the insert failed.
     */
    suspend fun savePoint(locPoint: LocPoint, name: String): Long =
        withContext(Dispatchers.IO + NonCancellable) {
            dbMutex.withLock {
                val alreadySaved = DbUtils.getAllBookmark().any { bookmark ->
                    val point = bookmark.locPoint
                    point != null &&
                        abs(point.latitude - locPoint.latitude) < COORDINATE_EPSILON &&
                        abs(point.longitude - locPoint.longitude) < COORDINATE_EPSILON
                }
                if (alreadySaved) {
                    DUPLICATE
                } else {
                    DbUtils.insertBookmark(LocBookmark(name, locPoint))
                }
            }
        }

    suspend fun delete(bookmark: LocBookmark) {
        withContext(Dispatchers.IO + NonCancellable) {
            dbMutex.withLock { DbUtils.deleteBookmark(bookmark) }
        }
    }

    /** @return whether the renamed bookmark was actually written to the database. */
    suspend fun update(bookmark: LocBookmark, newName: String): Boolean =
        withContext(Dispatchers.IO + NonCancellable) {
            dbMutex.withLock {
                val previousName = bookmark.name
                bookmark.name = newName
                val updated = DbUtils.updateBookmark(bookmark)
                if (!updated) {
                    // Keep the in-memory row consistent with the database so a
                    // failed write is not shown as if it had succeeded.
                    bookmark.name = previousName
                }
                updated
            }
        }

    /** Two coordinates closer than this are the same place (about 10 cm). */
    private const val COORDINATE_EPSILON = 1e-6
}
