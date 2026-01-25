package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemPostBinding
import com.senderlink.app.model.Post

class PostAdapter(
    private val onLike: (Post) -> Unit,
    private val onComments: (Post) -> Unit
) : RecyclerView.Adapter<PostAdapter.VH>() {

    private var items: List<Post> = emptyList()

    // ✅ Foto “fresca” del usuario actual (para refrescar cards al cambiar avatar)
    private var currentUserPhotoUrl: String? = null

    inner class VH(val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val post = items[position]
        val b = holder.binding

        b.tvUser.text = post.userName
        b.tvMeta.text = "Publicado" // luego lo refinamos con fecha relativa
        b.tvText.text = post.text

        // =========================
        // ✅ Avatar (userPhoto)
        // =========================
        // Si hemos actualizado la foto del usuario actual recientemente,
        // la usamos para refrescar las cards sin depender del backend.
        val avatarUrl = (currentUserPhotoUrl ?: post.userPhoto)?.trim().orEmpty()

        if (avatarUrl.isNotBlank()) {
            Glide.with(b.imgAvatar)
                .load(avatarUrl)
                // ✅ anti-cache: si cambia la URL, fuerza recarga
                .signature(ObjectKey(avatarUrl))
                .placeholder(R.drawable.perfilsenderista)
                .error(R.drawable.perfilsenderista)
                .circleCrop()
                .into(b.imgAvatar)
        } else {
            b.imgAvatar.setImageResource(R.drawable.perfilsenderista)
        }

        // =========================
        // ✅ Imagen del post (image)
        // =========================
        val imageUrl = post.image?.trim().orEmpty()
        if (imageUrl.isNotBlank()) {
            b.imgPost.visibility = View.VISIBLE
            Glide.with(b.imgPost)
                .load(imageUrl)
                .signature(ObjectKey(imageUrl))
                .placeholder(R.drawable.rutas1)
                .error(R.drawable.rutas1)
                .centerCrop()
                .into(b.imgPost)
        } else {
            b.imgPost.visibility = View.GONE
        }

        b.btnLike.text = "Me gusta (${post.likesCount})"
        b.tvCommentsCount.text = "${post.commentsCount} comentarios"

        b.btnLike.setOnClickListener { onLike(post) }
        b.tvCommentsCount.setOnClickListener { onComments(post) }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<Post>) {
        items = newItems
        notifyDataSetChanged()
    }

    /**
     * ✅ Llamar cuando el usuario cambie su foto.
     * Esto refresca el avatar de todas las cards.
     */
    fun setCurrentUserPhotoUrl(url: String?) {
        currentUserPhotoUrl = url?.trim()
        notifyDataSetChanged()
    }
}
