package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemRouteBinding
import com.senderlink.app.model.Route
import com.senderlink.app.utils.DifficultyMapper

class RouteAdapter(
    private val onClick: (Route) -> Unit
) : ListAdapter<Route, RouteAdapter.RouteViewHolder>(DIFF_CALLBACK) {

    inner class RouteViewHolder(val binding: ItemRouteBinding) :
        RecyclerView.ViewHolder(binding.root)

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val stableKey = getItem(position).id
        return stableKey.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRouteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = getItem(position)

        // ✅ Normalizamos una vez y reutilizamos
        val normalized = route.getNormalizedDifficulty()
        val difficultyUi = normalizedToUi(normalized)

        holder.binding.apply {
            tvRouteName.text = route.name

            // ✅ UI bonita y consistente
            tvDificultadRuta.text = difficultyUi
            chipDifficultyBadge.text = difficultyUi

            // (Opcional) si quieres más fino: String.format("%.1f km", route.distanceKm)
            tvDistanciaRuta.text = "${route.distanceKm} km"
            tvDistanceOverlay.text = "${route.distanceKm} km"

            // ✅ Color del chip según NORMALIZED (no el crudo)
            val chipColor = when (normalized) {
                DifficultyMapper.FACIL -> R.color.difficulty_easy
                DifficultyMapper.MODERADA -> R.color.difficulty_moderate
                DifficultyMapper.DIFICIL -> R.color.difficulty_hard
                else -> R.color.sl_primary
            }
            chipDifficultyBadge.setChipBackgroundColorResource(chipColor)

            Glide.with(holder.itemView.context)
                .load(route.coverImage)
                .placeholder(R.drawable.placeholder_route)
                .error(R.drawable.placeholder_route)
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
