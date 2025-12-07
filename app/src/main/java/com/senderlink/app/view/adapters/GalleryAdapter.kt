package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.databinding.ItemGalleryBinding

class GalleryAdapter(
    private val imagenes: List<String>,
    private val onImageClick: (String) -> Unit   // ← NUEVO
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    inner class GalleryViewHolder(val binding: ItemGalleryBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val url = imagenes[position]

        Glide.with(holder.itemView.context)
            .load(url)
            .into(holder.binding.imgGaleria)

        holder.itemView.setOnClickListener {
            onImageClick(url)   // ← LE AVISAMOS AL FRAGMENT
        }
    }

    override fun getItemCount() = imagenes.size
}

