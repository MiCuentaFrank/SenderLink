package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemConversationBinding
import com.senderlink.app.model.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation) =
                oldItem.chatId == newItem.chatId
            override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation) =
                oldItem == newItem
        }

        private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        private fun formatTime(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            return try { sdf.format(Date(iso)) } catch (_: Exception) { "" }
        }
    }

    inner class VH(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conv = getItem(position)
        val b = holder.binding

        b.tvNombre.text = conv.otherNombre
        b.tvLastMessage.text = conv.lastMessage
        b.tvTime.text = formatTime(conv.lastMessageAt)

        if (conv.otherFoto.isNotBlank()) {
            Glide.with(b.imgAvatar)
                .load(conv.otherFoto)
                .placeholder(R.drawable.bg_avatar_circle)
                .error(R.drawable.bg_avatar_circle)
                .into(b.imgAvatar)
        } else {
            b.imgAvatar.setImageResource(R.drawable.bg_avatar_circle)
        }

        b.root.setOnClickListener { onClick(conv) }
    }
}
