package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemGroupMessageMineBinding
import com.senderlink.app.databinding.ItemGroupMessageOtherBinding
import com.senderlink.app.model.DirectMessage

class DirectMessageAdapter(
    private val currentUidProvider: () -> String?,
    private val otherFoto: String? = null
) : ListAdapter<DirectMessage, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_MINE = 1
        private const val TYPE_OTHER = 2

        private val DIFF = object : DiffUtil.ItemCallback<DirectMessage>() {
            override fun areItemsTheSame(a: DirectMessage, b: DirectMessage) = a.id == b.id
            override fun areContentsTheSame(a: DirectMessage, b: DirectMessage) = a == b
        }

        private fun formatTime(iso: String?): String = try {
            iso?.substring(11, 16) ?: ""
        } catch (_: Exception) { "" }
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        val uid = currentUidProvider()
        return if (!uid.isNullOrBlank() && msg.remitenteUid == uid) TYPE_MINE else TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            TYPE_MINE -> MineVH(ItemGroupMessageMineBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else      -> OtherVH(ItemGroupMessageOtherBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is MineVH  -> holder.bind(msg)
            is OtherVH -> holder.bind(msg)
        }
    }

    inner class MineVH(private val b: ItemGroupMessageMineBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: DirectMessage) {
            b.tvMessage.text = msg.texto
            b.tvTime.text = formatTime(msg.createdAt)
        }
    }

    inner class OtherVH(private val b: ItemGroupMessageOtherBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: DirectMessage) {
            b.tvName.text = ""
            b.tvMessage.text = msg.texto
            b.tvTime.text = formatTime(msg.createdAt)
            if (!otherFoto.isNullOrBlank()) {
                Glide.with(b.imgAvatar)
                    .load(otherFoto)
                    .placeholder(R.drawable.perfilsenderista)
                    .error(R.drawable.perfilsenderista)
                    .circleCrop()
                    .into(b.imgAvatar)
            } else {
                b.imgAvatar.setImageResource(R.drawable.perfilsenderista)
            }
        }
    }
}
