package com.senderlink.app.repository

import com.senderlink.app.network.*

/**
 * 📦 RouteRepository - Capa de acceso a datos
 */
class RouteRepository(
    private val service: RouteService =
        RetrofitClient.instance.create(RouteService::class.java)
) {

    suspend fun getAllRoutes(page: Int = 1, limit: Int = 20): RouteResponse {
        return service.getAllRoutes(page, limit).await()
    }

    suspend fun getFeaturedRoutes(limit: Int = 10): FeaturedResponse {
        return service.getFeaturedRoutes(limit).await()
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
        limit: Int = 20
    ): RoutesNearResponse {
        return service.getRoutesNearMe(lat, lng, radio, limit).await()
    }

    suspend fun getRoutesByUser(uid: String): UserRoutesResponse {
        return service.getRoutesByUser(uid).await()
    }
}