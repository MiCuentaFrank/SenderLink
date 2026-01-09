package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

data class Route(
    @SerializedName("_id")
    val id: String,

    val type: String,
    val source: String,
    val name: String,
    val description: String,

    @SerializedName("coverImage")
    val coverImage: String,

    val images: List<String>,

    @SerializedName("distanceKm")
    val distanceKm: Double,

    val difficulty: String,

    // Ubicación
    val startLocality: String? = null,
    val provincia: String? = null,
    val comunidad: String? = null,

    // Flags
    val featured: Boolean = false,

    // Identificadores
    val code: String? = null,
    val externalId: String? = null,

    // Tiempo
    val durationMin: Int? = null,

    // Coordenadas - CORRECTAMENTE PARSEADAS
    val startPoint: Point? = null,
    val endPoint: Point? = null,
    val geometry: Geometry? = null,

    // Fechas
    val fechaEdicion: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,

    // Extra
    val extraInfo: Map<String, Any>? = null,

    @SerializedName("__v")
    val version: Int? = null
)

data class Point(
    val lat: Double,
    val lng: Double
)

data class Geometry(
    val type: String? = null,
    val coordinates: List<List<Double>>? = null
)