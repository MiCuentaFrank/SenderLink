package com.senderlink.app.network

import com.senderlink.app.model.Route

/**
 * 📦 Modelos de respuesta de la API
 *
 * Cada endpoint devuelve un JSON diferente.
 * Estas clases representan esas estructuras.
 */

// GET /api/routes (paginado)
data class RouteResponse(
    val ok: Boolean,
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
    val routes: List<Route>
)

// GET /api/routes/{id}
data class RouteDetailResponse(
    val ok: Boolean,
    val route: Route
)

// GET /api/routes/featured
data class FeaturedResponse(
    val ok: Boolean,
    val count: Int,
    val routes: List<Route>
)

// GET /api/routes/cerca
data class RoutesNearResponse(
    val ok: Boolean,
    val count: Int,
    val routes: List<Route>
)

// GET /api/routes/user/{uid}
data class UserRoutesResponse(
    val ok: Boolean,
    val count: Int,
    val routes: List<Route>
)