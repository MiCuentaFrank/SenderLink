package com.senderlink.app.network

import com.senderlink.app.model.Route
import retrofit2.Call
import retrofit2.http.GET

// La respuesta que devuelve tu backend en GET /api/routes
data class RouteResponse(
    val ok: Boolean,
    val count: Int,
    val routes: List<Route>
)

interface RouteService {

    @GET("routes")
    fun getRoutes(): Call<RouteResponse>
}
