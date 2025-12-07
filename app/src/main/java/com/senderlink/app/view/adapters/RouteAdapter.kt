package com.senderlink.app.view.adapters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemRouteBinding
import com.senderlink.app.model.Route

class RouteAdapter(
    private var routes: List<Route> = emptyList()
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    inner class RouteViewHolder(val binding: ItemRouteBinding) :
        RecyclerView.ViewHolder(binding.root)

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
        println("ADAPTER → Pintando: ${route.nombre}")

        holder.binding.tvNombreRuta.text = route.nombre
        holder.binding.tvDificultadRuta.text = "Dificultad: ${route.dificultad}"
        holder.binding.tvDistanciaRuta.text = "Distancia: ${route.distancia} km"

        holder.itemView.setOnClickListener {

            val bundle = Bundle().apply {

                // -------- DATOS PRINCIPALES --------
                putString("nombre", route.nombre)
                putString("descripcion", route.descripcion)
                putString("dificultad", route.dificultad)
                putDouble("distancia", route.distancia ?: 0.0)
                putInt("duracion", route.duracion ?: 0)
                putString("provincia", route.provincia)
                putString("localidad", route.localidad)


                // ✔ OJO: ahora enviamos correctamente "imagen"
                putString("imagen", route.imagenPortada)

                // -------- COORDENADAS ----------
                if (route.puntos.isNotEmpty()) {
                    putDouble("latitud", route.puntos[0].latitud)
                    putDouble("longitud", route.puntos[0].longitud)
                }

                // -------- GALERÍA ----------
                putStringArrayList(
                    "imagenes",
                    ArrayList(route.imagenes ?: emptyList())
                )
            }

            Navigation.findNavController(holder.itemView)
                .navigate(R.id.action_homeFragment_to_routeDetailFragment, bundle)
        }

        // Cargar imagen principal
        Glide.with(holder.itemView.context)
            .load(route.imagenPortada)
            .placeholder(R.drawable.ic_launcher_background)
            .into(holder.binding.imgRuta)
    }

    override fun getItemCount(): Int = routes.size

    fun submitList(newRoutes: List<Route>) {
        routes = newRoutes
        notifyDataSetChanged()
    }
}
