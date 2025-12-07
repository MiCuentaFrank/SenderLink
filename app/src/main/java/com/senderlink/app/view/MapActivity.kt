package com.senderlink.app.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.senderlink.app.databinding.ActivityMapBinding

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapBinding
    private var googleMap: GoogleMap? = null

    private var lat: Double = 0.0
    private var lng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recuperar coordenadas enviadas desde RouteDetailFragment
        lat = intent.getDoubleExtra("lat", 0.0)
        lng = intent.getDoubleExtra("lng", 0.0)

        // Configurar botón atrás
        binding.btnVolver.setOnClickListener {
            finish() // Cierra la actividad y vuelve al fragment anterior sin romper Navigation
        }

        // Cargar el mapa
        val mapFragment =
            supportFragmentManager.findFragmentById(binding.map.id) as SupportMapFragment
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

        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(punto, 15f))
    }
}
