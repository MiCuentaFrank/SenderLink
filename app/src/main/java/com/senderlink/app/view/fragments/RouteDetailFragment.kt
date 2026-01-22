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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.databinding.DialogCreateEventBinding
import com.senderlink.app.databinding.FragmentRouteDetailBinding
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.model.Route
import com.senderlink.app.view.adapters.EventoAdapter
import com.senderlink.app.view.adapters.ImageGalleryAdapter
import com.senderlink.app.viewmodel.RouteDetailViewModel
import com.senderlink.app.viewmodel.RutasGrupalesViewModel

class RouteDetailFragment : Fragment() {

    private var _binding: FragmentRouteDetailBinding? = null
    private val binding get() = _binding!!

    private val args: RouteDetailFragmentArgs by navArgs()

    // ✅ ViewModel de detalle ruta (solo ruta)
    private val viewModel: RouteDetailViewModel by viewModels()

    // ✅ ViewModel de eventos
    private val eventosViewModel: RutasGrupalesViewModel by viewModels()

    private lateinit var galleryAdapter: ImageGalleryAdapter
    private lateinit var eventosAdapter: EventoAdapter

    private var eventosCache: List<EventoGrupal> = emptyList()

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
        setupEventosRecycler()

        observeRouteViewModel()
        observeEventosViewModel()
        observeEventosList()

        // ✅ Botón crear evento (está en la card de eventos)
        binding.btnCrearEvento.setOnClickListener {
            showCreateEventoBottomSheet(routeId)
        }
        binding.btnVerEventosRuta.setOnClickListener {
            val bundle = Bundle().apply { putString("routeId", args.routeId) }
            findNavController().navigate(
                R.id.action_routeDetailFragment_to_rutasGrupalesFragment,
                bundle
            )
        }


        // ✅ Cargar detalle de ruta
        viewModel.loadRouteById(routeId)

        // ✅ Cargar eventos CON flags (isParticipant/isOrganizer)
        loadEventosDeRutaConFlags(routeId)
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
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = galleryAdapter
            setHasFixedSize(true)
        }
    }

    // ===========================
    // ✅ EVENTOS UI
    // ===========================
    private fun setupEventosRecycler() {
        eventosAdapter = EventoAdapter(
            onEventoClick = { evento ->
                // click en card -> lo dejo neutral por ahora
                Log.d("ROUTE_DETAIL", "Click evento: ${evento.id}")
            },
            onJoinClick = { evento ->
                eventosViewModel.joinEvento(evento)
            },
            onLeaveClick = { evento ->
                eventosViewModel.leaveEvento(evento)
            },
            onChatClick = {
                goToRutasGrupales(args.routeId)
            }

        )

        binding.rvEventos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventosAdapter
            setHasFixedSize(false)
        }

        // Estado inicial
        binding.progressEventos.visibility = View.GONE
        binding.rvEventos.visibility = View.GONE
        binding.tvEventosEmpty.visibility = View.GONE
    }

    private fun loadEventosDeRutaConFlags(routeId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            eventosCache = emptyList()
            renderEventosUI()
            return
        }

        // mini-loading de eventos
        binding.progressEventos.visibility = View.VISIBLE
        binding.rvEventos.visibility = View.GONE
        binding.tvEventosEmpty.visibility = View.GONE

        // ✅ Usamos el VM (no repo directo)
        eventosViewModel.loadEventosPorRutaForUser(routeId)
    }
    private fun goToRutasGrupales(routeId: String) {
        val bundle = Bundle().apply { putString("routeId", routeId) }
        findNavController().navigate(
            R.id.action_routeDetailFragment_to_rutasGrupalesFragment,
            bundle
        )
    }


    private fun observeEventosList() {
        eventosViewModel.eventos.observe(viewLifecycleOwner) { list ->
            binding.progressEventos.visibility = View.GONE

            eventosCache = list ?: emptyList()

            // Debug rápido
            eventosCache.take(5).forEachIndexed { i, ev ->
                Log.d(
                    "ROUTE_DETAIL",
                    "Evento[$i] id=${ev.id} isParticipant=${ev.isParticipant} isOrganizer=${ev.isOrganizer} chatId=${ev.chatId}"
                )
            }

            renderEventosUI()
        }
    }

    private fun renderEventosUI() {
        if (eventosCache.isEmpty()) {
            binding.rvEventos.visibility = View.GONE
            binding.tvEventosEmpty.visibility = View.VISIBLE
        } else {
            binding.tvEventosEmpty.visibility = View.GONE
            binding.rvEventos.visibility = View.VISIBLE
            eventosAdapter.submitList(eventosCache)
        }
    }




    // ===========================
    // OBSERVERS
    // ===========================
    private fun observeRouteViewModel() {
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

    private fun observeEventosViewModel() {
        eventosViewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                eventosViewModel.clearMessages()

                // Si hubo error, recargar para mantener UI coherente
                loadEventosDeRutaConFlags(args.routeId)
            }
        }

        eventosViewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                eventosViewModel.clearMessages()

                // ✅ tras create/join/leave -> recargar lista de esta ruta con flags
                loadEventosDeRutaConFlags(args.routeId)
            }
        }
    }

    // ===========================
    // UI RUTA
    // ===========================
    private fun displayRouteDetails(route: Route) {
        binding.apply {
            collapsingToolbar.title = route.name

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

            tvDistance.text = String.format("%.1f km", route.distanceKm)
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
        return "${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
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
            Log.e("ROUTE_DETAIL", "Error al navegar al mapa: ${e.message}", e)
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
            "FACIL" -> resources.getColor(android.R.color.holo_green_dark, null)
            "MODERADA" -> resources.getColor(android.R.color.holo_orange_dark, null)
            "DIFICIL" -> resources.getColor(android.R.color.holo_red_dark, null)
            else -> resources.getColor(android.R.color.darker_gray, null)
        }
    }

    // ===========================
    // ✅ CREAR EVENTO DESDE DETALLE
    // ===========================
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

            val fechaIso = "${fechaSimple}T${hora}:00.000Z"

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
