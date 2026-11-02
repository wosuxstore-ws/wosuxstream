package com.freetv.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.freetv.app.databinding.ItemChannelBinding

private val CHANNEL_DIFF = object : DiffUtil.ItemCallback<Channel>() {
    override fun areItemsTheSame(oldItem: Channel, newItem: Channel): Boolean = oldItem.url == newItem.url
    override fun areContentsTheSame(oldItem: Channel, newItem: Channel): Boolean = oldItem == newItem
}

class ChannelAdapter(
    private val isFavorite: (Channel) -> Boolean,
    private val onClick: (Channel, Int) -> Unit,
    private val onLongClick: (Channel, Int) -> Unit
) : ListAdapter<Channel, ChannelAdapter.VH>(CHANNEL_DIFF) {

    class VH(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        val root = vh.b.root

        root.setOnClickListener {
            val p = vh.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(getItem(p), p)
        }
        // Long-press (or hold OK on a TV remote) = add / remove favorite
        root.setOnLongClickListener {
            val p = vh.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick(getItem(p), p)
            true
        }
        // Grow the card a little when the remote focuses it
        root.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1f
            v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 12f else 0f).setDuration(120).start()
        }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        holder.b.name.text = c.name
        holder.b.star.isVisible = isFavorite(c)

        val logo = c.logo
        if (logo.isNullOrBlank()) {
            holder.b.logo.setImageResource(R.drawable.ic_tv)
        } else {
            holder.b.logo.load(logo) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
            }
        }
    }
}
