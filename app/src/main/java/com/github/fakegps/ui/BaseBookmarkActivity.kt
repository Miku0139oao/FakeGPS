package com.github.fakegps.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.fakegps.BookmarkEvents
import com.github.fakegps.BookmarkNames
import com.github.fakegps.BookmarkRepository
import com.github.fakegps.model.LocBookmark
import com.github.fakegps.model.LocPoint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.fakegps.R
import com.github.fakegps.databinding.DialogRenameBinding
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
    private var emptyState: View? = null
    private var countView: TextView? = null
    private var reloadJob: Job? = null

    protected fun initBookmarkList(list: RecyclerView) {
        recyclerView = list
        adapter = BookmarkAdapter(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        emptyView = findViewById(R.id.empty_view)
        emptyHint = findViewById(R.id.empty_hint)
        emptyState = findViewById(R.id.empty_state)
        countView = findViewById(R.id.tv_bookmark_count)
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
                emptyState?.visibility = if (empty) View.VISIBLE else View.GONE
                countView?.visibility = if (empty) View.GONE else View.VISIBLE
                countView?.text = bookmarks.size.toString()
            }
        }
    }

    override fun onBookmarkClick(bookmark: LocBookmark) {
        onBookmarkSelected(bookmark.locPoint)
    }

    override fun onBookmarkLongClick(bookmark: LocBookmark) {
        confirmDelete(bookmark)
    }

    override fun onBookmarkMenuClick(bookmark: LocBookmark, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_RENAME, 0, R.string.bookmark_rename)
            menu.add(0, MENU_DELETE, 1, R.string.delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RENAME -> {
                        showRenameDialog(bookmark)
                        true
                    }
                    MENU_DELETE -> {
                        confirmDelete(bookmark)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showRenameDialog(bookmark: LocBookmark) {
        val dialogBinding = DialogRenameBinding.inflate(layoutInflater)
        val currentName = BookmarkNames.displayName(this, bookmark)
        dialogBinding.inputName.setText(currentName)
        dialogBinding.inputName.setSelection(currentName.length)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_bookmark_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            // Keep the dialog open on invalid input instead of dismissing it
            // and throwing the typed name away.
            saveButton.isEnabled = !dialogBinding.inputName.text.isNullOrBlank()
            dialogBinding.inputName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    saveButton.isEnabled = !s.isNullOrBlank()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
            saveButton.setOnClickListener {
                val newName = dialogBinding.inputName.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty()) {
                    Toast.makeText(
                        this@BaseBookmarkActivity,
                        R.string.bookmark_name_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                saveButton.isEnabled = false
                lifecycleScope.launch {
                    val updated = BookmarkRepository.update(bookmark, newName)
                    dialog.dismiss()
                    Toast.makeText(
                        this@BaseBookmarkActivity,
                        if (updated) R.string.bookmark_renamed else R.string.bookmark_rename_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    if (!updated) {
                        reloadBookmarks()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(bookmark: LocBookmark) {
        MaterialAlertDialogBuilder(this)
            .setTitle(
                getString(R.string.delete_bookmark_title) + " " +
                    BookmarkNames.displayName(this, bookmark)
            )
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

    private companion object {
        const val MENU_RENAME = 1
        const val MENU_DELETE = 2
    }
}
