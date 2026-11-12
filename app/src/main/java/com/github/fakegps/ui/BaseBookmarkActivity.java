package com.github.fakegps.ui;

import android.content.DialogInterface;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.fakegps.BookmarkEvents;
import com.github.fakegps.DbUtils;
import com.github.fakegps.model.LocBookmark;
import com.github.fakegps.model.LocPoint;
import com.tencent.fakegps.R;

/**
 * Shared bookmark list, context menu, and refresh handling for the main and
 * flight screens.
 */
public abstract class BaseBookmarkActivity extends AppCompatActivity
        implements BookmarkEvents.Listener {

    protected static final int DELETE_ID = 1001;

    protected BookmarkAdapter mAdapter;

    protected void initBookmarkList(ListView listView) {
        mAdapter = new BookmarkAdapter(this);
        mAdapter.setLocBookmarkList(DbUtils.getAllBookmark());
        listView.setAdapter(mAdapter);

        View emptyView = findViewById(R.id.empty_view);
        if (emptyView != null) {
            listView.setEmptyView(emptyView);
        }

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                LocPoint locPoint = mAdapter.getItem(position).getLocPoint();
                onBookmarkSelected(locPoint);
            }
        });

        registerForContextMenu(listView);
    }

    /**
     * Called when the user taps a bookmark in the list.
     */
    protected abstract void onBookmarkSelected(LocPoint locPoint);

    protected void reloadBookmarks() {
        if (mAdapter != null) {
            mAdapter.setLocBookmarkList(DbUtils.getAllBookmark());
        }
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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, DELETE_ID, Menu.NONE, R.string.menu_delete);
        super.onCreateContextMenu(menu, view, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == DELETE_ID) {
            AdapterView.AdapterContextMenuInfo info =
                    (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            confirmDelete(info.position);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void confirmDelete(final int position) {
        if (position < 0 || mAdapter == null || position >= mAdapter.getCount()) {
            return;
        }
        final LocBookmark bookmark = mAdapter.getItem(position);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_bookmark_title) + " " + bookmark.getName())
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DbUtils.deleteBookmark(bookmark);
                        reloadBookmarks();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
