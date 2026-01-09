package com.senderlink.app.utils

import kotlin.math.roundToInt

object DistanceFormatter {

    /**
     * Formatea la distancia de forma inteligente:
     * - Si < 1 km → muestra en metros (ej: "850 m")
     * - Si 1-10 km → muestra con 1 decimal (ej: "3.2 km")
     * - Si > 10 km → muestra sin decimales (ej: "15 km")
     */
    fun format(distanceKm: Double): String {
        return when {
            distanceKm < 0.1 -> {
                // Muy cerca: mostrar en metros sin decimales
                val meters = (distanceKm * 1000).roundToInt()
                "$meters m"
            }
            distanceKm < 1.0 -> {
                // Menos de 1 km: mostrar en metros redondeados a decenas
                val meters = ((distanceKm * 1000) / 10).roundToInt() * 10
                "$meters m"
            }
            distanceKm < 10.0 -> {
                // Entre 1 y 10 km: mostrar con 1 decimal
                "%.1f km".format(distanceKm)
            }
            else -> {
                // Más de 10 km: mostrar sin decimales
                "${distanceKm.roundToInt()} km"
            }
        }
    }

    /**
     * Formatea la duración en formato legible
     * Ejemplo: 125 minutos → "2h 5min"
     */
    fun formatDuration(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}min"
        }
    }

    /**
     * Formatea el desnivel
     * Ejemplo: 450 → "450 m"
     */
    fun formatElevation(meters: Int): String {
        return "$meters m"
    }
}