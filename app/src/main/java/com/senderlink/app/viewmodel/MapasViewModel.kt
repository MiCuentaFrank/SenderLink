package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.senderlink.app.model.Route
import com.senderlink.app.network.RetrofitClient
import com.senderlink.app.network.RoutesNearResponse
import com.senderlink.app.network.RouteService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MapasViewModel : ViewModel() {

    private val routeService: RouteService =
        RetrofitClient.instance.create(RouteService::class.java)

    private val _allRoutes = MutableLiveData<List<Route>>(emptyList())
    val allRoutes: LiveData<List<Route>> = _allRoutes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun clearError() {
        _error.value = null
    }

    fun loadNearbyRoutes(lat: Double, lng: Double, radiusKm: Float) {
        _isLoading.value = true
        val radioMeters = (radiusKm * 1000).toInt()
        routeService.getRoutesNearMe(lat = lat, lng = lng, radio = radioMeters, limit = 100)
            .enqueue(object : Callback<RoutesNearResponse> {
                override fun onResponse(
                    call: Call<RoutesNearResponse>,
                    response: Response<RoutesNearResponse>
                ) {
                    _isLoading.value = false
                    if (response.isSuccessful) {
                        _allRoutes.value = response.body()?.routes ?: emptyList()
                    } else {
                        _error.value = "Error al cargar rutas (${response.code()})"
                    }
                }

                override fun onFailure(call: Call<RoutesNearResponse>, t: Throwable) {
                    _isLoading.value = false
                    _error.value = "Sin conexión. Comprueba tu internet."
                }
            })
    }
}
