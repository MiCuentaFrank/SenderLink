package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.databinding.ItemRouteBinding
import com.senderlink.app.model.Route

class RouteAdapter(
    private val onClick: (Route) -> Unit
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    private var routes: List<Route> = emptyList()

    inner class RouteViewHolder(
        val binding: ItemRouteBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRouteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]

        // Actualizado para usar los IDs correctos del XML
        holder.binding.tvRouteName.text = route.name
        holder.binding.tvDificultadRuta.text = route.difficulty
        holder.binding.tvDistanciaRuta.text = "${route.distanceKm} km"

        Glide.with(holder.itemView.context)
            .load(route.coverImage)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_dialog_alert)
            .centerCrop()
            .into(holder.binding.imgRoute)

        holder.itemView.setOnClickListener {
            onClick(route)
        }
    }

    override fun getItemCount(): Int = routes.size

    fun submitList(newList: List<Route>) {
        routes = newList
        notifyDataSetChanged()
    }
}