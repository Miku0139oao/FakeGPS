package com.github.fakegps.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.fakegps.model.BookmarkList;
import com.github.fakegps.model.LocBookmark;
import com.tencent.fakegps.R;

import java.util.List;

/**
 * Bookmark list adapter for the Material bookmark cards.
 */
public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.Holder> {

    public interface Listener {
        void onBookmarkClick(LocBookmark bookmark);

        void onBookmarkLongClick(LocBookmark bookmark);
    }

    private final BookmarkList mItems = new BookmarkList();
    private final Listener mListener;

    public BookmarkAdapter(Listener listener) {
        mListener = listener;
    }

    public void setItems(List<LocBookmark> items) {
        mItems.setItems(items);
        notifyDataSetChanged();
    }

    public LocBookmark getItem(int position) {
        return mItems.get(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_bookmark_layout, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final LocBookmark item = getItem(position);
        holder.name.setText(item.getName());
        holder.loc.setText(item.getLocPoint() != null ? item.getLocPoint().toString() : "");
        holder.itemView.setOnClickListener(view -> {
            if (mListener != null) {
                mListener.onBookmarkClick(item);
            }
        });
        holder.itemView.setOnLongClickListener(view -> {
            if (mListener != null) {
                mListener.onBookmarkLongClick(item);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView loc;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_name);
            loc = itemView.findViewById(R.id.tv_loc);
        }
    }
}
