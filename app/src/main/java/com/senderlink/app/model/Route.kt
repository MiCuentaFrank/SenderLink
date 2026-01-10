package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

/**
 * 🗺️ Route - Modelo principal
 *
 * Soporta el formato de tu backend que incluye AMBOS:
 * - lat/lng (campos legacy)
 * - coordinates (formato GeoJSON)
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

    val extraInfo: Map<String, Any>? = null,

    @SerializedName("__v")
    val version: Int? = null
) {
    // Helpers para acceder a coordenadas fácilmente
    fun getStartLat(): Double = startPoint?.getLat() ?: 0.0
    fun getStartLng(): Double = startPoint?.getLng() ?: 0.0
    fun getEndLat(): Double = endPoint?.getLat() ?: 0.0
    fun getEndLng(): Double = endPoint?.getLng() ?: 0.0

    fun hasValidGPS(): Boolean = startPoint?.isValid() == true
}

/**
 * 📍 GeoPoint - Punto geográfico
 *
 * Tu backend envía AMBOS formatos:
 * {
 *   "lat": 43.38,
 *   "lng": -2.98,
 *   "coordinates": [-2.98, 43.38],
 *   "type": "Point"
 * }
 */
data class GeoPoint(
    // Formato GeoJSON
    val type: String? = null,
    val coordinates: List<Double>? = null,

    // Formato legacy
    val lat: Double? = null,
    val lng: Double? = null
) {
    /**
     * Obtiene latitud (prioriza coordinates, fallback a lat)
     */
    fun getLat(): Double {
        // GeoJSON: coordinates[1] es lat
        if (coordinates != null && coordinates.size >= 2) {
            return coordinates[1]
        }
        return lat ?: 0.0
    }

    /**
     * Obtiene longitud (prioriza coordinates, fallback a lng)
     */
    fun getLng(): Double {
        // GeoJSON: coordinates[0] es lng
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
 * 📏 GeoLineString - Línea de la ruta
 */
data class GeoLineString(
    val type: String? = null,
    val coordinates: List<List<Double>>? = null
) {
    fun pointCount(): Int = coordinates?.size ?: 0
    fun isValid(): Boolean = coordinates != null && coordinates.size >= 2
}