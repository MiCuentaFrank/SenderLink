package com.senderlink.app.view.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentActiveTrackingBinding
import com.senderlink.app.model.Route
import com.senderlink.app.utils.DistanceFormatter
import com.senderlink.app.viewmodel.RouteDetailViewModel
import com.senderlink.app.viewmodel.TrackingViewModel
import kotlinx.coroutines.launch

class ActiveTrackingFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentActiveTrackingBinding? = null
    private val binding get() = _binding!!

    private val args: ActiveTrackingFragmentArgs by navArgs()
    private val trackingViewModel: TrackingViewModel by activityViewModels()
    private val routeDetailViewModel: RouteDetailViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var userMarker: Marker? = null
    private var currentRoute: Route? = null
    private var completionHandled = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActiveTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as SupportMapFragment?
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction().add(R.id.mapContainer, it).commit()
            }
        mapFragment.getMapAsync(this)

        // Cargar ruta
        routeDetailViewModel.loadRouteById(args.routeId)
        routeDetailViewModel.route.observe(viewLifecycleOwner) { route ->
            if (route != null) {
                currentRoute = route
                binding.tvRouteName.text = route.name
                googleMap?.let { drawRoute(it, route) }
                updateDistanceRemaining(0.0)
            }
        }

        // Iniciar seguimiento si no está corriendo
        if (!trackingViewModel.isRunning.value) {
            trackingViewModel.startTracking(requireContext())
        }

        observeTracking()

        binding.btnFinishRoute.setOnClickListener { attemptFinish() }
    }

    private fun observeTracking() {
        viewLifecycleOwner.lifecycleScope.launch {
            trackingViewModel.currentLocation.collect { latLng ->
                if (latLng != null) {
                    updateUserMarker(latLng)
                    checkOffTrack(latLng)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            trackingViewModel.elapsedSeconds.collect { secs ->
                binding.tvElapsedTime.text = formatTime(secs)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            trackingViewModel.totalDistanceM.collect { dist ->
                binding.tvDistanceTraveled.text = DistanceFormatter.format(dist / 1000.0)
                updateDistanceRemaining(dist)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            trackingViewModel.completeRouteResult.collect { state ->
                when (state) {
                    is TrackingViewModel.CompleteRouteState.Success -> {
                        if (!completionHandled) {
                            completionHandled = true
                            trackingViewModel.resetCompleteResult()
                            showCompletionDialog(state.xpGained, state.newLevel, state.rankTitle)
                        }
                    }
                    is TrackingViewModel.CompleteRouteState.Error -> {
                        if (!completionHandled) {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            trackingViewModel.resetCompleteResult()
                            findNavController().popBackStack()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun updateUserMarker(latLng: LatLng) {
        val map = googleMap ?: return
        userMarker?.remove()
        userMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Tu posición")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun checkOffTrack(userLatLng: LatLng) {
        val coords = currentRoute?.geometry?.coordinates ?: return
        val minDist = coords.minOfOrNull { coord ->
            val r = FloatArray(1)
            Location.distanceBetween(userLatLng.latitude, userLatLng.longitude, coord[1], coord[0], r)
            r[0].toDouble()
        } ?: return
        binding.tvOffTrack.visibility = if (minDist > 100) View.VISIBLE else View.GONE
    }

    private fun updateDistanceRemaining(traveledM: Double) {
        val r = currentRoute ?: return
        val remaining = (r.distanceKm * 1000.0 - traveledM).coerceAtLeast(0.0)
        binding.tvDistanceRemaining.text = DistanceFormatter.format(remaining / 1000.0)
    }

    private fun attemptFinish() {
        val r   = currentRoute ?: return
        val pos = trackingViewModel.currentLocation.value

        if (pos == null) {
            Toast.makeText(requireContext(), "Esperando señal GPS...", Toast.LENGTH_SHORT).show()
            return
        }

        // Si empieza al revés, el "final" es el punto de inicio original (y viceversa)
        val checkLat: Double
        val checkLng: Double
        if (args.reversed) {
            checkLat = r.getStartLat()
            checkLng = r.getStartLng()
        } else {
            checkLat = r.getEndLat()
            checkLng = r.getEndLng()
        }

        // Si la ruta no tiene punto de destino definido, terminar sin XP
        if (checkLat == 0.0 && checkLng == 0.0) {
            finishRoute(verified = false)
            return
        }

        val dist = FloatArray(1)
        Location.distanceBetween(pos.latitude, pos.longitude, checkLat, checkLng, dist)

        if (dist[0] <= 200f) {
            finishRoute(verified = true)
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("¿Terminar ruta?")
                .setMessage("Estás a ${dist[0].toInt()} m del final de la ruta.\n¿Seguro que quieres terminar sin completarla?")
                .setPositiveButton("Sí, salir") { _, _ -> finishRoute(verified = false) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun finishRoute(verified: Boolean) {
        trackingViewModel.stopTracking(requireContext())
        if (verified) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            trackingViewModel.completeRoute(uid, args.routeId)
        } else {
            // Salida anticipada: no se registra ni se dan XP
            findNavController().popBackStack()
        }
    }

    private fun showCompletionDialog(xpGained: Int, newLevel: Int, rankTitle: String) {
        val xpText = if (xpGained > 0) "\n\n+$xpGained XP ganados · Nivel $newLevel · $rankTitle" else ""
        AlertDialog.Builder(requireContext())
            .setTitle("¡Ruta completada!")
            .setMessage("Has terminado la ruta con éxito.$xpText")
            .setPositiveButton("Genial") { _, _ -> findNavController().popBackStack() }
            .setCancelable(false)
            .show()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (hasLocationPermission()) {
            @SuppressLint("MissingPermission")
            map.isMyLocationEnabled = true
        }
        currentRoute?.let { drawRoute(map, it) }
    }

    private fun drawRoute(map: GoogleMap, route: Route) {
        val coords = route.geometry?.coordinates ?: return
        val points = coords.map { LatLng(it[1], it[0]) }
        if (points.size < 2) return

        map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(ContextCompat.getColor(requireContext(), R.color.sl_primary))
                .width(10f)
                .geodesic(true)
        )
        map.addMarker(MarkerOptions().position(points.first()).title("Inicio"))
        map.addMarker(MarkerOptions().position(points.last()).title("Fin"))

        val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
