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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🏠 HomeViewModel - OPTIMIZADO CON PAGINACIÓN REAL
 *
 * MEJORAS:
 * - ⚡ Carga paralela de recientes + destacadas
 * - 💾 Caché de rutas destacadas
 * - 🔄 Paginación infinita real (página 1, 2, 3...)
 * - ⏱️ Timestamp para invalidar caché
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

    // Control de paginación
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMorePages = true

    // Control de caché
    private var lastFeaturedLoadTime = 0L
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutos

    /**
     * ⚡ CARGA PARALELA - Recientes + Destacadas (primera página)
     */
    fun loadAllData(context: Context, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Resetear paginación si es refresh
                if (forceRefresh) {
                    resetPagination()
                }

                // ⚡ Lanzar ambas cargas EN PARALELO
                val recentsDeferred = async { loadRecentsFromStorageInternal(context) }
                val featuredDeferred = async { loadFeaturedRoutesInternal(context, forceRefresh) }

                recentsDeferred.await()
                featuredDeferred.await()

                Log.d("HOME_VM", "✅ Carga paralela completada")
            } catch (e: Exception) {
                _error.value = "Error al cargar datos: ${e.message}"
                Log.e("HOME_VM", "❌ Error en carga paralela", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * ⭐ Carga rutas destacadas - PRIMERA PÁGINA
     */
    private suspend fun loadFeaturedRoutesInternal(context: Context, forceRefresh: Boolean) {
        try {
            // 1. Verificar caché
            val now = System.currentTimeMillis()
            val cacheValid = (now - lastFeaturedLoadTime) < CACHE_DURATION_MS

            if (!forceRefresh && cacheValid && !_featuredRoutes.value.isNullOrEmpty()) {
                Log.d("HOME_VM", "💾 Usando caché (${_featuredRoutes.value?.size} rutas)")
                return
            }

            // 2. Intentar cargar desde storage local
            if (!forceRefresh) {
                val cached = loadFeaturedFromStorage(context)
                if (cached.isNotEmpty()) {
                    _featuredRoutes.value = cached
                    Log.d("HOME_VM", "💾 Del storage: ${cached.size} rutas")
                }
            }

            // 3. Cargar PÁGINA 1 desde servidor
            val response = repository.getFeaturedRoutes(page = 1, limit = 20)

            if (response.ok) {
                _featuredRoutes.value = response.routes
                lastFeaturedLoadTime = now
                // 🔁 Estado real de paginación
                currentPage = response.page
                hasMorePages = currentPage < response.pages


                Log.d(
                    "HOME_VM",
                    "📌 Featured: page=${response.page}, pages=${response.pages}, total=${response.total}"
                )


                // Guardar en storage
                saveFeaturedToStorage(context, response.routes)

                Log.d("HOME_VM", "✅ Página 1: ${response.count} rutas (total: ${response.total})")
            } else {
                _error.value = "Error al cargar destacadas"
            }

        } catch (e: Exception) {
            Log.e("HOME_VM", "❌ Error cargando página 1: ${e.message}", e)
            if (_featuredRoutes.value.isNullOrEmpty()) {
                _error.value = "Error de conexión"
            }
        }
    }

    /**
     * 🔄 CARGAR MÁS RUTAS (paginación infinita)
     * Llamado al hacer scroll
     */
    fun loadMoreFeaturedRoutes() {
        if (isLoadingMore || !hasMorePages) {
            Log.d("HOME_VM", "⏸️ No cargar más (loading=$isLoadingMore, hasMore=$hasMorePages)")
            return
        }

        isLoadingMore = true
        val nextPage = currentPage + 1

        Log.d("HOME_VM", "📄 Cargando página $nextPage...")

        viewModelScope.launch {
            try {
                val response = repository.getFeaturedRoutes(page = nextPage, limit = 20)

                if (!response.ok) {
                    Log.d("HOME_VM", "❌ Response ok=false en página $nextPage")
                    // Si falla, no avanzamos página y permitimos reintentar
                    return@launch
                }

                // ✅ Actualizamos página real desde backend
                currentPage = response.page

                // ✅ Añadimos rutas si vienen
                if (response.routes.isNotEmpty()) {
                    val current = _featuredRoutes.value?.toMutableList() ?: mutableListOf()
                    current.addAll(response.routes)
                    _featuredRoutes.value = current

                    Log.d(
                        "HOME_VM",
                        "✅ Página ${response.page}/${response.pages} cargada (+${response.count}), total=${current.size}"
                    )
                } else {
                    Log.d("HOME_VM", "📭 Página ${response.page} sin rutas (count=${response.count})")
                }

                // ✅ Decisión de si hay más páginas (regla REAL)
                hasMorePages = currentPage < response.pages

                Log.d("HOME_VM", "🧭 currentPage=$currentPage, pages=${response.pages}, hasMore=$hasMorePages")

            } catch (e: Exception) {
                Log.e("HOME_VM", "❌ Error cargando página $nextPage: ${e.message}", e)
                // Si hay error, NO marques hasMorePages=false (permite reintentar)
            } finally {
                isLoadingMore = false
            }
        }
    }



    /**
     * 🔄 Reset paginación
     */
    private fun resetPagination() {
        currentPage = 1
        hasMorePages = true
        isLoadingMore = false
    }

    /**
     * 🔄 Refresh manual
     */
    fun refresh(context: Context) {
        loadAllData(context, forceRefresh = true)
    }

    // ==========================================
    // 🕐 RUTAS RECIENTES
    // ==========================================

    private suspend fun loadRecentsFromStorageInternal(context: Context) {
        try {
            val json = HomeDataStore.loadRecentsJson(context)
            val liteList = jsonToLiteList(json)
            _routes.value = liteList.map { liteToRoute(it) }
            Log.d("HOME_VM", "📱 Recientes: ${liteList.size}")
        } catch (e: Exception) {
            Log.e("HOME_VM", "❌ Error recientes: ${e.message}")
            _routes.value = emptyList()
        }
    }

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

    // ==========================================
    // 💾 CACHÉ DE DESTACADAS
    // ==========================================

    private suspend fun saveFeaturedToStorage(context: Context, routes: List<Route>) {
        try {
            val json = featuredRoutesToJson(routes)
            HomeDataStore.saveFeaturedJson(context, json)
        } catch (e: Exception) {
            Log.e("HOME_VM", "Error guardando destacadas: ${e.message}")
        }
    }

    private suspend fun loadFeaturedFromStorage(context: Context): List<Route> {
        return try {
            val json = HomeDataStore.loadFeaturedJson(context)
            jsonToFeaturedRoutes(json)
        } catch (e: Exception) {
            Log.e("HOME_VM", "Error leyendo destacadas: ${e.message}")
            emptyList()
        }
    }

    private fun featuredRoutesToJson(routes: List<Route>): String {
        val arr = JSONArray()
        routes.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("coverImage", r.coverImage ?: JSONObject.NULL)
                put("difficulty", r.getNormalizedDifficulty())
                put("distanceKm", r.distanceKm)
            })
        }
        return arr.toString()
    }

    private fun jsonToFeaturedRoutes(json: String): List<Route> {
        if (json.isBlank() || json == "[]") return emptyList()

        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Route(
                id = obj.optString("id", ""),
                type = "featured",
                source = "cache",
                name = obj.optString("name", "Sin nombre"),
                description = "",
                coverImage = obj.optString("coverImage", null),
                images = emptyList(),
                distanceKm = obj.optDouble("distanceKm", 0.0),
                difficulty = obj.optString("difficulty", "MODERADA"),
                featured = true
            )
        }
    }

    // ==========================================
    // 🔧 HELPERS
    // ==========================================

    private fun toLite(route: Route) = RecentRouteLite(
        id = route.id,
        name = route.name,
        coverImage = route.coverImage,
        difficulty = route.getNormalizedDifficulty(),
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
                id = obj.optString("id", ""),
                name = obj.optString("name", "Sin nombre"),
                coverImage = obj.optString("coverImage", null),
                difficulty = obj.optString("difficulty", null),
                distanceKm = if (obj.isNull("distanceKm")) null else obj.optDouble("distanceKm", 0.0)
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