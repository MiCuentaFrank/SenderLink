package com.senderlink.app.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senderlink.app.model.Route
import com.senderlink.app.repository.RouteRepository
import kotlinx.coroutines.launch

/**
 * 🏠 HomeViewModel - COMPATIBLE con tu RouteRepository
 *
 * - Destacadas: getFeaturedRoutes(page, limit)
 * - Recientes: getAllRoutes(page, limit)
 * - Sin skip, sin data, sin message
 */
class HomeViewModel : ViewModel() {

    private val repository = RouteRepository()
    private val TAG = "HomeViewModel"

    // ==============================
    // DESTACADAS
    // ==============================
    private val _featuredRoutes = MutableLiveData<List<Route>>(emptyList())
    val featuredRoutes: LiveData<List<Route>> get() = _featuredRoutes

    private var featuredCache: List<Route>? = null
    private var featuredPage = 1
    private val FEATURED_PAGE_SIZE = 6

    // ==============================
    // RECIENTES
    // ==============================
    private val _routes = MutableLiveData<List<Route>>(emptyList())
    val routes: LiveData<List<Route>> get() = _routes

    // ==============================
    // ESTADOS
    // ==============================
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> get() = _error

    // ==============================
    // CARGA PRINCIPAL
    // ==============================
    fun loadAllData(context: Context) {
        viewModelScope.launch {
            try {
                _error.value = null
                _isLoading.value = true

                Log.d(TAG, "⚡ Cargando destacadas...")
                loadFeaturedRoutesInternal()

                Log.d(TAG, "⚡ Cargando recientes...")
                loadRecentRoutesInternal(limit = 10)

                Log.d(TAG, "✅ Carga completa")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando datos: ${e.message}", e)
                _error.value = "Error cargando rutas"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==============================
    // DESTACADAS (interno)
    // ==============================
    private suspend fun loadFeaturedRoutesInternal() {
        // ✅ Usar caché si existe
        featuredCache?.let { cache ->
            _featuredRoutes.value = cache
            Log.d(TAG, "📦 Usando caché de destacadas: ${cache.size} rutas")
            return
        }

        featuredPage = 1
        val response = repository.getFeaturedRoutes(page = featuredPage, limit = FEATURED_PAGE_SIZE)

        val list = response.routes ?: emptyList()
        featuredCache = list
        _featuredRoutes.value = list

        Log.d(TAG, "✅ Destacadas cargadas: ${list.size} rutas (page=$featuredPage)")
    }

    // ==============================
    // RECIENTES (interno)
    // ==============================
    private suspend fun loadRecentRoutesInternal(limit: Int = 10) {
        // En tu API: getAllRoutes(page, limit)
        val response = repository.getAllRoutes(page = 1, limit = limit)

        val list = response.routes ?: emptyList()
        _routes.value = list

        Log.d(TAG, "✅ Recientes cargadas: ${list.size} rutas")
    }

    // ==============================
    // PAGINACIÓN DESTACADAS
    // ==============================
    fun loadMoreFeaturedRoutes() {
        // Evitar cargas múltiples
        if (_isLoading.value == true) return

        viewModelScope.launch {
            try {
                _error.value = null
                _isLoading.value = true

                featuredPage += 1
                Log.d(TAG, "📄 Cargando más destacadas... page=$featuredPage")

                val response = repository.getFeaturedRoutes(page = featuredPage, limit = FEATURED_PAGE_SIZE)
                val newRoutes = response.routes ?: emptyList()

                if (newRoutes.isEmpty()) {
                    Log.d(TAG, "⚠️ No hay más rutas destacadas")
                    return@launch
                }

                val current = _featuredRoutes.value ?: emptyList()
                _featuredRoutes.value = current + newRoutes

                Log.d(TAG, "✅ Añadidas ${newRoutes.size}. Total: ${_featuredRoutes.value?.size}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando más destacadas: ${e.message}", e)
                _error.value = "Error cargando más destacadas"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==============================
    // OTROS
    // ==============================
    fun markRouteAsRecent(context: Context, route: Route) {
        Log.d(TAG, "📍 Ruta marcada como reciente: ${route.name}")
        // Aquí luego metes persistencia si quieres
    }

    fun refresh(context: Context) {
        featuredCache = null
        featuredPage = 1
        loadAllData(context)
    }
}
