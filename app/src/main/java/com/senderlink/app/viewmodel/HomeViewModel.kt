package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.senderlink.app.model.Route
import com.senderlink.app.network.RetrofitClient
import com.senderlink.app.network.RouteResponse
import com.senderlink.app.network.RouteService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeViewModel : ViewModel() {

    private val routeService: RouteService =
        RetrofitClient.instance.create(RouteService::class.java)

    private val _routes = MutableLiveData<List<Route>>()
    val routes: LiveData<List<Route>> get() = _routes

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    fun loadRoutes() {
        _isLoading.value = true
        _error.value = null

        routeService.getRoutes().enqueue(object : Callback<RouteResponse> {
            override fun onResponse(
                call: Call<RouteResponse>,
                response: Response<RouteResponse>
            ) {
                _isLoading.value = false

                if (response.isSuccessful) {
                    _routes.value = response.body()?.routes ?: emptyList()
                } else {
                    _error.value = "Error ${response.code()}"
                }
                Log.d("HOME_VM", "Rutas recibidas: ${response.body()?.routes?.size}")

            }

            override fun onFailure(call: Call<RouteResponse>, t: Throwable) {
                _isLoading.value = false
                _error.value = t.message
            }
        })
    }
}
