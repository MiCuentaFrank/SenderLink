package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.databinding.ItemGroupMessageMineBinding
import com.senderlink.app.databinding.ItemGroupMessageOtherBinding
import com.senderlink.app.model.GroupMessage

class GroupMessageAdapter(
    private val currentUidProvider: () -> String?
) : ListAdapter<GroupMessage, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val TYPE_MINE = 1
        private const val TYPE_OTHER = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<GroupMessage>() {
            override fun areItemsTheSame(oldItem: GroupMessage, newItem: GroupMessage): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: GroupMessage, newItem: GroupMessage): Boolean {
                return oldItem == newItem
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        val uid = currentUidProvider()
        return if (!uid.isNullOrBlank() && msg.isMine(uid)) TYPE_MINE else TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_MINE -> {
                val binding = ItemGroupMessageMineBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                MineVH(binding)
            }
            else -> {
                val binding = ItemGroupMessageOtherBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                OtherVH(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)

        when (holder) {
            is MineVH -> holder.bind(msg)
            is OtherVH -> holder.bind(msg)
        }
    }

    inner class MineVH(private val binding: ItemGroupMessageMineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: GroupMessage) {
            binding.apply {
                tvMessage.text = msg.text
                tvTime.text = msg.getFormattedTime()
            }
        }
    }

    inner class OtherVH(private val binding: ItemGroupMessageOtherBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: GroupMessage) {
            binding.apply {
                tvName.text = msg.senderName
                tvMessage.text = msg.text
                tvTime.text = msg.getFormattedTime()

                // Avatar (si viene foto, la cargamos; si no, placeholder)
                if (msg.hasSenderPhoto()) {
                    Glide.with(itemView.context)
                        .load(msg.senderPhoto)
                        .circleCrop()
                        .into(imgAvatar)
                } else {
                    // Placeholder simple (tu app ya usa android default icons)
                    imgAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                }
            }
        }
    }
}
