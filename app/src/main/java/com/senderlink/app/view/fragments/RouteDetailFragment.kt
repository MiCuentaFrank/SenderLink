package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.databinding.DialogCreateEventBinding
import com.senderlink.app.databinding.FragmentRouteDetailBinding
import com.senderlink.app.model.Route
import com.senderlink.app.view.adapters.ImageGalleryAdapter
import com.senderlink.app.viewmodel.RouteDetailViewModel
import com.senderlink.app.viewmodel.RutasGrupalesViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RouteDetailFragment : Fragment() {

    private var _binding: FragmentRouteDetailBinding? = null
    private val binding get() = _binding!!

    private val args: RouteDetailFragmentArgs by navArgs()

    // ✅ VM de detalle ruta
    private val routeDetailViewModel: RouteDetailViewModel by viewModels()

    // ✅ VM de eventos (compartido con Comunidad/RutasGrupales)
    private val eventosViewModel: RutasGrupalesViewModel by activityViewModels()

    private lateinit var galleryAdapter: ImageGalleryAdapter

    private val TAG = "ROUTE_DETAIL"

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
        Log.d(TAG, "Route ID recibido: $routeId")

        // ❌ setupToolbar() eliminado (ya no hay toolbar en el XML)
        setupGallery()
        setupEventosActions(routeId)

        observeRouteViewModel()
        observeEventosState()

        // ✅ Cargar detalle de ruta
        routeDetailViewModel.loadRouteById(routeId)

        // ✅ Comprobar si hay eventos de esta ruta (sin mostrar lista aquí)
        refreshEventosState(routeId)
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

    // ===========================
    // ✅ EVENTOS (SIN LISTA)
    // ===========================
    private fun setupEventosActions(routeId: String) {
        // Arranque: todo oculto hasta tener resultado
        binding.progressEventos.visibility = View.GONE
        binding.tvEventosInfo.visibility = View.GONE
        binding.btnCrearEvento.visibility = View.GONE
        binding.btnVerEventosRuta.visibility = View.GONE

        binding.btnCrearEvento.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Necesitas iniciar sesión para crear una ruta grupal",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            showCreateEventoBottomSheet(routeId)
        }

        binding.btnVerEventosRuta.setOnClickListener {
            goToRutasGrupales(routeId)
        }
    }

    private fun refreshEventosState(routeId: String) {
        binding.progressEventos.visibility = View.VISIBLE
        binding.tvEventosInfo.visibility = View.GONE
        binding.btnCrearEvento.visibility = View.GONE
        binding.btnVerEventosRuta.visibility = View.GONE

        // ✅ EXISTE: carga eventos por ruta y publica en eventosRuta
        eventosViewModel.loadEventosPorRuta(routeId)
    }

    private fun renderEventosUI(eventosCount: Int) {
        binding.progressEventos.visibility = View.GONE
        binding.tvEventosInfo.visibility = View.VISIBLE

        if (eventosCount <= 0) {
            binding.tvEventosInfo.text =
                "Aún no hay rutas grupales para esta ruta. ¿Creas la primera?"
            binding.btnCrearEvento.visibility = View.VISIBLE
            binding.btnVerEventosRuta.visibility = View.GONE
        } else {
            binding.tvEventosInfo.text =
                "Hay $eventosCount rutas grupales para esta ruta."
            binding.btnCrearEvento.visibility = View.GONE
            binding.btnVerEventosRuta.visibility = View.VISIBLE
        }
    }

    private fun goToRutasGrupales(routeId: String) {
        Log.d(TAG, "📍 Navegando a Rutas Grupales con routeId=$routeId")

        eventosViewModel.openFromRouteDetail(routeId, eventId = null)

        val bundle = Bundle().apply {
            putInt("initialTab", SocialFragment.TAB_RUTAS_GRUPALES)
            putString("routeId", routeId)
        }

        try {
            findNavController().navigate(R.id.nav_comunidad, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a Rutas Grupales", e)
            Toast.makeText(requireContext(), "Error al abrir Rutas Grupales", Toast.LENGTH_SHORT).show()
        }
    }

    // ===========================
    // OBSERVERS
    // ===========================
    private fun observeEventosState() {
        eventosViewModel.eventosRuta.observe(viewLifecycleOwner) { list ->
            renderEventosUI(eventosCount = list.size)
        }

        eventosViewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                eventosViewModel.clearMessages()

                binding.progressEventos.visibility = View.GONE
                binding.tvEventosInfo.visibility = View.VISIBLE
                binding.tvEventosInfo.text = "No se pudieron cargar las rutas grupales ahora mismo."
                binding.btnCrearEvento.visibility = View.GONE
                binding.btnVerEventosRuta.visibility = View.GONE
            }
        }

        eventosViewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                eventosViewModel.clearMessages()
                refreshEventosState(args.routeId)
            }
        }
    }

    private fun observeRouteViewModel() {
        routeDetailViewModel.route.observe(viewLifecycleOwner) { route ->
            route?.let { displayRouteDetails(it) }
        }

        routeDetailViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        routeDetailViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error: $it")
            }
        }
    }

    // ===========================
    // UI RUTA
    // ===========================
    private fun displayRouteDetails(route: Route) {
        // ✅ Ya no hay collapsingToolbar -> usamos título del Activity (opcional)
        requireActivity().title = route.name

        binding.apply {
            loadImageInCover(route.coverImage, 0)

            if (route.images.size > 1) {
                rvGallery.visibility = View.VISIBLE
                tvGalleryTitle.visibility = View.VISIBLE
                tvGalleryTitle.text = "📸 ${route.images.size} fotos"
                galleryAdapter.submitList(route.images)
            } else {
                rvGallery.visibility = View.GONE
                tvGalleryTitle.visibility = View.GONE
            }

            if (route.featured) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "⭐ DESTACADA"
            } else {
                tvBadge.visibility = View.GONE
            }

            tvRouteType.visibility = View.VISIBLE
            tvRouteType.text = formatRouteType(route.type, route.source)

            tvRouteName.text = route.name

            tvDistance.text = String.format(Locale.getDefault(), "%.1f km", route.distanceKm)
            displayDuration(route.durationMin)
            tvDifficulty.text = formatDifficulty(route.difficulty)
            tvDifficulty.setTextColor(getDifficultyColor(route.difficulty))

            displayLocation(route)
            tvDescription.text = route.description
            displayGPSInfo(route)

            btnViewOnMap.setOnClickListener { navigateToMap(route) }
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
            Toast.makeText(requireContext(), "📸 Foto ${position + 1}", Toast.LENGTH_SHORT).show()
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
        } else binding.tvDuration.text = "N/A"
    }

    private fun displayLocation(route: Route) {
        if (!route.startLocality.isNullOrEmpty()) {
            binding.layoutMunicipio.visibility = View.VISIBLE
            binding.tvMunicipio.text = route.startLocality
        } else binding.layoutMunicipio.visibility = View.GONE

        if (!route.provincia.isNullOrEmpty()) {
            binding.layoutProvincia.visibility = View.VISIBLE
            binding.tvProvincia.text = route.provincia
        } else binding.layoutProvincia.visibility = View.GONE

        if (!route.comunidad.isNullOrEmpty()) {
            binding.layoutComunidad.visibility = View.VISIBLE
            binding.tvComunidad.text = route.comunidad
        } else binding.layoutComunidad.visibility = View.GONE
    }

    private fun displayGPSInfo(route: Route) {
        val info = buildString {
            route.geometry?.coordinates?.let { coords ->
                if (coords.isNotEmpty()) append("🗺️ ${coords.size} puntos GPS registrados\n")
            }

            route.startPoint?.let { point ->
                append("🚩 Inicio: ${formatCoordinates(point.getLat(), point.getLng())}\n")
            }

            route.endPoint?.let { point ->
                append("🏁 Final: ${formatCoordinates(point.getLat(), point.getLng())}")
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
        return "${String.format(Locale.getDefault(), "%.4f", lat)}, ${
            String.format(Locale.getDefault(), "%.4f", lng)
        }"
    }

    private fun navigateToMap(route: Route) {
        if (route.startPoint == null) {
            Toast.makeText(requireContext(), "⚠️ Esta ruta no tiene datos GPS disponibles", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val routePointsArray = route.geometry?.coordinates?.let { coords ->
                FloatArray(coords.size * 2).apply {
                    coords.forEachIndexed { index, coord ->
                        this[index * 2] = coord[1].toFloat()
                        this[index * 2 + 1] = coord[0].toFloat()
                    }
                }
            }

            val bundle = Bundle().apply {
                putString("routeName", route.name)
                putFloat("startLat", route.startPoint?.lat?.toFloat() ?: 0.0f)
                putFloat("startLng", route.startPoint?.lng?.toFloat() ?: 0.0f)
                putFloat("endLat", route.endPoint?.lat?.toFloat() ?: 0.0f)
                putFloat("endLng", route.endPoint?.lng?.toFloat() ?: 0.0f)
                putFloat("distanceKm", route.distanceKm.toFloat())
                putString("difficulty", route.difficulty)
                routePointsArray?.let { putFloatArray("routePoints", it) }
            }

            findNavController().navigate(R.id.nav_maps, bundle)
            Toast.makeText(requireContext(), "📍 Cargando ruta en el mapa...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar al mapa: ${e.message}", e)
            Toast.makeText(requireContext(), "Error al abrir el mapa", Toast.LENGTH_SHORT).show()
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
            "FACIL" -> ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            "MODERADA" -> ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
            "DIFICIL" -> ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
            else -> ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        }
    }

    private fun showCreateEventoBottomSheet(routeId: String) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogCreateEventBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.btnCancelar.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnCrear.setOnClickListener {
            val fechaSimple = dialogBinding.etFecha.text?.toString()?.trim().orEmpty()
            val hora = dialogBinding.etHora.text?.toString()?.trim().orEmpty()
            val max = dialogBinding.etMaxParticipantes.text?.toString()?.trim()?.toIntOrNull() ?: 10
            val descripcion = dialogBinding.etDescripcion.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

            if (fechaSimple.isBlank() || hora.isBlank()) {
                Toast.makeText(requireContext(), "Faltan datos: fecha/hora", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fechaIso = try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaParsed = dateFormat.parse(fechaSimple)

                if (fechaParsed == null) {
                    Toast.makeText(requireContext(), "Formato de fecha inválido. Usa dd/MM/yyyy", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val calendar = Calendar.getInstance()
                calendar.time = fechaParsed

                val horaParts = hora.split(":")
                if (horaParts.size == 2) {
                    calendar.set(Calendar.HOUR_OF_DAY, horaParts[0].toIntOrNull() ?: 9)
                    calendar.set(Calendar.MINUTE, horaParts[1].toIntOrNull() ?: 0)
                } else {
                    calendar.set(Calendar.HOUR_OF_DAY, 9)
                    calendar.set(Calendar.MINUTE, 0)
                }

                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                isoFormat.format(calendar.time)

            } catch (e: Exception) {
                Log.e(TAG, "Error parseando fecha: $fechaSimple", e)
                Toast.makeText(requireContext(), "Error en el formato de fecha", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d(TAG, "📅 Fecha ISO generada: $fechaIso")

            eventosViewModel.createEvento(
                routeId = routeId,
                fecha = fechaIso,
                maxParticipantes = max,
                descripcion = descripcion,
                horaEncuentro = hora
            )

            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
