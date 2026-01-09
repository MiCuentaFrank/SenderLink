package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.senderlink.app.model.Route
import com.senderlink.app.network.RetrofitClient
import com.senderlink.app.network.RouteService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RouteDetailViewModel : ViewModel() {

    private val routeService = RetrofitClient.instance
        .create(RouteService::class.java)

    private val _route = MutableLiveData<Route?>()
    val route: LiveData<Route?> = _route

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadRouteById(routeId: String) {
        _isLoading.value = true
        _error.value = null

        Log.d("ROUTE_DETAIL_VM", "Cargando ruta con ID: $routeId")

        routeService.getRouteById(routeId)
            .enqueue(object : Callback<RouteDetailResponse> {
                override fun onResponse(
                    call: Call<RouteDetailResponse>,
                    response: Response<RouteDetailResponse>
                ) {
                    _isLoading.value = false

                    if (response.isSuccessful) {
                        val routeData = response.body()?.route
                        _route.value = routeData
                        Log.d("ROUTE_DETAIL_VM", "Ruta cargada: ${routeData?.name}")
                    } else {
                        val errorMsg = "Error al cargar la ruta: ${response.code()}"
                        _error.value = errorMsg
                        Log.e("ROUTE_DETAIL_VM", errorMsg)
                    }
                }

                override fun onFailure(call: Call<RouteDetailResponse>, t: Throwable) {
                    _isLoading.value = false
                    val errorMsg = "Error de red: ${t.message}"
                    _error.value = errorMsg
                    Log.e("ROUTE_DETAIL_VM", errorMsg, t)
                }
            })
    }
}

// Data class para la respuesta del backend
data class RouteDetailResponse(
    val ok: Boolean,
    val route: Route
)
