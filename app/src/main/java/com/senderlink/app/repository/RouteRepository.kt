package com.senderlink.app.repository

import com.google.gson.Gson
import com.senderlink.app.SenderLinkApp
import com.senderlink.app.db.AppDatabase
import com.senderlink.app.db.RouteEntity
import com.senderlink.app.model.Route
import com.senderlink.app.network.*

class RouteRepository(
    private val service: RouteService =
        RetrofitClient.instance.create(RouteService::class.java)
) {
    private val gson = Gson()
    private val db by lazy { AppDatabase.getInstance(SenderLinkApp.appContext) }
    private val routeDao by lazy { db.routeDao() }

    // Caché válida 24 horas
    private val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    // ── Conversión Route ↔ RouteEntity ──────────────────────────────────────

    private fun Route.toEntity() = RouteEntity(
        id = this.id,
        json = gson.toJson(this),
        featured = if (this.featured) 1 else 0
    )

    private fun RouteEntity.toRoute(): Route? = try {
        gson.fromJson(this.json, Route::class.java)
    } catch (_: Exception) { null }

    private fun List<RouteEntity>.toRoutes(): List<Route> = mapNotNull { it.toRoute() }

    // ── Métodos con cache-first ──────────────────────────────────────────────

    suspend fun getAllRoutes(page: Int = 1, limit: Int = 20): RouteResponse {
        return try {
            val response = service.getAllRoutes(page, limit).await()
            if (response.ok) {
                routeDao.insertAll(response.routes.map { it.toEntity() })
                routeDao.deleteOlderThan(System.currentTimeMillis() - CACHE_TTL_MS)
            }
            response
        } catch (_: Exception) {
            val cached = routeDao.getAll(limit).toRoutes()
            RouteResponse(ok = true, routes = cached, total = cached.size, page = 1, limit = limit, pages = 1)
        }
    }

    suspend fun getAllRoutesForMap(
        page: Int = 1,
        limit: Int = 100,
        difficulty: String? = null
    ): RouteResponse {
        return try {
            val response = service.getAllRoutesForMap(page, limit, difficulty).await()
            if (response.ok) {
                routeDao.insertAll(response.routes.map { it.toEntity() })
            }
            response
        } catch (_: Exception) {
            val cached = routeDao.getAll(limit).toRoutes()
            RouteResponse(ok = true, routes = cached, total = cached.size, page = 1, limit = limit, pages = 1)
        }
    }

    suspend fun getFeaturedRoutes(page: Int = 1, limit: Int = 20): FeaturedResponse {
        return try {
            val response = service.getFeaturedRoutes(page, limit).await()
            if (response.ok) {
                routeDao.insertAll(response.routes.map { it.toEntity() })
            }
            response
        } catch (_: Exception) {
            val cached = routeDao.getFeatured(limit).toRoutes()
            FeaturedResponse(ok = true, routes = cached, total = cached.size, page = 1, limit = limit, pages = 1, count = cached.size)
        }
    }

    suspend fun getRouteById(routeId: String): RouteDetailResponse {
        return try {
            val response = service.getRouteById(routeId).await()
            if (response.ok) {
                routeDao.insert(response.route.toEntity())
            }
            response
        } catch (_: Exception) {
            val cached = routeDao.getById(routeId)?.toRoute()
            if (cached != null) {
                RouteDetailResponse(ok = true, route = cached)
            } else {
                throw Exception("Sin conexión y sin caché para esta ruta")
            }
        }
    }

    // ── Métodos sin caché (requieren red) ────────────────────────────────────

    suspend fun getRoutesByPark(park: String, limit: Int = 20): RouteResponse {
        return service.getRoutesByPark(park, limit).await()
    }

    suspend fun getRoutesNearMe(
        lat: Double,
        lng: Double,
        radio: Int = 50000,
        limit: Int = 100
    ): RoutesNearResponse {
        return try {
            val response = service.getRoutesNearMe(lat, lng, radio, limit).await()
            if (response.ok) {
                routeDao.insertAll(response.routes.map { it.toEntity() })
            }
            response
        } catch (_: Exception) {
            val cached = routeDao.getAll(limit).toRoutes()
            RoutesNearResponse(ok = true, count = cached.size, routes = cached)
        }
    }

    suspend fun getRoutesByUser(uid: String): UserRoutesResponse {
        return service.getRoutesByUser(uid).await()
    }

    suspend fun createRoute(request: CreateRouteRequest): CreateRouteApiResponse {
        return service.createRoute(request).await()
    }

    suspend fun completeRoute(routeId: String, request: CompleteRouteRequest): CompleteRouteResponse {
        return service.completeRoute(routeId, request).await()
    }
}
