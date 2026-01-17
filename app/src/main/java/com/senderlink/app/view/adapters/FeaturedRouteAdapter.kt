package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.databinding.ItemFeaturedRouteBinding
import com.senderlink.app.model.Route
import com.senderlink.app.utils.DifficultyMapper

class FeaturedRouteAdapter(
    private val onClick: (Route) -> Unit
) : RecyclerView.Adapter<FeaturedRouteAdapter.FeaturedRouteViewHolder>() {

    private var routes: List<Route> = emptyList()

    inner class FeaturedRouteViewHolder(
        val binding: ItemFeaturedRouteBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeaturedRouteViewHolder {
        val binding = ItemFeaturedRouteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeaturedRouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeaturedRouteViewHolder, position: Int) {
        val route = routes[position]

        val normalized = route.getNormalizedDifficulty()
        val difficultyUi = normalizedToUi(normalized)

        holder.binding.txtRouteName.text = route.name
        holder.binding.txtRouteInfo.text = "${route.distanceKm} km · $difficultyUi"

        holder.binding.tvBadge?.visibility = View.VISIBLE
        holder.binding.tvBadge?.text = "DESTACADA"

        Glide.with(holder.itemView.context)
            .load(route.coverImage)
            .centerCrop()
            .into(holder.binding.imgRoute)

        holder.itemView.setOnClickListener { onClick(route) }
    }

    override fun getItemCount(): Int = routes.size

    fun submitList(newList: List<Route>) {
        routes = newList
        notifyDataSetChanged()
    }

    private fun normalizedToUi(normalized: String): String {
        return when (normalized) {
            DifficultyMapper.FACIL -> "Fácil"
            DifficultyMapper.MODERADA -> "Media"
            DifficultyMapper.DIFICIL -> "Difícil"
            else -> "Media"
        }
    }
}
