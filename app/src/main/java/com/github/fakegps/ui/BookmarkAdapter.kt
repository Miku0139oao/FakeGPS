package com.github.fakegps.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.fakegps.model.BookmarkList
import com.github.fakegps.model.LocBookmark
import com.tencent.fakegps.databinding.ListItemBookmarkLayoutBinding

/**
 * Bookmark list adapter for the Material bookmark cards.
 */
class BookmarkAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<BookmarkAdapter.Holder>() {

    interface Listener {
        fun onBookmarkClick(bookmark: LocBookmark)
        fun onBookmarkLongClick(bookmark: LocBookmark)
    }

    private val items = BookmarkList()

    fun setItems(newItems: List<LocBookmark>?) {
        items.setItems(newItems)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): LocBookmark = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ListItemBookmarkLayoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.name
        holder.binding.tvLoc.text = item.locPoint?.toString() ?: ""
        holder.binding.root.setOnClickListener {
            listener.onBookmarkClick(item)
        }
        holder.binding.root.setOnLongClickListener {
            listener.onBookmarkLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size()

    class Holder(
        val binding: ListItemBookmarkLayoutBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
