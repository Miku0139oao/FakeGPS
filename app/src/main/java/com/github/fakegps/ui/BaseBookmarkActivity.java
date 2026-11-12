package com.github.fakegps.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.fakegps.BookmarkEvents;
import com.github.fakegps.DbUtils;
import com.github.fakegps.model.LocBookmark;
import com.github.fakegps.model.LocPoint;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tencent.fakegps.R;

import java.util.List;

/**
 * Shared bookmark list and refresh handling for the main and flight screens.
 */
public abstract class BaseBookmarkActivity extends AppCompatActivity
        implements BookmarkEvents.Listener, BookmarkAdapter.Listener {

    protected BookmarkAdapter mAdapter;

    private RecyclerView mRecyclerView;
    private View mEmptyView;
    private View mEmptyHint;

    protected void initBookmarkList(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        mAdapter = new BookmarkAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);
        mEmptyView = findViewById(R.id.empty_view);
        mEmptyHint = findViewById(R.id.empty_hint);
        // onStart() loads the list; avoid a duplicate query during onCreate.
    }

    protected abstract void onBookmarkSelected(LocPoint locPoint);

    protected void reloadBookmarks() {
        if (mAdapter == null) {
            return;
        }
        List<LocBookmark> bookmarks = DbUtils.getAllBookmark();
        mAdapter.setItems(bookmarks);
        boolean empty = bookmarks.isEmpty();
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        if (mEmptyView != null) {
            mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (mEmptyHint != null) {
            mEmptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onBookmarkClick(LocBookmark bookmark) {
        onBookmarkSelected(bookmark.getLocPoint());
    }

    @Override
    public void onBookmarkLongClick(LocBookmark bookmark) {
        confirmDelete(bookmark);
    }

    private void confirmDelete(final LocBookmark bookmark) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_bookmark_title) + " " + bookmark.getName())
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        DbUtils.deleteBookmark(bookmark))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        BookmarkEvents.addListener(this);
        reloadBookmarks();
    }

    @Override
    protected void onStop() {
        BookmarkEvents.removeListener(this);
        super.onStop();
    }

    @Override
    public void onBookmarksChanged() {
        reloadBookmarks();
    }
}
