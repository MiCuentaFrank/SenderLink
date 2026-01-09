package com.senderlink.app.network

import com.senderlink.app.model.Route


data class RouteResponse(
    val ok: Boolean,
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
    val routes: List<Route>
)

