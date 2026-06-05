package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemPostBinding
import com.senderlink.app.model.Post

class PostAdapter(
    private val onLike: (Post) -> Unit,
    private val onComments: (Post) -> Unit,
    private val onDelete: ((Post) -> Unit)? = null
) : RecyclerView.Adapter<PostAdapter.VH>() {

    private var items: List<Post> = emptyList()

    // Foto “fresca” del usuario actual (para refrescar cards al cambiar avatar)
    private var currentUserPhotoUrl: String? = null
    private var currentUserUid: String? = null

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
        // Avatar (userPhoto)
        // =========================
        // Solo usar la foto fresca si el post pertenece al usuario actual
        val avatarUrl = if (currentUserUid != null && post.uid == currentUserUid) {
            (currentUserPhotoUrl ?: post.userPhoto)?.trim().orEmpty()
        } else {
            post.userPhoto?.trim().orEmpty()
        }

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
                .fitCenter()
                .into(b.imgPost)
        } else {
            b.imgPost.visibility = View.GONE
        }

        b.btnLike.text = "Me gusta (${post.likesCount})"
        b.tvCommentsCount.text = "${post.commentsCount} comentarios"

        b.btnLike.setOnClickListener { onLike(post) }
        b.tvCommentsCount.setOnClickListener { onComments(post) }

        if (onDelete != null) {
            b.btnDelete.visibility = View.VISIBLE
            b.btnDelete.setOnClickListener { onDelete.invoke(post) }
        } else {
            b.btnDelete.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<Post>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].id == newItems[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        })
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Llamar cuando el usuario cambie su foto.
     * Solo refresca el avatar de los posts del usuario actual.
     */
    fun setCurrentUserPhotoUrl(url: String?, uid: String? = null) {
        currentUserPhotoUrl = url?.trim()
        if (uid != null) currentUserUid = uid
        // Solo rebindear los posts del usuario actual, no toda la lista
        items.forEachIndexed { index, post ->
            if (post.uid == currentUserUid) notifyItemChanged(index)
        }
    }
}
