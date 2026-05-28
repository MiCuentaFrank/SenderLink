package com.senderlink.app.view.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentCreateRouteBinding
import com.senderlink.app.utils.DistanceFormatter
import com.senderlink.app.viewmodel.TrackingViewModel
import kotlinx.coroutines.launch

class CreateRouteFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentCreateRouteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackingViewModel by activityViewModels()
    private var googleMap: GoogleMap? = null
    private var firstCenter = true

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            enableMapMyLocation()
            doStartRecording()
        } else {
            Toast.makeText(requireContext(), "Necesitas permisos de ubicación", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as SupportMapFragment?
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction().add(R.id.mapContainer, it).commit()
            }
        mapFragment.getMapAsync(this)

        binding.btnStartPause.setOnClickListener { onStartPauseClicked() }
        binding.btnStop.setOnClickListener { onStopClicked() }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.trackPoints.collect { points ->
                binding.tvPoints.text = points.size.toString()
                drawPolyline(points)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalDistanceM.collect { dist ->
                binding.tvDistance.text = DistanceFormatter.format(dist / 1000.0)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.elapsedSeconds.collect { secs ->
                binding.tvDuration.text = formatTime(secs)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentLocation.collect { latLng ->
                if (latLng != null && firstCenter) {
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    firstCenter = false
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isRunning.collect { running ->
                binding.btnStop.isEnabled = running
                if (!running && !viewModel.isPaused.value) {
                    binding.btnStartPause.text = "Iniciar"
                    binding.recordingIndicator.visibility = View.GONE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isPaused.collect { paused ->
                binding.btnStartPause.text = when {
                    !viewModel.isRunning.value -> "Iniciar"
                    paused -> "Reanudar"
                    else   -> "Pausar"
                }
                binding.recordingIndicator.visibility =
                    if (viewModel.isRunning.value && !paused) View.VISIBLE else View.GONE
            }
        }
    }

    private fun onStartPauseClicked() {
        when {
            !viewModel.isRunning.value -> {
                if (hasLocationPermission()) doStartRecording()
                else locationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            viewModel.isPaused.value -> viewModel.resumeRecording(requireContext())
            else                     -> viewModel.pauseRecording(requireContext())
        }
    }

    private fun onStopClicked() {
        if (viewModel.trackPoints.value.size < 2) {
            Toast.makeText(requireContext(), "Graba al menos 2 puntos GPS antes de finalizar", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.stopRecording(requireContext())
        findNavController().navigate(R.id.action_createRouteFragment_to_saveRouteFragment)
    }

    private fun doStartRecording() {
        firstCenter = true
        viewModel.startRecording(requireContext())
        binding.btnStartPause.text = "Pausar"
        binding.btnStop.isEnabled = true
        binding.recordingIndicator.visibility = View.VISIBLE
    }

    private fun drawPolyline(points: List<LatLng>) {
        val map = googleMap ?: return
        map.clear()
        if (points.size >= 2) {
            map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(ContextCompat.getColor(requireContext(), R.color.sl_primary))
                    .width(10f)
                    .geodesic(true)
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (hasLocationPermission()) {
            enableMapMyLocation()
            centerOnUser()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMapMyLocation() {
        googleMap?.isMyLocationEnabled = true
    }

    @SuppressLint("MissingPermission")
    private fun centerOnUser() {
        LocationServices.getFusedLocationProviderClient(requireContext())
            .lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f))
                }
            }
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
