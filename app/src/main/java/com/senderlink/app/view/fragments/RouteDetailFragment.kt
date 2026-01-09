package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentRouteDetailBinding
import com.senderlink.app.model.Route
import com.senderlink.app.view.adapters.ImageGalleryAdapter
import com.senderlink.app.viewmodel.RouteDetailViewModel

class RouteDetailFragment : Fragment() {

    private var _binding: FragmentRouteDetailBinding? = null
    private val binding get() = _binding!!

    private val args: RouteDetailFragmentArgs by navArgs()
    private val viewModel: RouteDetailViewModel by viewModels()
    private lateinit var galleryAdapter: ImageGalleryAdapter

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

        val routeId = args.routeId
        Log.d("ROUTE_DETAIL", "Route ID recibido: $routeId")

        setupToolbar()
        setupGallery()
        observeViewModel()

        viewModel.loadRouteById(routeId)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupGallery() {
        galleryAdapter = ImageGalleryAdapter { imageUrl, position ->
            loadImageInCover(imageUrl, position)
        }

        binding.rvGallery.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = galleryAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewModel.route.observe(viewLifecycleOwner) { route ->
            route?.let { displayRouteDetails(it) }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                Log.e("ROUTE_DETAIL", "Error: $it")
            }
        }
    }

    private fun displayRouteDetails(route: Route) {
        binding.apply {
            // Título
            collapsingToolbar.title = route.name

            // Imagen de portada
            loadImageInCover(route.coverImage, 0)

            // ===== GALERÍA (PRIMERO, CERCA DE LA IMAGEN) =====
            if (route.images.size > 1) {
                rvGallery.visibility = View.VISIBLE
                tvGalleryTitle.visibility = View.VISIBLE
                tvGalleryTitle.text = "📸 ${route.images.size} fotos"
                galleryAdapter.submitList(route.images)
            } else {
                rvGallery.visibility = View.GONE
                tvGalleryTitle.visibility = View.GONE
            }

            // Badge destacada
            if (route.featured) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "⭐ DESTACADA"
            } else {
                tvBadge.visibility = View.GONE
            }

            // Tipo de ruta
            tvRouteType.visibility = View.VISIBLE
            tvRouteType.text = formatRouteType(route.type, route.source)

            // Nombre
            tvRouteName.text = route.name

            // ===== DATOS TÉCNICOS =====
            tvDistance.text = String.format("%.1f km", route.distanceKm)
            displayDuration(route.durationMin)
            tvDifficulty.text = formatDifficulty(route.difficulty)
            tvDifficulty.setTextColor(getDifficultyColor(route.difficulty))

            // ===== UBICACIÓN =====
            displayLocation(route)

            // ===== DESCRIPCIÓN =====
            tvDescription.text = route.description

            // ===== INFORMACIÓN GPS =====
            displayGPSInfo(route)

            // ===== BOTÓN VER EN MAPA =====
            btnViewOnMap.setOnClickListener {
                navigateToMap(route)
            }
        }
    }

    private fun loadImageInCover(imageUrl: String, position: Int) {
        Glide.with(requireContext())
            .load(imageUrl)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .into(binding.imgRouteCover)

        if (position > 0) {
            Toast.makeText(
                requireContext(),
                "📸 Foto ${position + 1}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatRouteType(type: String, source: String): String {
        val typeFormatted = when (type) {
            "PR" -> "Pequeño Recorrido"
            "GR" -> "Gran Recorrido"
            "SL" -> "Sendero Local"
            "VIA_VERDE" -> "Vía Verde"
            "PARQUE_NACIONAL" -> "Parque Nacional"
            "GPX_LIBRE" -> "Ruta GPX"
            "USER_ROUTE" -> "Ruta de Usuario"
            else -> type
        }

        val sourceFormatted = when (source) {
            "FEDME" -> "FEDME"
            "PARQUES_NACIONALES" -> "Parques Nacionales"
            "USER" -> "Usuario"
            "PROPIO" -> "SenderLink"
            else -> source
        }

        return "$typeFormatted · $sourceFormatted"
    }

    private fun displayDuration(durationMin: Int?) {
        if (durationMin != null && durationMin > 0) {
            val hours = durationMin / 60
            val minutes = durationMin % 60

            binding.tvDuration.text = when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
                hours > 0 -> "${hours}h"
                else -> "${minutes}min"
            }
        } else {
            binding.tvDuration.text = "N/A"
        }
    }

    private fun displayLocation(route: Route) {
        // Municipio
        if (!route.startLocality.isNullOrEmpty()) {
            binding.layoutMunicipio.visibility = View.VISIBLE
            binding.tvMunicipio.text = route.startLocality
        } else {
            binding.layoutMunicipio.visibility = View.GONE
        }

        // Provincia
        if (!route.provincia.isNullOrEmpty()) {
            binding.layoutProvincia.visibility = View.VISIBLE
            binding.tvProvincia.text = route.provincia
        } else {
            binding.layoutProvincia.visibility = View.GONE
        }

        // Comunidad
        if (!route.comunidad.isNullOrEmpty()) {
            binding.layoutComunidad.visibility = View.VISIBLE
            binding.tvComunidad.text = route.comunidad
        } else {
            binding.layoutComunidad.visibility = View.GONE
        }
    }

    /**
     * 📍 Muestra información GPS disponible
     */
    private fun displayGPSInfo(route: Route) {
        val info = buildString {
            // Número de puntos GPS
            route.geometry?.coordinates?.let { coords ->
                if (coords.isNotEmpty()) {
                    append("🗺️ ${coords.size} puntos GPS registrados\n")
                }
            }

            // Coordenadas de inicio
            route.startPoint?.let { point ->
                append("🚩 Inicio: ${formatCoordinates(point.lat, point.lng)}\n")
            }

            // Coordenadas de fin
            route.endPoint?.let { point ->
                append("🏁 Final: ${formatCoordinates(point.lat, point.lng)}")
            }
        }

        if (info.isNotBlank()) {
            binding.tvAdditionalInfoTitle.visibility = View.VISIBLE
            binding.tvAdditionalInfo.visibility = View.VISIBLE
            binding.tvAdditionalInfo.text = info.trim()
        } else {
            binding.tvAdditionalInfoTitle.visibility = View.GONE
            binding.tvAdditionalInfo.visibility = View.GONE
        }
    }

    private fun formatCoordinates(lat: Double, lng: Double): String {
        return "${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
    }

    /**
     * 🗺️ Navega al MapFragment con los datos de la ruta
     */
    private fun navigateToMap(route: Route) {
        // Verificar que tengamos datos GPS
        if (route.startPoint == null) {
            Toast.makeText(
                requireContext(),
                "⚠️ Esta ruta no tiene datos GPS disponibles",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            // Preparar los puntos de la ruta
            val routePointsArray = route.geometry?.coordinates?.let { coords ->
                DoubleArray(coords.size * 2).apply {
                    coords.forEachIndexed { index, coord ->
                        // coord[0] = lng, coord[1] = lat
                        this[index * 2] = coord[1]     // lat
                        this[index * 2 + 1] = coord[0] // lng
                    }
                }
            }

            // Crear bundle con todos los datos
            val bundle = Bundle().apply {
                putString("routeName", route.name)
                putDouble("startLat", route.startPoint?.lat ?: 0.0)
                putDouble("startLng", route.startPoint?.lng ?: 0.0)
                putDouble("endLat", route.endPoint?.lat ?: 0.0)
                putDouble("endLng", route.endPoint?.lng ?: 0.0)
                putDouble("distanceKm", route.distanceKm)
                putString("difficulty", route.difficulty)
                routePointsArray?.let { putDoubleArray("routePoints", it) }
            }

            // Navegar al tab de Mapas con los argumentos
            findNavController().navigate(R.id.nav_maps, bundle)

            Toast.makeText(
                requireContext(),
                "📍 Cargando ruta en el mapa...",
                Toast.LENGTH_SHORT
            ).show()

            Log.d("ROUTE_DETAIL", "Navegando al mapa con:")
            Log.d("ROUTE_DETAIL", "- Ruta: ${route.name}")
            Log.d("ROUTE_DETAIL", "- Puntos GPS: ${route.geometry?.coordinates?.size}")

        } catch (e: Exception) {
            Log.e("ROUTE_DETAIL", "Error al navegar al mapa: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error al abrir el mapa",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatDifficulty(difficulty: String): String {
        return when (difficulty) {
            "FACIL" -> "Fácil"
            "MODERADA" -> "Moderada"
            "DIFICIL" -> "Difícil"
            else -> difficulty
        }
    }

    private fun getDifficultyColor(difficulty: String): Int {
        return when (difficulty) {
            "FACIL" -> resources.getColor(android.R.color.holo_green_dark, null)
            "MODERADA" -> resources.getColor(android.R.color.holo_orange_dark, null)
            "DIFICIL" -> resources.getColor(android.R.color.holo_red_dark, null)
            else -> resources.getColor(android.R.color.darker_gray, null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}