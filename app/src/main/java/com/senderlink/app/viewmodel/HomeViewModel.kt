package com.senderlink.app.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senderlink.app.model.Route
import com.senderlink.app.network.RetrofitClient
import com.senderlink.app.network.RouteResponse
import com.senderlink.app.network.RouteService
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import org.json.JSONArray

class HomeViewModel : ViewModel() {

    private val routeService = RetrofitClient.instance
        .create(RouteService::class.java)

    private val _routes = MutableLiveData<List<Route>>(emptyList())
    val routes: LiveData<List<Route>> = _routes

    private val _featuredRoutes = MutableLiveData<List<Route>>(emptyList())
    val featuredRoutes: LiveData<List<Route>> = _featuredRoutes

    // ========== DESTACADAS ==========
    private var currentFeaturedPage = 0
    private val pageSize = 20
    private var isLoadingFeatured = false
    private var hasMoreFeatured = true
    private val allFeaturedRoutes = mutableListOf<Route>()
    private var shuffledFeaturedRoutes: List<Route>? = null

    fun loadFeaturedRoutes(reset: Boolean = false) {
        if (isLoadingFeatured) return
        if (!hasMoreFeatured && !reset) return

        if (reset) {
            currentFeaturedPage = 0
            allFeaturedRoutes.clear()
            hasMoreFeatured = true
            shuffledFeaturedRoutes = null
            Log.d("HOME_VM", "Reset completo: limpiando rutas destacadas")
        }

        isLoadingFeatured = true
        val limit = (currentFeaturedPage + 1) * pageSize + 20

        routeService.getAllRoutes(limit)
            .enqueue(object : Callback<RouteResponse> {

                override fun onResponse(
                    call: Call<RouteResponse>,
                    response: Response<RouteResponse>
                ) {
                    isLoadingFeatured = false

                    if (response.isSuccessful) {
                        val allRoutes = response.body()?.routes ?: emptyList()
                        val featuredOnly = allRoutes.filter { it.featured }

                        if (shuffledFeaturedRoutes == null) {
                            shuffledFeaturedRoutes = featuredOnly.shuffled()
                            Log.d("HOME_VM", "Rutas destacadas aleatorizadas: ${shuffledFeaturedRoutes?.size}")
                        }

                        val featuredToShow = shuffledFeaturedRoutes ?: featuredOnly
                        val startIndex = currentFeaturedPage * pageSize
                        val endIndex = minOf(startIndex + pageSize, featuredToShow.size)

                        if (startIndex < featuredToShow.size) {
                            val newRoutes = featuredToShow.subList(startIndex, endIndex)
                            allFeaturedRoutes.addAll(newRoutes)
                            _featuredRoutes.value = allFeaturedRoutes.toList()

                            currentFeaturedPage++
                            hasMoreFeatured = endIndex < featuredToShow.size
                        } else {
                            hasMoreFeatured = false
                        }
                    } else {
                        Log.e("HOME_VM", "Respuesta no exitosa")
                    }
                }

                override fun onFailure(call: Call<RouteResponse>, t: Throwable) {
                    isLoadingFeatured = false
                    Log.e("HOME_VM", "Error: ${t.message}")
                }
            })
    }

    // ========== RECIENTES (persistentes) ==========

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

        // Persistir (guardar “lite”)
        val liteList = current.map { toLite(it) }

        viewModelScope.launch {
            val json = liteListToJson(liteList)
            Log.d("RECENTS", "Guardando JSON: $json")
            com.senderlink.app.utils.HomeDataStore.saveRecentsJson(context, json)
        }
    }

    fun loadRecentsFromStorage(context: Context) {
        viewModelScope.launch {
            val json = com.senderlink.app.utils.HomeDataStore.loadRecentsJson(context)
            Log.d("RECENTS", "JSON cargado: $json")

            val liteList = jsonToLiteList(json)
            Log.d("RECENTS", "Recientes cargadas: ${liteList.size}")

            _routes.value = liteList.map { liteToRoute(it) }
        }
    }

    private fun toLite(route: Route): RecentRouteLite {
        return RecentRouteLite(
            id = route.id,
            name = route.name,
            coverImage = route.coverImage,
            difficulty = route.difficulty,
            distanceKm = route.distanceKm
        )
    }

    private fun liteListToJson(list: List<RecentRouteLite>): String {
        val arr = org.json.JSONArray()
        list.forEach { r ->
            val obj = org.json.JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("coverImage", r.coverImage)
            obj.put("difficulty", r.difficulty)
            obj.put("distanceKm", r.distanceKm)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun jsonToLiteList(json: String): List<RecentRouteLite> {
        val arr = JSONArray(json)
        val out = mutableListOf<RecentRouteLite>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(
                RecentRouteLite(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    coverImage = obj.optString("coverImage", null),
                    difficulty = obj.optString("difficulty", null),
                    distanceKm = if (obj.isNull("distanceKm")) null else obj.getDouble("distanceKm")
                )
            )
        }
        return out
    }

    /**
     * 🔧 Creamos un Route “mínimo válido” con defaults,
     * porque tu Route real tiene muchos campos obligatorios.
     */
    private fun liteToRoute(lite: RecentRouteLite): Route {
        return Route(
            id = lite.id,

            // obligatorios:
            type = "recent",                 // valor por defecto
            source = "local",                // valor por defecto
            name = lite.name,
            description = "",                // no lo tenemos guardado
            coverImage = lite.coverImage ?: "",

            images = emptyList(),            // no lo tenemos guardado
            distanceKm = lite.distanceKm ?: 0.0,
            difficulty = lite.difficulty ?: "",

            // opcionales / con default:
            featured = false
        )
    }
}
