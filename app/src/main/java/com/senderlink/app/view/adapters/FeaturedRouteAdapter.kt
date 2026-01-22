package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.databinding.ItemFeaturedRouteBinding
import com.senderlink.app.model.Route
import com.senderlink.app.utils.DifficultyMapper

/**
 * ⭐ FeaturedRouteAdapter - OPTIMIZADO
 *
 * MEJORAS:
 * - ✅ Usa ListAdapter con DiffUtil (más eficiente que notifyDataSetChanged)
 * - ✅ Animaciones suaves al actualizar lista
 * - ✅ Solo actualiza items que cambiaron
 */
class FeaturedRouteAdapter(
    private val onClick: (Route) -> Unit
) : ListAdapter<Route, FeaturedRouteAdapter.FeaturedRouteViewHolder>(DIFF_CALLBACK) {

    inner class FeaturedRouteViewHolder(
        val binding: ItemFeaturedRouteBinding
    ) : RecyclerView.ViewHolder(binding.root)

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeaturedRouteViewHolder {
        val binding = ItemFeaturedRouteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeaturedRouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeaturedRouteViewHolder, position: Int) {
        val route = getItem(position)

        val normalized = route.getNormalizedDifficulty()
        val difficultyUi = normalizedToUi(normalized)

        holder.binding.apply {
            txtRouteName.text = route.name
            txtRouteInfo.text = "${route.distanceKm} km · $difficultyUi"

            tvBadge?.visibility = View.VISIBLE
            tvBadge?.text = "DESTACADA"

            // ✅ Glide optimizado
            Glide.with(holder.itemView.context)
                .load(route.coverImage)
                .centerCrop()
                .into(imgRoute)

            root.setOnClickListener { onClick(route) }
        }
    }

    private fun normalizedToUi(normalized: String): String {
        return when (normalized) {
            DifficultyMapper.FACIL -> "Fácil"
            DifficultyMapper.MODERADA -> "Media"
            DifficultyMapper.DIFICIL -> "Difícil"
            else -> "Media"
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Route>() {
            override fun areItemsTheSame(oldItem: Route, newItem: Route): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Route, newItem: Route): Boolean {
                return oldItem == newItem
            }
        }
    }
}