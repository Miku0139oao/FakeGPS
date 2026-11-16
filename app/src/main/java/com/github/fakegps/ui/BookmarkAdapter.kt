package com.github.fakegps.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.fakegps.BookmarkNames
import com.github.fakegps.model.BookmarkList
import com.github.fakegps.model.LocBookmark
import com.github.fakegps.databinding.ListItemBookmarkLayoutBinding

/**
 * Bookmark list adapter for the Material bookmark cards.
 */
class BookmarkAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<BookmarkAdapter.Holder>() {

    interface Listener {
        fun onBookmarkClick(bookmark: LocBookmark)
        fun onBookmarkLongClick(bookmark: LocBookmark)
        fun onBookmarkMenuClick(bookmark: LocBookmark, anchor: View)
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
        val coordinates = item.locPoint?.toString() ?: ""
        // Older bookmarks were all saved with the generic "Bookmark" name, so
        // show the coordinates as the title when the name carries no meaning.
        // The check covers every language the app ships, not just the one
        // selected right now.
        val genericName = BookmarkNames.isGenericName(holder.itemView.context, item)
        holder.binding.tvName.text = if (genericName && coordinates.isNotEmpty()) {
            coordinates
        } else {
            item.name ?: coordinates
        }
        holder.binding.tvLoc.text = coordinates
        holder.binding.tvLoc.visibility = if (genericName) View.GONE else View.VISIBLE
        holder.binding.bookmarkRow.setOnClickListener {
            listener.onBookmarkClick(item)
        }
        holder.binding.bookmarkRow.setOnLongClickListener {
            listener.onBookmarkLongClick(item)
            true
        }
        holder.binding.btnRowMenu.setOnClickListener { anchor ->
            listener.onBookmarkMenuClick(item, anchor)
        }
    }

    override fun getItemCount(): Int = items.size()

    class Holder(
        val binding: ListItemBookmarkLayoutBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
