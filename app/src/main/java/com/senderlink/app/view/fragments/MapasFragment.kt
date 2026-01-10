package com.senderlink.app.view.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.Gson
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentMapasBinding
import okhttp3.*
import java.io.IOException

class MapasFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapasBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val httpClient = OkHttpClient()

    // ⚠️ Mejor luego: BuildConfig / local.properties
    private val GOOGLE_API_KEY = "TU_API_KEY_AQUI"

    private lateinit var sheetBehavior: BottomSheetBehavior<View>

    // Datos de la ruta
    private var routeName: String? = null
    private var startLat: Double = 0.0
    private var startLng: Double = 0.0
    private var endLat: Double = 0.0
    private var endLng: Double = 0.0
    private var routePoints: ArrayList<LatLng>? = null
    private var distanceKm: Double = 0.0
    private var difficulty: String? = null

    private var userLocation: Location? = null

    private var currentPolyline: Polyline? = null
    private var navigationPolyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                enableMyLocation()
                calculateRealDistanceToStart()
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
        getArgumentsData()

        setupBottomSheet()
        setupActionButtons()
        setupTopUi()

        val mapFragment = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction()
            .replace(R.id.mapContainer, mapFragment)
            .commit()

        mapFragment.getMapAsync(this)
    }

    private fun setupBottomSheet() {
        sheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        sheetBehavior.isHideable = false
        sheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.sheet_peek_height)
    }

    private fun setupTopUi() {
        // Si quieres: mostrar el nombre de la ruta como "hint"
        binding.etSearch.setText(routeName ?: "")

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            // Por ahora solo UI. Luego lo hacemos funcional si quieres.
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            when (id) {
                R.id.chipEasy -> Log.d("MAPAS_FRAGMENT", "Filtro: fácil")
                R.id.chipModerate -> Log.d("MAPAS_FRAGMENT", "Filtro: media")
                R.id.chipHard -> Log.d("MAPAS_FRAGMENT", "Filtro: difícil")
                R.id.chipNearby -> Log.d("MAPAS_FRAGMENT", "Filtro: cerca")
            }
        }
    }

    private fun getArgumentsData() {
        arguments?.let { args ->
            routeName = args.getString("routeName")
            startLat = args.getDouble("startLat", 0.0)
            startLng = args.getDouble("startLng", 0.0)
            endLat = args.getDouble("endLat", 0.0)
            endLng = args.getDouble("endLng", 0.0)
            distanceKm = args.getDouble("distanceKm", 0.0)
            difficulty = args.getString("difficulty")

            val pointsArray = args.getDoubleArray("routePoints")
            if (pointsArray != null && pointsArray.size >= 2) {
                routePoints = ArrayList()
                for (i in pointsArray.indices step 2) {
                    routePoints?.add(LatLng(pointsArray[i], pointsArray[i + 1]))
                }
            }

            Log.d("MAPAS_FRAGMENT", "Ruta: $routeName, Puntos: ${routePoints?.size ?: 0}")
        }
    }

    private fun setupActionButtons() {
        binding.fabMyLocation.setOnClickListener { checkLocationPermissionAndShow() }
        binding.fabFitRoute.setOnClickListener { fitRouteInView() }

        binding.btnZoomIn.setOnClickListener { googleMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.btnZoomOut.setOnClickListener { googleMap?.animateCamera(CameraUpdateFactory.zoomOut()) }

        binding.fabStartNavigation.setOnClickListener { navigateToStart() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap?.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isMapToolbarEnabled = false
            mapType = GoogleMap.MAP_TYPE_TERRAIN
        }

        if (hasLocationPermission()) {
            enableMyLocation()
            calculateRealDistanceToStart()
        }

        if (routePoints != null && routePoints!!.isNotEmpty()) {
            drawRoute()
            binding.fabFitRoute.visibility = View.VISIBLE
            binding.fabStartNavigation.visibility = View.VISIBLE
        } else if (startLat != 0.0 && startLng != 0.0) {
            showSinglePoint()
            binding.fabStartNavigation.visibility = View.VISIBLE
        } else {
            showDefaultLocation()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermissionAndShow() {
        when {
            hasLocationPermission() -> centerOnCurrentLocation()
            else -> locationPermissionRequest.launch(
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
        } catch (e: SecurityException) {
            Log.e("MAPAS_FRAGMENT", "Error activando ubicación: ${e.message}")
        }
    }

    private fun centerOnCurrentLocation() {
        if (!hasLocationPermission()) {
            checkLocationPermissionAndShow()
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLocation = it
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    if (startLat != 0.0 && startLng != 0.0) calculateRealDistanceToStart()
                    sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                } ?: Toast.makeText(requireContext(), "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e("MAPAS_FRAGMENT", "Error obteniendo ubicación: ${e.message}")
        }
    }

    private fun calculateRealDistanceToStart() {
        if (!hasLocationPermission()) return
        if (startLat == 0.0 || startLng == 0.0) return

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { userLoc ->
                    userLocation = userLoc

                    // Ya no mostramos distancia/tiempo en el UI
                    // Solo dibujamos la línea de navegación
                    drawNavigationLine(userLoc.latitude, userLoc.longitude)

                    sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        } catch (e: SecurityException) {
            Log.e("MAPAS_FRAGMENT", "Error calculando distancia: ${e.message}")
        }
    }

    private fun drawNavigationLine(currentLat: Double, currentLng: Double) {
        navigationPolyline?.remove()

        val polylineOptions = PolylineOptions()
            .add(LatLng(currentLat, currentLng))
            .add(LatLng(startLat, startLng))
            .width(8f)
            .color(Color.parseColor("#2196F3"))
            .geodesic(true)
            .pattern(listOf(Dot(), Gap(20f)))

        navigationPolyline = googleMap?.addPolyline(polylineOptions)
    }

    private fun drawRoute() {
        routePoints?.let { points ->
            val polylineOptions = PolylineOptions()
                .addAll(points)
                .width(12f)
                .color(getDifficultyColor())
                .geodesic(true)

            currentPolyline = googleMap?.addPolyline(polylineOptions)

            if (startLat != 0.0 && startLng != 0.0) {
                startMarker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(LatLng(startLat, startLng))
                        .title("🚩 Inicio")
                        .snippet(routeName ?: "Punto de inicio")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }

            if (endLat != 0.0 && endLng != 0.0) {
                endMarker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(LatLng(endLat, endLng))
                        .title("🏁 Final")
                        .snippet("Punto final")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }

            fitRouteInView()
        }
    }

    private fun showSinglePoint() {
        val punto = LatLng(startLat, startLng)
        googleMap?.addMarker(MarkerOptions().position(punto).title(routeName ?: "Ubicación de la ruta"))
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(punto, 14f))
    }

    private fun showDefaultLocation() {
        val defaultLocation = LatLng(40.4168, -3.7038)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 6f))
        if (hasLocationPermission()) centerOnCurrentLocation()
    }

    private fun getDifficultyColor(): Int {
        return when (difficulty?.uppercase()) {
            "FACIL", "FÁCIL" -> Color.parseColor("#4CAF50")
            "MODERADA", "MEDIA" -> Color.parseColor("#FF9800")
            "DIFICIL", "DIFÍCIL" -> Color.parseColor("#F44336")
            else -> Color.parseColor("#2196F3")
        }
    }

    private fun fitRouteInView() {
        routePoints?.let { points ->
            if (points.isEmpty()) return
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(it) }
            userLocation?.let { builder.include(LatLng(it.latitude, it.longitude)) }

            val bounds = builder.build()
            val padding = 150

            try {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } catch (e: Exception) {
                Log.e("MAPAS_FRAGMENT", "Error ajustando cámara: ${e.message}")
            }
        }
    }

    private fun navigateToStart() {
        if (startLat == 0.0 || startLng == 0.0) {
            Toast.makeText(requireContext(), "No hay punto de inicio disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse("google.navigation:q=$startLat,$startLng&mode=d")
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$startLat,$startLng")
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        } catch (e: Exception) {
            Log.e("MAPAS_FRAGMENT", "Error abriendo navegación: ${e.message}")
            Toast.makeText(requireContext(), "Error al abrir la navegación", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        currentPolyline = null
        navigationPolyline = null
        startMarker = null
        endMarker = null
        userLocation = null
        _binding = null
    }

    // ✅ Renombradas para no chocar con tu modelo Route real
    data class DirectionsResponse(
        val routes: List<DirectionsRoute>,
        val status: String
    )

    data class DirectionsRoute(
        val legs: List<Leg>
    )

    data class Leg(
        val distance: Distance,
        val duration: Duration
    )

    data class Distance(val text: String, val value: Int)
    data class Duration(val text: String, val value: Int)
}