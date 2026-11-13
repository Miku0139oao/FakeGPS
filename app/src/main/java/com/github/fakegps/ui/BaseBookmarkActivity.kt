package com.github.fakegps.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.fakegps.BookmarkEvents
import com.github.fakegps.BookmarkRepository
import com.github.fakegps.model.LocBookmark
import com.github.fakegps.model.LocPoint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tencent.fakegps.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shared bookmark list and refresh handling for the main and flight screens.
 */
abstract class BaseBookmarkActivity : AppCompatActivity(),
    BookmarkEvents.Listener, BookmarkAdapter.Listener {

    protected var adapter: BookmarkAdapter? = null

    private var recyclerView: RecyclerView? = null
    private var emptyView: View? = null
    private var emptyHint: View? = null
    private var reloadJob: Job? = null

    protected fun initBookmarkList(list: RecyclerView) {
        recyclerView = list
        adapter = BookmarkAdapter(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        emptyView = findViewById(R.id.empty_view)
        emptyHint = findViewById(R.id.empty_hint)
        // onStart() loads the list; avoid a duplicate query during onCreate.
    }

    protected abstract fun onBookmarkSelected(locPoint: LocPoint?)

    protected fun reloadBookmarks() {
        val currentAdapter = adapter ?: return
        reloadJob?.cancel()
        reloadJob = lifecycleScope.launch {
            val bookmarks = BookmarkRepository.loadAll()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                currentAdapter.setItems(bookmarks)
                val empty = bookmarks.isEmpty()
                recyclerView?.visibility = if (empty) View.GONE else View.VISIBLE
                emptyView?.visibility = if (empty) View.VISIBLE else View.GONE
                emptyHint?.visibility = if (empty) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onBookmarkClick(bookmark: LocBookmark) {
        onBookmarkSelected(bookmark.locPoint)
    }

    override fun onBookmarkLongClick(bookmark: LocBookmark) {
        confirmDelete(bookmark)
    }

    private fun confirmDelete(bookmark: LocBookmark) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_bookmark_title) + " " + bookmark.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch { BookmarkRepository.delete(bookmark) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        BookmarkEvents.addListener(this)
        reloadBookmarks()
    }

    override fun onStop() {
        reloadJob?.cancel()
        BookmarkEvents.removeListener(this)
        super.onStop()
    }

    override fun onBookmarksChanged() {
        reloadBookmarks()
    }
}
