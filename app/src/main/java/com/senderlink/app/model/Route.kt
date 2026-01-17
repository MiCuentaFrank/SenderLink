package com.senderlink.app.model

import com.google.gson.annotations.SerializedName
import com.senderlink.app.utils.DifficultyMapper

/**
 * 🗺️ Route - Modelo principal
 *
 * Soporta el backend de SenderLink que puede incluir:
 * - Coordenadas legacy (lat / lng)
 * - Coordenadas GeoJSON (coordinates)
 */
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

    @SerializedName("durationMin")
    val durationMin: Int? = null,

    // ⚠️ Valor crudo del backend (se normaliza en Android)
    val difficulty: String,

    // Ubicación textual
    val startLocality: String? = null,
    val provincia: String? = null,
    val comunidad: String? = null,
    val parqueNacional: String? = null,

    // Geometría
    val startPoint: GeoPoint? = null,
    val endPoint: GeoPoint? = null,
    val geometry: GeoLineString? = null,

    // Flags
    val featured: Boolean = false,

    // Identificadores
    val code: String? = null,
    val externalId: String? = null,
    val uid: String? = null,

    // Fechas
    val fechaEdicion: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,

    // Información extra variable (evita errores de deserialización)
    val extraInfo: Any? = null,

    @SerializedName("__v")
    val version: Int? = null,

    // Distancia en metros desde la ubicación del usuario (endpoint /cerca)
    val distance: Double? = null
) {

    /* =======================
     * Helpers de coordenadas
     * ======================= */

    fun getStartLat(): Double = startPoint?.getLat() ?: 0.0
    fun getStartLng(): Double = startPoint?.getLng() ?: 0.0
    fun getEndLat(): Double = endPoint?.getLat() ?: 0.0
    fun getEndLng(): Double = endPoint?.getLng() ?: 0.0

    fun hasValidGPS(): Boolean = startPoint?.isValid() == true

    /* =======================
     * Helpers de negocio
     * ======================= */

    // Dificultad normalizada para toda la app (FACIL / MODERADA / DIFICIL)
    fun getNormalizedDifficulty(): String =
        DifficultyMapper.normalize(difficulty)

    // Distancia en km desde el usuario
    fun getDistanceKmFromUser(): Double? =
        distance?.let { it / 1000.0 }

    // Distancia formateada para UI
    fun getFormattedDistance(): String {
        return distance?.let {
            when {
                it < 1000 -> "${it.toInt()} m"
                else -> String.format("%.1f km", it / 1000.0)
            }
        } ?: ""
    }
}

/**
 * 📍 GeoPoint
 *
 * Soporta:
 * - GeoJSON: coordinates [lng, lat]
 * - Legacy: lat / lng
 */
data class GeoPoint(

    // GeoJSON
    val type: String? = null,
    val coordinates: List<Double>? = null,

    // Legacy
    val lat: Double? = null,
    val lng: Double? = null
) {

    fun getLat(): Double {
        // GeoJSON → coordinates[1]
        if (coordinates != null && coordinates.size >= 2) {
            return coordinates[1]
        }
        return lat ?: 0.0
    }

    fun getLng(): Double {
        // GeoJSON → coordinates[0]
        if (coordinates != null && coordinates.size >= 2) {
            return coordinates[0]
        }
        return lng ?: 0.0
    }

    fun isValid(): Boolean {
        if (coordinates != null && coordinates.size >= 2) {
            return coordinates[0] != 0.0 || coordinates[1] != 0.0
        }
        return (lat != null && lng != null) && (lat != 0.0 || lng != 0.0)
    }
}

/**
 * 📍 GeoLineString - Línea de la ruta (GeoJSON)
 */
data class GeoLineString(
    val type: String? = null,
    val coordinates: List<List<Double>>? = null
) {
    fun pointCount(): Int = coordinates?.size ?: 0
    fun isValid(): Boolean = coordinates != null && coordinates.size >= 2
}
