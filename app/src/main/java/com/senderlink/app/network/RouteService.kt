package com.senderlink.app.network

import com.senderlink.app.viewmodel.RouteDetailResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RouteService {

    // 🔹 Todas las rutas
    @GET("routes")
    fun getAllRoutes(
        @Query("limit") limit: Int
    ): Call<RouteResponse>

    // 🔹 Rutas destacadas
    @GET("routes/featured")
    fun getFeaturedRoutes(
        @Query("limit") limit: Int
    ): Call<RouteResponse>

    // 🔹 Rutas por parque nacional
    @GET("routes/parques")
    fun getRoutesByPark(
        @Query("name") parque: String,
        @Query("limit") limit: Int
    ): Call<RouteResponse>

    // 🔹 Obtener ruta por ID
    @GET("routes/{id}")
    fun getRouteById(
        @Path("id") routeId: String
    ): Call<RouteDetailResponse>
}