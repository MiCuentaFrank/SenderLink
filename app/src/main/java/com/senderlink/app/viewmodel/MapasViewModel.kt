package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senderlink.app.model.Route
import com.senderlink.app.repository.RouteRepository
import kotlinx.coroutines.launch
import java.lang.System.console

class MapasViewModel : ViewModel() {

    private val repository = RouteRepository()

    private val _allRoutes = MutableLiveData<List<Route>>(emptyList())
    val allRoutes: LiveData<List<Route>> = _allRoutes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // Estado de Nearby (persistente)
    private var lastNearbyLat: Double? = null
    private var lastNearbyLng: Double? = null
    private var lastNearbyRadiusKm: Float = 50f

    /**
     * ✅ Buscar rutas cercanas desde el servidor.
     * NO limita la cantidad - el servidor devuelve TODAS las rutas dentro del radio.
     * La paginación se hace localmente en el Fragment.
     */
    fun loadNearbyRoutes(lat: Double, lng: Double, radiusKm: Float = 50f) {
        if (_isLoading.value == true) {
            Log.d("MAPAS_VM", "Ya hay una carga en proceso")
            return
        }

        lastNearbyLat = lat
        lastNearbyLng = lng
        lastNearbyRadiusKm = radiusKm

        _isLoading.value = true

        val nearbyLimit = 500
        val radiusMeters = (radiusKm * 1000f).toInt()
        val radiusKmInt = radiusKm.toInt().coerceAtLeast(1)


        Log.d(
            "MAPAS_VM",
            "Buscando rutas cercanas: lat=$lat, lng=$lng, radio=${radiusKm}km (meters=$radiusMeters), limit=$nearbyLimit"
        )

        viewModelScope.launch {
            try {
                // 1) Intento A: enviar METROS (lo típico en Mongo $near $maxDistance)
                val responseMeters = repository.getRoutesNearMe(
                    lat = lat,
                    lng = lng,
                    radio = radiusMeters,
                    limit = nearbyLimit
                )

                Log.d("MAPAS_VM", "Respuesta (metros): ok=${responseMeters.ok} routes=${responseMeters.routes?.size}")

                if (responseMeters.ok) {
                    val nearbyRoutes = responseMeters.routes.orEmpty()

                    // Si llegaron rutas => perfecto
                    if (nearbyRoutes.isNotEmpty()) {
                        Log.d("MAPAS_VM", "Rutas cercanas encontradas (metros): ${nearbyRoutes.size}")
                        _allRoutes.value = nearbyRoutes
                        _error.value = null
                        return@launch
                    }

                    // 2) Fallback: si vienen 0 rutas, puede que el backend espere KM (no metros)
                    Log.w("MAPAS_VM", "0 rutas con metros. Reintentando enviando KM como radio=$radiusKmInt")

                    val responseKm = repository.getRoutesNearMe(
                        lat = lat,
                        lng = lng,
                        radio = radiusKmInt, // <- OJO: ahora mandamos KM
                        limit = nearbyLimit
                    )

                    Log.d("MAPAS_VM", "Respuesta (km): ok=${responseKm.ok} routes=${responseKm.routes?.size}")

                    if (responseKm.ok) {
                        val routesKm = responseKm.routes.orEmpty()
                        Log.d("MAPAS_VM", "Rutas cercanas encontradas (km): ${routesKm.size}")
                        _allRoutes.value = routesKm
                        _error.value = null
                    } else {
                        val errorMsg = "Error: respuesta no exitosa (km fallback)"
                        Log.e("MAPAS_VM", errorMsg)
                        _error.value = errorMsg
                        _allRoutes.value = emptyList()
                    }

                } else {
                    val errorMsg = "Error: respuesta no exitosa (metros)"
                    Log.e("MAPAS_VM", errorMsg)
                    _error.value = errorMsg
                    _allRoutes.value = emptyList()
                }

            } catch (e: Exception) {
                val errorMsg = "Error al buscar rutas cercanas: ${e.message}"
                Log.e("MAPAS_VM", errorMsg, e)
                _error.value = errorMsg
                _allRoutes.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }


    /**
     * ✅ Si vuelves atrás y el Fragment se recrea, puedes llamar a esto para
     * "recuperar" lo último que estabas viendo si la lista está vacía.
     */
    fun ensureRestoredIfEmpty() {
        if (_isLoading.value == true) return
        if (!_allRoutes.value.isNullOrEmpty()) return

        val lat = lastNearbyLat
        val lng = lastNearbyLng
        if (lat != null && lng != null) {
            loadNearbyRoutes(lat, lng, lastNearbyRadiusKm)
        }
    }

    fun clearError() {
        _error.value = null
    }
}