package com.senderlink.app.repository

import com.senderlink.app.network.*

class RouteRepository(
    private val service: RouteService =
        RetrofitClient.instance.create(RouteService::class.java)
) {

    suspend fun getAllRoutes(page: Int = 1, limit: Int = 20): RouteResponse {
        return service.getAllRoutes(page, limit).await()
    }

    /**
     * Obtener TODAS las rutas para el mapa (sin filtro featured)
     */
    suspend fun getAllRoutesForMap(
        page: Int = 1,
        limit: Int = 100,
        difficulty: String? = null
    ): RouteResponse {
        return service.getAllRoutesForMap(page, limit, difficulty).await()
    }

    /**
     * ⭐ Obtener rutas destacadas CON PAGINACIÓN
     */

    suspend fun getFeaturedRoutes(
        page: Int = 1,        // ✅ NUEVO
        limit: Int = 20
    ): FeaturedResponse {
        return service.getFeaturedRoutes(page, limit).await()
    }


    suspend fun getRoutesByPark(park: String, limit: Int = 20): RouteResponse {
        return service.getRoutesByPark(park, limit).await()
    }

    suspend fun getRouteById(routeId: String): RouteDetailResponse {
        return service.getRouteById(routeId).await()
    }

    suspend fun getRoutesNearMe(
        lat: Double,
        lng: Double,
        radio: Int = 50000,
        limit: Int = 100
    ): RoutesNearResponse {
        return service.getRoutesNearMe(lat, lng, radio, limit).await()
    }

    suspend fun getRoutesByUser(uid: String): UserRoutesResponse {
        return service.getRoutesByUser(uid).await()
    }
}