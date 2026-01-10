package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senderlink.app.model.Route
import com.senderlink.app.repository.RouteRepository
import kotlinx.coroutines.launch
import com.senderlink.app.network.RouteDetailResponse

class RouteDetailViewModel : ViewModel() {

    private val repository = RouteRepository()

    private val _route = MutableLiveData<Route?>()
    val route: LiveData<Route?> = _route

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun loadRouteById(routeId: String) {
        _isLoading.value = true
        _error.value = null

        Log.d("ROUTE_DETAIL_VM", "Cargando ruta con ID: $routeId")

        viewModelScope.launch {
            try {
                val response: RouteDetailResponse = repository.getRouteById(routeId)
                _route.value = response.route
                Log.d("ROUTE_DETAIL_VM", "Ruta cargada: ${response.route.name}")
            } catch (e: Exception) {
                val msg = "Error al cargar la ruta: ${e.message}"
                _error.value = msg
                Log.e("ROUTE_DETAIL_VM", msg, e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
