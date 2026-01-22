package com.senderlink.app.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RouteService {

    /**
     * 📋 Obtener todas las rutas (paginado)
     * GET /api/routes?page=1&limit=20
     */
    @GET("api/routes")
    fun getAllRoutes(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Call<RouteResponse>

    /**
     * 🗺️ Obtener TODAS las rutas para el mapa (sin filtro featured)
     * GET /api/routes/map?page=1&limit=100&difficulty=FACIL
     */
    @GET("api/routes/map")
    fun getAllRoutesForMap(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("difficulty") difficulty: String? = null
    ): Call<RouteResponse>

    // ==========================================
// ACTUALIZAR en RouteService.kt
// ==========================================

    /**
     * ⭐ Obtener rutas destacadas CON PAGINACIÓN
     * GET /api/routes/featured?page=1&limit=20
     */
    @GET("api/routes/featured")
    fun getFeaturedRoutes(
        @Query("page") page: Int = 1,      // ✅ NUEVO
        @Query("limit") limit: Int = 20
    ): Call<FeaturedResponse>

    /**
     * 🌲 Obtener rutas por parque nacional
     * GET /api/routes?parqueNacional=Sierra%20Nevada&limit=20
     */
    @GET("api/routes")
    fun getRoutesByPark(
        @Query("parqueNacional") park: String,
        @Query("limit") limit: Int = 20
    ): Call<RouteResponse>

    /**
     * 🔍 Obtener ruta por ID
     * GET /api/routes/{id}
     */
    @GET("api/routes/{id}")
    fun getRouteById(
        @Path("id") routeId: String
    ): Call<RouteDetailResponse>

    /**
     * 📍 Obtener rutas cercanas
     * GET /api/routes/cerca?lat=40.41&lng=-3.70&radio=50000&limit=100
     */
    @GET("api/routes/cerca")
    fun getRoutesNearMe(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radio") radio: Int = 50000,  // 50km por defecto
        @Query("limit") limit: Int = 100
    ): Call<RoutesNearResponse>

    /**
     * 👤 Obtener rutas de un usuario
     * GET /api/routes/user/{uid}
     */
    @GET("api/routes/user/{uid}")
    fun getRoutesByUser(
        @Path("uid") uid: String
    ): Call<UserRoutesResponse>
}