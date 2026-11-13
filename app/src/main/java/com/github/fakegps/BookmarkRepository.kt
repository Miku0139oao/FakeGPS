package com.github.fakegps

import com.github.fakegps.model.LocBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes lite-orm access on a background dispatcher.
 *
 * <p>Reads run on [Dispatchers.IO] so they never touch the main thread. Writes
 * use [NonCancellable] because the Activity that triggered them may finish
 * before the insert/delete completes; dropping the write would lose data.</p>
 */
object BookmarkRepository {

    private val dbMutex = Mutex()

    suspend fun loadAll(): List<LocBookmark> = withContext(Dispatchers.IO) {
        dbMutex.withLock { DbUtils.getAllBookmark() }
    }

    suspend fun insert(bookmark: LocBookmark): Long =
        withContext(Dispatchers.IO + NonCancellable) {
            dbMutex.withLock { DbUtils.insertBookmark(bookmark) }
        }

    suspend fun delete(bookmark: LocBookmark) {
        withContext(Dispatchers.IO + NonCancellable) {
            dbMutex.withLock { DbUtils.deleteBookmark(bookmark) }
        }
    }
}
