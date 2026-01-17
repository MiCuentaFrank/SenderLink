package com.senderlink.app.view.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.senderlink.app.R
import com.senderlink.app.databinding.DialogDistanceFilterBinding
import com.senderlink.app.databinding.FragmentMapasBinding
import com.senderlink.app.model.Route
import com.senderlink.app.view.adapters.RouteAdapter
import com.senderlink.app.viewmodel.MapasViewModel
import java.text.Normalizer
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MapasFragment : Fragment(), OnMapReadyCallback {

    private companion object {
        const val TAG = "MAPAS_FRAGMENT"
        const val KEY_HAS_TRIGGERED_INITIAL_NEARBY = "KEY_HAS_TRIGGERED_INITIAL_NEARBY"
        const val KEY_MAX_DISTANCE = "KEY_MAX_DISTANCE"
        const val KEY_SEARCH = "KEY_SEARCH"
        const val KEY_SELECTED_TYPES = "KEY_SELECTED_TYPES"
        const val KEY_SELECTED_DIFFS = "KEY_SELECTED_DIFFS"
        const val KEY_ONLY_NATIONAL_PARKS = "KEY_ONLY_NATIONAL_PARKS"
        const val KEY_HAS_ADJUSTED_CAMERA = "KEY_HAS_ADJUSTED_CAMERA"

        // ✅ Paginación local
        const val PAGE_SIZE = 20
    }

    private var _binding: FragmentMapasBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val viewModel: MapasViewModel by viewModels()

    private lateinit var routeAdapter: RouteAdapter
    private lateinit var sheetBehaviorRoutes: BottomSheetBehavior<View>

    private var currentCenterLatLng: LatLng? = null
    private var suppressSearchCallback = false

    // Datos
    private var allRoutes: List<Route> = emptyList()
    private var filteredRoutes: List<Route> = emptyList()
    private var displayedRoutes: List<Route> = emptyList()

    // ✅ Paginación LOCAL
    private var currentDisplayCount = PAGE_SIZE
    private var isLoadingMore = false
    private var hasTriggeredInitialNearby = false

    // Estado filtros
    private var maxDistanceKm: Float = 50f

    private val selectedTypes = mutableSetOf<String>()
    private val selectedDifficulties = mutableSetOf<String>()
    private var onlyNationalParks: Boolean = false

    // Estado mapa / markers
    private val markersByRouteId = mutableMapOf<String, Marker>()
    private var lastWantedIds: Set<String> = emptySet()
    private var hasAdjustedCameraOnce = false

    // Datos cuando vienes desde RouteDetail
    private var routeName: String? = null
    private var startLat: Double = 0.0
    private var startLng: Double = 0.0
    private var endLat: Double = 0.0
    private var endLng: Double = 0.0
    private var routePoints: ArrayList<LatLng>? = null
    private var difficultyFromArgs: String? = null

    private var userLocation: Location? = null
    private var navigationPolyline: Polyline? = null
    private var currentPolyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                enableMyLocation()
                getUserLocation()
                if (startLat != 0.0 && startLng != 0.0) {
                    calculateRealDistanceToStart()
                }
            }
            else -> Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        restoreState(savedInstanceState)
        getArgumentsData()

        val comingFromDetail = (routePoints != null && routePoints!!.isNotEmpty()) ||
                (startLat != 0.0 && startLng != 0.0)

        if (!hasTriggeredInitialNearby && savedInstanceState == null && !comingFromDetail) {
            maxDistanceKm = 50f
            hasAdjustedCameraOnce = false
            hasTriggeredInitialNearby = true
        }

        setupBottomSheet()
        setupRecyclerView()
        setupActionButtons()
        setupTopUi()
        setupChips()
        observeViewModel()

        val mapFragment = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction()
            .replace(R.id.mapContainer, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        val cached = viewModel.allRoutes.value.orEmpty()

        if (!comingFromDetail) {
            Log.d(TAG, "Modo cerca activo -> cargando rutas a <= ${maxDistanceKm.toInt()} km")

            if (cached.isNotEmpty()) {
                allRoutes = cached
            }

            updateChipStates()
            filterRoutesByDistance()
        } else {
            if (cached.isNotEmpty()) {
                Log.d(TAG, "Usando cache del ViewModel: ${cached.size} rutas")
                allRoutes = cached
                applyActiveFilters(adjustCamera = false)
                updateChipStates()
            } else {
                Log.d(TAG, "ViewModel vacío -> cargando del servidor")
                filterRoutesByDistance()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(KEY_HAS_TRIGGERED_INITIAL_NEARBY, hasTriggeredInitialNearby)
        outState.putFloat(KEY_MAX_DISTANCE, maxDistanceKm)
        outState.putString(KEY_SEARCH, binding.etSearch.text?.toString() ?: "")

        outState.putStringArray(KEY_SELECTED_TYPES, selectedTypes.toTypedArray())
        outState.putStringArray(KEY_SELECTED_DIFFS, selectedDifficulties.toTypedArray())
        outState.putBoolean(KEY_ONLY_NATIONAL_PARKS, onlyNationalParks)

        outState.putBoolean(KEY_HAS_ADJUSTED_CAMERA, hasAdjustedCameraOnce)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState ?: return

        maxDistanceKm = savedInstanceState.getFloat(KEY_MAX_DISTANCE, 50f)
        onlyNationalParks = savedInstanceState.getBoolean(KEY_ONLY_NATIONAL_PARKS, false)

        selectedTypes.clear()
        selectedTypes.addAll(savedInstanceState.getStringArray(KEY_SELECTED_TYPES).orEmpty())

        selectedDifficulties.clear()
        selectedDifficulties.addAll(savedInstanceState.getStringArray(KEY_SELECTED_DIFFS).orEmpty())
        hasTriggeredInitialNearby = savedInstanceState.getBoolean(KEY_HAS_TRIGGERED_INITIAL_NEARBY, false)
        hasAdjustedCameraOnce = savedInstanceState.getBoolean(KEY_HAS_ADJUSTED_CAMERA, false)
    }

    // =========================================================
    // ✅ Observer
    // =========================================================
    private fun observeViewModel() {
        viewModel.allRoutes.observe(viewLifecycleOwner) { routes ->
            allRoutes = routes.orEmpty()
            Log.d(TAG, "📥 Observer recibió ${allRoutes.size} rutas, googleMap=${googleMap != null}")
            Log.d(TAG, "📥 Primera ruta: ${allRoutes.firstOrNull()?.name}, hasValidGPS=${allRoutes.firstOrNull()?.hasValidGPS()}")
            Log.d(TAG, "📥 StartPoint: ${allRoutes.firstOrNull()?.startPoint}")

            if (allRoutes.isEmpty()) {
                filteredRoutes = emptyList()
                displayedRoutes = emptyList()
                routeAdapter.submitList(emptyList())
                binding.tvRoutesCount.text = "0 Rutas"
                googleMap?.let {
                    markersByRouteId.values.forEach { marker -> marker.remove() }
                    markersByRouteId.clear()
                    lastWantedIds = emptySet()
                }
                return@observe
            }

            currentDisplayCount = PAGE_SIZE
            applyActiveFilters(adjustCamera = !hasAdjustedCameraOnce)

            isLoadingMore = false
            binding.progressBarLoadMore.visibility = View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading) {
                isLoadingMore = false
                binding.progressBarLoadMore.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
                isLoadingMore = false
                binding.progressBarLoadMore.visibility = View.GONE
            }
        }
    }

    private fun setupBottomSheet() {
        sheetBehaviorRoutes = BottomSheetBehavior.from(binding.bottomSheetRoutes)
        sheetBehaviorRoutes.state = BottomSheetBehavior.STATE_COLLAPSED
        sheetBehaviorRoutes.peekHeight = 250
        sheetBehaviorRoutes.isHideable = false
        sheetBehaviorRoutes.isFitToContents = false
        sheetBehaviorRoutes.halfExpandedRatio = 0.5f
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteAdapter { route -> navigateToRouteDetail(route) }
        val layoutManager = LinearLayoutManager(requireContext())

        binding.rvRoutes.apply {
            this.layoutManager = layoutManager
            adapter = routeAdapter
        }

        binding.bottomSheetRoutes.setOnScrollChangeListener { v: View, _, scrollY, _, oldScrollY ->
            if (scrollY <= oldScrollY) return@setOnScrollChangeListener

            val nestedScrollView = v as androidx.core.widget.NestedScrollView
            val childView = nestedScrollView.getChildAt(0)

            val diff = childView.bottom - (nestedScrollView.height + scrollY)

            if (!isLoadingMore &&
                currentDisplayCount < filteredRoutes.size &&
                diff < 300
            ) {
                loadMoreLocalRoutes()
            }
        }
    }

    private fun loadMoreLocalRoutes() {
        if (currentDisplayCount >= filteredRoutes.size) return

        isLoadingMore = true
        binding.progressBarLoadMore.visibility = View.VISIBLE

        binding.root.postDelayed({
            currentDisplayCount += PAGE_SIZE
            updateDisplayedRoutes()

            isLoadingMore = false
            binding.progressBarLoadMore.visibility = View.GONE

            Log.d(TAG, "Mostrando ${displayedRoutes.size} de ${filteredRoutes.size} rutas")
        }, 200)
    }

    // =========================================================
    // ✅ TOP UI (buscador)
    // =========================================================
    private fun setupTopUi() {

        // 1) Filtrado local mientras escribe
        binding.etSearch.addTextChangedListener {
            if (suppressSearchCallback) return@addTextChangedListener
            currentDisplayCount = PAGE_SIZE
            applyActiveFilters(adjustCamera = false)
        }

        // 2) Acción SEARCH del teclado => interpretar como lugar si se puede
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString().orEmpty().trim()
                if (query.isBlank()) return@setOnEditorActionListener true

                lifecycleScope.launch {
                    val latLng = geocodeToLatLng(query)
                    if (latLng != null) {
                        // ✅ ES UN LUGAR: buscamos por cercanía
                        moveCenterAndLoadNearby(latLng, label = query)
                    } else {
                        // ❌ No se pudo ubicar: filtrado por texto
                        currentDisplayCount = PAGE_SIZE
                        applyActiveFilters(adjustCamera = true)
                        Toast.makeText(requireContext(), "No pude ubicar ese lugar. Filtrando por texto.", Toast.LENGTH_SHORT).show()
                    }
                }

                true
            } else false
        }
    }

    // =========================================================
    // ✅ CHIPS
    // =========================================================
    private fun setupChips() {
        binding.chipNearby.setOnClickListener { showDistanceFilterDialog() }

        binding.chipNearby.setOnCheckedChangeListener { _, _ ->
            binding.chipNearby.isChecked = true
        }

        binding.chipFilters.setOnClickListener { showFiltersDialog() }

        updateChipStates()
    }

    private fun updateChipStates() {
        binding.chipNearby.isChecked = true
        binding.chipNearby.text = "Cerca ${maxDistanceKm.toInt()}km"
        binding.chipFilters.isChecked = hasAnyExtraFilters()
    }

    private fun hasAnyExtraFilters(): Boolean {
        val hasDiff = selectedDifficulties.isNotEmpty()
        val hasTypes = selectedTypes.isNotEmpty()
        return hasDiff || hasTypes || onlyNationalParks
    }

    private fun showDistanceFilterDialog() {
        val dialogBinding = DialogDistanceFilterBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.sliderDistance.value = maxDistanceKm
        dialogBinding.tvSelectedDistance.text = "${maxDistanceKm.toInt()} km"

        dialogBinding.sliderDistance.addOnChangeListener { _, value, _ ->
            dialogBinding.tvSelectedDistance.text = "${value.toInt()} km"
        }

        dialogBinding.chip10km.setOnClickListener { dialogBinding.sliderDistance.value = 10f }
        dialogBinding.chip25km.setOnClickListener { dialogBinding.sliderDistance.value = 25f }
        dialogBinding.chip50km.setOnClickListener { dialogBinding.sliderDistance.value = 50f }
        dialogBinding.chip100km.setOnClickListener { dialogBinding.sliderDistance.value = 100f }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnApply.setOnClickListener {
            val newDistance = dialogBinding.sliderDistance.value

            if (newDistance != maxDistanceKm) {
                maxDistanceKm = newDistance
                hasAdjustedCameraOnce = false
                currentDisplayCount = PAGE_SIZE

                updateChipStates()
                filterRoutesByDistance()

                Toast.makeText(
                    requireContext(),
                    "Mostrando rutas a menos de ${maxDistanceKm.toInt()} km",
                    Toast.LENGTH_SHORT
                ).show()
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFiltersDialog() {
        val ctx = requireContext()

        val diffLabels = listOf("Fácil", "Media", "Difícil")
        val diffKeys = listOf("FACIL", "MEDIA", "DIFICIL")

        val typeLabels = listOf("GR", "PR", "SL", "Vía Verde")
        val typeKeys = listOf("GR", "PR", "SL", "VIA_VERDE")

        val root = ScrollView(ctx)
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        root.addView(content)

        fun title(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 8, 0, 8)
        }

        content.addView(title("Dificultad"))

        val diffChecks = diffKeys.mapIndexed { i, key ->
            CheckBox(ctx).apply {
                text = diffLabels[i]
                isChecked = selectedDifficulties.contains(key)
            }.also { content.addView(it) }
        }

        content.addView(title("Tipo de ruta"))

        val typeChecks = typeKeys.mapIndexed { i, key ->
            CheckBox(ctx).apply {
                text = typeLabels[i]
                isChecked = selectedTypes.contains(key)
            }.also { content.addView(it) }
        }

        content.addView(title("Otros"))

        val cbNationalParks = CheckBox(ctx).apply {
            text = "Solo Parques Nacionales"
            isChecked = onlyNationalParks
        }
        content.addView(cbNationalParks)

        AlertDialog.Builder(ctx)
            .setTitle("Filtros")
            .setView(root)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Limpiar") { _, _ ->
                selectedDifficulties.clear()
                selectedTypes.clear()
                onlyNationalParks = false
                hasAdjustedCameraOnce = false
                currentDisplayCount = PAGE_SIZE
                updateChipStates()
                applyActiveFilters(adjustCamera = true)
            }
            .setPositiveButton("Aplicar") { _, _ ->
                selectedDifficulties.clear()
                selectedTypes.clear()

                diffChecks.forEachIndexed { i, cb ->
                    if (cb.isChecked) selectedDifficulties.add(diffKeys[i])
                }
                typeChecks.forEachIndexed { i, cb ->
                    if (cb.isChecked) selectedTypes.add(typeKeys[i])
                }

                onlyNationalParks = cbNationalParks.isChecked

                hasAdjustedCameraOnce = false
                currentDisplayCount = PAGE_SIZE
                updateChipStates()
                applyActiveFilters(adjustCamera = true)
            }
            .show()
    }

    // =========================================================
    // FILTRADO por cercanía
    // =========================================================
    private fun filterRoutesByDistance() {
        userLocation?.let { location ->
            viewModel.loadNearbyRoutes(
                lat = location.latitude,
                lng = location.longitude,
                radiusKm = maxDistanceKm
            )
        } ?: run {
            Toast.makeText(requireContext(), "Obteniendo tu ubicación...", Toast.LENGTH_SHORT).show()
            getUserLocation()

            binding.root.postDelayed({
                if (userLocation != null) {
                    filterRoutesByDistance()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No se pudo obtener tu ubicación. Activa el GPS.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 1200)
        }
    }

    private fun applyActiveFilters(adjustCamera: Boolean) {
        Log.d(TAG, "🔍 applyActiveFilters: allRoutes.size=${allRoutes.size}")

        val query = binding.etSearch.text?.toString().orEmpty().trim()

        var list = allRoutes
        Log.d(TAG, "🔍 Después de asignar allRoutes: ${list.size}")

        if (onlyNationalParks) {
            list = list.filter { !it.parqueNacional.isNullOrBlank() }
            Log.d(TAG, "🔍 Después de filtro parques: ${list.size}")
        }

        if (selectedDifficulties.isNotEmpty()) {
            Log.d(TAG, "🔍 Dificultades seleccionadas: $selectedDifficulties")
            list = list.filter { route ->
                val key = normalizeDifficulty(route.difficulty)
                selectedDifficulties.contains(key)
            }
            Log.d(TAG, "🔍 Después de filtro dificultad: ${list.size}")
        }

        if (selectedTypes.isNotEmpty()) {
            Log.d(TAG, "🔍 Tipos seleccionados: $selectedTypes")
            list = list.filter { route ->
                val key = normalizeType(route.type)
                selectedTypes.contains(key)
            }
            Log.d(TAG, "🔍 Después de filtro tipo: ${list.size}")
        }

        if (query.isNotBlank()) {
            Log.d(TAG, "🔍 Query de búsqueda: '$query'")
            val q = query.lowercase()
            list = list.filter { r ->
                r.name.lowercase().contains(q) ||
                        r.description.lowercase().contains(q) ||
                        (r.startLocality?.lowercase()?.contains(q) == true)
            }
            Log.d(TAG, "🔍 Después de filtro búsqueda: ${list.size}")
        }

        filteredRoutes = list
        Log.d(TAG, "🔍 filteredRoutes final: ${filteredRoutes.size}")

        updateDisplayedRoutes()
        updateMarkersOnMap(filteredRoutes, adjustCamera)
    }

    private fun updateDisplayedRoutes() {
        displayedRoutes = filteredRoutes.take(currentDisplayCount)
        routeAdapter.submitList(displayedRoutes.toList())
        binding.tvRoutesCount.text = "${filteredRoutes.size} Rutas"

        Log.d(TAG, "Lista: ${displayedRoutes.size}/${filteredRoutes.size} | Mapa: ${markersByRouteId.size} marcadores")
    }

    private fun updateMarkersOnMap(routes: List<Route>, adjustCamera: Boolean) {
        val map = googleMap
        if (map == null) {
            Log.w(TAG, "⚠️ googleMap es NULL - ${routes.size} rutas pendientes de pintar")
            return
        }

        Log.d(TAG, "🗺️ Pintando ${routes.size} rutas en el mapa")

        val wantedIds = routes.map { it.id }.toSet()

        if (wantedIds == lastWantedIds && markersByRouteId.isNotEmpty()) {
            Log.d(TAG, "Marcadores ya actualizados, saltando")
            return
        }
        lastWantedIds = wantedIds

        val iterator = markersByRouteId.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!wantedIds.contains(entry.key)) {
                entry.value.remove()
                iterator.remove()
            }
        }

        var addedCount = 0
        var skippedNoGPS = 0

        routes.forEach { route ->
            if (!route.hasValidGPS()) {
                skippedNoGPS++
                return@forEach
            }

            val routeId = route.id
            if (markersByRouteId.containsKey(routeId)) return@forEach

            val position = LatLng(route.getStartLat(), route.getStartLng())
            val markerColor = when (normalizeDifficulty(route.difficulty)) {
                "FACIL" -> BitmapDescriptorFactory.HUE_GREEN
                "MEDIA" -> BitmapDescriptorFactory.HUE_ORANGE
                "DIFICIL" -> BitmapDescriptorFactory.HUE_RED
                else -> BitmapDescriptorFactory.HUE_BLUE
            }

            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(route.name)
                    .snippet("${route.distanceKm} km - ${route.difficulty}")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )
            marker?.let {
                it.tag = routeId
                markersByRouteId[routeId] = it
                addedCount++
            }
        }

        Log.d(TAG, "Marcadores en mapa: ${markersByRouteId.size} (añadidos: $addedCount, sin GPS: $skippedNoGPS)")

        if (adjustCamera && routes.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            var included = false
            routes.forEach { route ->
                if (route.hasValidGPS()) {
                    builder.include(LatLng(route.getStartLat(), route.getStartLng()))
                    included = true
                }
            }
            if (included) {
                try {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
                    hasAdjustedCameraOnce = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error ajustando cámara: ${e.message}")
                }
            }
        }
    }

    // =========================================================
    // NAVEGACIÓN Y ARGUMENTOS
    // =========================================================
    private fun navigateToRouteDetail(route: Route) {
        try {
            sheetBehaviorRoutes.state = BottomSheetBehavior.STATE_COLLAPSED
            val action = MapasFragmentDirections.actionNavMapsToRouteDetailFragment(route.id)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al abrir el detalle de la ruta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getArgumentsData() {
        arguments?.let { args ->
            routeName = args.getString("routeName")
            startLat = args.getFloat("startLat", 0f).toDouble()
            startLng = args.getFloat("startLng", 0f).toDouble()
            endLat = args.getFloat("endLat", 0f).toDouble()
            endLng = args.getFloat("endLng", 0f).toDouble()
            difficultyFromArgs = args.getString("difficulty")

            val pointsArray = args.getFloatArray("routePoints")
            if (pointsArray != null && pointsArray.size >= 2) {
                routePoints = ArrayList()
                for (i in pointsArray.indices step 2) {
                    routePoints?.add(LatLng(pointsArray[i].toDouble(), pointsArray[i + 1].toDouble()))
                }
            }
        }
    }

    private fun setupActionButtons() {
        binding.fabMyLocation.setOnClickListener { checkLocationPermissionAndShow() }
        binding.btnZoomIn.setOnClickListener { googleMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.btnZoomOut.setOnClickListener { googleMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
    }

    // =========================================================
    // ✅ onMapReady (CORREGIDO)
    // =========================================================
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d(TAG, "🗺️ onMapReady llamado, allRoutes=${allRoutes.size}, filteredRoutes=${filteredRoutes.size}")

        markersByRouteId.clear()
        lastWantedIds = emptySet()

        googleMap?.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isMapToolbarEnabled = false
            mapType = GoogleMap.MAP_TYPE_TERRAIN
        }

        if (hasLocationPermission()) {
            enableMyLocation()
            getUserLocation()
        }

        googleMap?.setOnMarkerClickListener { marker ->
            val routeId = marker.tag as? String
            routeId?.let { id ->
                allRoutes.find { it.id == id }?.let { route ->
                    navigateToRouteDetail(route)
                }
            }
            true
        }

        // ✅ AQUÍ ESTÁ LA CURA:
        googleMap?.setOnMapClickListener { latLng ->
            lifecycleScope.launch {
                val label = reverseGeocodeToLabel(latLng) ?: "${latLng.latitude}, ${latLng.longitude}"

                // 🚫 NO ponemos el label en el buscador (porque filtra y deja 0)
                // ✅ En su lugar: buscamos cerca y mostramos el label como info
                moveCenterAndLoadNearby(latLng, label)
            }
        }

        when {
            filteredRoutes.isNotEmpty() -> {
                Log.d(TAG, "onMapReady: Repintando ${filteredRoutes.size} rutas filtradas")
                updateMarkersOnMap(filteredRoutes, adjustCamera = !hasAdjustedCameraOnce)
                updateDisplayedRoutes()
            }
            allRoutes.isNotEmpty() -> {
                Log.d(TAG, "onMapReady: Aplicando filtros a ${allRoutes.size} rutas")
                applyActiveFilters(adjustCamera = !hasAdjustedCameraOnce)
            }
            else -> Log.d(TAG, "onMapReady: Sin rutas aún, esperando datos del servidor")
        }

        if (routePoints != null && routePoints!!.isNotEmpty()) {
            drawRoute()
        } else if (startLat != 0.0 && startLng != 0.0) {
            showSinglePoint()
        } else {
            showDefaultLocation()
        }
    }

    private fun drawRoute() {
        routePoints?.let { points ->
            currentPolyline?.remove()
            currentPolyline = googleMap?.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .width(12f)
                    .color(getDifficultyColor(difficultyFromArgs))
                    .geodesic(true)
            )

            if (startLat != 0.0 && startLng != 0.0) {
                startMarker?.remove()
                startMarker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(LatLng(startLat, startLng))
                        .title("🚩 Inicio")
                        .snippet(routeName ?: "Punto de inicio")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }

            if (endLat != 0.0 && endLng != 0.0) {
                endMarker?.remove()
                endMarker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(LatLng(endLat, endLng))
                        .title("🏁 Final")
                        .snippet("Punto final")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }
        }
    }

    private fun showSinglePoint() {
        val punto = LatLng(startLat, startLng)
        googleMap?.addMarker(MarkerOptions().position(punto).title(routeName ?: "Ubicación"))
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(punto, 14f))
    }

    private fun showDefaultLocation() {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(40.4168, -3.7038), 6f))
    }

    private fun calculateRealDistanceToStart() {
        if (startLat == 0.0 || startLng == 0.0) return

        val fineGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                userLocation = it
                drawNavigationLine(currentLat = it.latitude, currentLng = it.longitude)
            }
        }
    }

    private fun drawNavigationLine(currentLat: Double, currentLng: Double) {
        navigationPolyline?.remove()
        navigationPolyline = googleMap?.addPolyline(
            PolylineOptions()
                .add(LatLng(currentLat, currentLng), LatLng(startLat, startLng))
                .width(8f)
                .color(Color.parseColor("#2196F3"))
                .geodesic(true)
                .pattern(listOf(Dot(), Gap(20f)))
        )
    }

    private fun getDifficultyColor(diff: String?) = when (normalizeDifficulty(diff ?: "")) {
        "FACIL" -> Color.parseColor("#4CAF50")
        "MEDIA" -> Color.parseColor("#FF9800")
        "DIFICIL" -> Color.parseColor("#F44336")
        else -> Color.parseColor("#2196F3")
    }

    // =========================================================
    // UTILIDADES
    // =========================================================
    private fun normalizeDifficulty(raw: String): String {
        val s = normalizeText(raw).uppercase()
        return when {
            s.contains("FACIL") || s.contains("FÁCIL") -> "FACIL"
            s.contains("MODERADA") || s.contains("MEDIA") -> "MEDIA"
            s.contains("DIFICIL") || s.contains("DIFÍCIL") -> "DIFICIL"
            else -> s
        }
    }

    private fun normalizeType(raw: String): String {
        val s = normalizeText(raw).uppercase()
        return when {
            s.contains("VIA") && s.contains("VERDE") -> "VIA_VERDE"
            s == "GR" -> "GR"
            s == "PR" -> "PR"
            s == "SL" -> "SL"
            else -> s
        }
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
    }

    // =========================================================
    // PERMISOS Y UBICACIÓN
    // =========================================================
    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun checkLocationPermissionAndShow() {
        if (hasLocationPermission()) {
            centerOnCurrentLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocation() {
        if (!hasLocationPermission()) return
        try {
            googleMap?.isMyLocationEnabled = true
        } catch (_: SecurityException) {}
    }

    private fun getUserLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                userLocation = location
            }
        } catch (_: SecurityException) {}
    }

    private fun centerOnCurrentLocation() {
        val fineGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
    }

    // =========================================================
    // ✅ CLAVE: mover centro y cargar cerca SIN FILTRAR POR TEXTO
    // =========================================================
    private fun moveCenterAndLoadNearby(latLng: LatLng, label: String? = null) {
        currentCenterLatLng = latLng

        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11f))

        hasAdjustedCameraOnce = false
        currentDisplayCount = PAGE_SIZE

        // ✅ IMPORTANTE: limpiar el buscador para que NO filtre rutas por texto
        suppressSearchCallback = true
        binding.etSearch.setText("")
        suppressSearchCallback = false

        viewModel.loadNearbyRoutes(
            lat = latLng.latitude,
            lng = latLng.longitude,
            radiusKm = maxDistanceKm
        )

        if (!label.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Rutas cerca de: $label", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun geocodeToLatLng(query: String): LatLng? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext())
                val results: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
                    geocoderGetFromLocationName33(geocoder, query, 1)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 1).orEmpty()
                }

                val a = results.firstOrNull() ?: return@withContext null
                LatLng(a.latitude, a.longitude)
            } catch (e: Exception) {
                Log.e(TAG, "Geocode error: ${e.message}", e)
                null
            }
        }
    }

    @androidx.annotation.RequiresApi(33)
    private suspend fun geocoderGetFromLocationName33(
        geocoder: Geocoder,
        name: String,
        maxResults: Int
    ): List<Address> = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocationName(name, maxResults) { list ->
            cont.resume(list ?: emptyList())
        }
    }

    private suspend fun reverseGeocodeToLabel(latLng: LatLng): String? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext())
                val results: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
                    geocoderGetFromLocation33(geocoder, latLng.latitude, latLng.longitude, 1)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1).orEmpty()
                }

                val a = results.firstOrNull() ?: return@withContext null

                val locality = a.locality
                val subAdmin = a.subAdminArea
                val admin = a.adminArea

                listOfNotNull(locality, subAdmin, admin)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { null }
            } catch (e: Exception) {
                Log.e(TAG, "Reverse geocode error: ${e.message}", e)
                null
            }
        }
    }

    @androidx.annotation.RequiresApi(33)
    private suspend fun geocoderGetFromLocation33(
        geocoder: Geocoder,
        lat: Double,
        lng: Double,
        maxResults: Int
    ): List<Address> = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(lat, lng, maxResults) { list ->
            cont.resume(list ?: emptyList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }
}
