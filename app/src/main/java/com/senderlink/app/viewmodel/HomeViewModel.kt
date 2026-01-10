package com.senderlink.app.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senderlink.app.model.Route
import com.senderlink.app.repository.RouteRepository
import com.senderlink.app.utils.HomeDataStore
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🏠 HomeViewModel
 *
 * Maneja:
 * - Rutas destacadas (del servidor)
 * - Rutas recientes (guardadas localmente)
 */
class HomeViewModel : ViewModel() {

    private val repository = RouteRepository()

    // LiveData públicos
    private val _routes = MutableLiveData<List<Route>>(emptyList())
    val routes: LiveData<List<Route>> = _routes

    private val _featuredRoutes = MutableLiveData<List<Route>>(emptyList())
    val featuredRoutes: LiveData<List<Route>> = _featuredRoutes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // Control de carga
    private var isLoadingFeatured = false

    /**
     * ⭐ Carga rutas destacadas desde el servidor
     */
    fun loadFeaturedRoutes(reset: Boolean = false) {
        if (isLoadingFeatured) return

        isLoadingFeatured = true
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // Usa el endpoint específico /api/routes/featured
                val response = repository.getFeaturedRoutes(limit = 50)

                if (response.ok) {
                    val shuffled = response.routes.shuffled()
                    _featuredRoutes.value = shuffled
                    Log.d("HOME_VM", "✅ Rutas destacadas: ${response.count}")
                } else {
                    _error.value = "Error al cargar destacadas"
                }

            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.message}"
                Log.e("HOME_VM", "❌ Error: ${e.message}", e)
            } finally {
                isLoadingFeatured = false
                _isLoading.value = false
            }
        }
    }

    // ==========================================
    // 🕐 RUTAS RECIENTES
    // ==========================================

    data class RecentRouteLite(
        val id: String,
        val name: String,
        val coverImage: String?,
        val difficulty: String?,
        val distanceKm: Double?
    )

    fun markRouteAsRecent(context: Context, route: Route, maxItems: Int = 20) {
        val current = (_routes.value ?: emptyList()).toMutableList()
        current.removeAll { it.id == route.id }
        current.add(0, route)

        if (current.size > maxItems) {
            current.subList(maxItems, current.size).clear()
        }

        _routes.value = current

        viewModelScope.launch {
            val json = liteListToJson(current.map { toLite(it) })
            HomeDataStore.saveRecentsJson(context, json)
        }
    }

    fun loadRecentsFromStorage(context: Context) {
        viewModelScope.launch {
            try {
                val json = HomeDataStore.loadRecentsJson(context)
                val liteList = jsonToLiteList(json)
                _routes.value = liteList.map { liteToRoute(it) }
                Log.d("HOME_VM", "Recientes cargadas: ${liteList.size}")
            } catch (e: Exception) {
                Log.e("HOME_VM", "Error cargando recientes: ${e.message}")
            }
        }
    }

    // Helpers de conversión
    private fun toLite(route: Route) = RecentRouteLite(
        id = route.id,
        name = route.name,
        coverImage = route.coverImage,
        difficulty = route.difficulty,
        distanceKm = route.distanceKm
    )

    private fun liteListToJson(list: List<RecentRouteLite>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("coverImage", r.coverImage ?: JSONObject.NULL)
                put("difficulty", r.difficulty ?: JSONObject.NULL)
                put("distanceKm", r.distanceKm ?: JSONObject.NULL)
            })
        }
        return arr.toString()
    }

    private fun jsonToLiteList(json: String): List<RecentRouteLite> {
        if (json.isBlank() || json == "[]") return emptyList()

        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            RecentRouteLite(
                id = obj.getString("id"),
                name = obj.getString("name"),
                coverImage = if (obj.isNull("coverImage")) null else obj.getString("coverImage"),
                difficulty = if (obj.isNull("difficulty")) null else obj.getString("difficulty"),
                distanceKm = if (obj.isNull("distanceKm")) null else obj.getDouble("distanceKm")
            )
        }
    }

    private fun liteToRoute(lite: RecentRouteLite) = Route(
        id = lite.id,
        type = "recent",
        source = "local",
        name = lite.name,
        description = "",
        coverImage = lite.coverImage ?: "",
        images = emptyList(),
        distanceKm = lite.distanceKm ?: 0.0,
        difficulty = lite.difficulty ?: "",
        featured = false
    )
}