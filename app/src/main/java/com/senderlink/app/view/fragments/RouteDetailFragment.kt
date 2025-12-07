package com.senderlink.app.view.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentRouteDetailBinding
import com.senderlink.app.view.MapActivity
import com.senderlink.app.view.adapters.GalleryAdapter

class RouteDetailFragment : Fragment() {

    private var _binding: FragmentRouteDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nombre = arguments?.getString("nombre")
        val descripcion = arguments?.getString("descripcion")
        val imagen = arguments?.getString("imagen")
        val imagenes = arguments?.getStringArrayList("imagenes") ?: emptyList<String>()
        val lat = arguments?.getDouble("latitud")
        val lng = arguments?.getDouble("longitud")

        // Texto
        binding.tvNombreRutaDetalle.text = nombre
        binding.tvDescripcionRutaDetalle.text = descripcion
        // Información técnica de la ruta
        binding.tvDificultad.text = "Dificultad: ${arguments?.getString("dificultad") ?: "N/A"}"
        binding.tvDistancia.text = "Distancia: ${arguments?.getDouble("distancia") ?: 0.0} km"
        binding.tvDuracion.text = "Duración: ${arguments?.getInt("duracion") ?: 0} min"
        binding.tvUbicacion.text =
            "${arguments?.getString("localidad") ?: "Ubicación desconocida"}, " +
                    "${arguments?.getString("provincia") ?: ""}"


        // Imagen principal AL ENTRAR
        Glide.with(requireContext())
            .load(imagen)
            .into(binding.imgRutaDetalle)

        // Galería horizontal con clic para cambiar imagen grande
        binding.rvGaleria.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.rvGaleria.adapter = GalleryAdapter(imagenes) { urlSeleccionada ->
            Glide.with(requireContext())
                .load(urlSeleccionada)
                .into(binding.imgRutaDetalle)
        }

        // Botón ver en el mapa → Abre actividad Maps
        binding.btnVerEnMapa.setOnClickListener {
            val intent = Intent(requireContext(), MapActivity::class.java)
            intent.putExtra("lat", lat)
            intent.putExtra("lng", lng)
            startActivity(intent)
        }

        // BOTÓN VOLVER -> SIEMPRE LLEVA A HOME
        binding.btnVolver.setOnClickListener {
            // Limpia el backstack hasta home
            Navigation.findNavController(requireView())
                .popBackStack(R.id.nav_home, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
