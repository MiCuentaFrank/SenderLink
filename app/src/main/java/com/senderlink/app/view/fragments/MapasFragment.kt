package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentMapasBinding
class MapasFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapasBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var lat = 0.0
    private var lng = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lat = arguments?.getDouble("latitud") ?: 0.0
        lng = arguments?.getDouble("longitud") ?: 0.0

        // Crear y montar manualmente el MAP FRAGMENT
        val mapFragment = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction()
            .replace(R.id.mapContainer, mapFragment)
            .commit()

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        val punto = LatLng(lat, lng)

        googleMap?.addMarker(
            MarkerOptions()
                .position(punto)
                .title("Ubicación de la ruta")
        )

        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(punto, 14f))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
