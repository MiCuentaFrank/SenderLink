package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.senderlink.app.databinding.ItemCommentBinding
import com.senderlink.app.model.Comment

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.VH>() {

    private var items: List<Comment> = emptyList()

    inner class VH(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.binding.tvCommentUser.text = c.userName
        holder.binding.tvCommentText.text = c.text
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<Comment>) {
        items = newItems
        notifyDataSetChanged()
    }
}
