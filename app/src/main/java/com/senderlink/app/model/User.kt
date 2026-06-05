package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Usuario
 *
 * COINCIDE CON: Backend Node.js (userController.js)
 */
data class User(
    @SerializedName("_id")
    val id: String = "",

    @SerializedName("uid")
    val uid: String = "",

    @SerializedName("email")
    val email: String = "",

    // ========== DATOS DEL PERFIL ==========

    @SerializedName("nombre")
    val nombre: String = "",

    @SerializedName("foto")
    val foto: String = "",

    @SerializedName("bio")
    val bio: String = "",

    @SerializedName("comunidad")
    val comunidad: String = "",

    @SerializedName("provincia")
    val provincia: String = "",

    @SerializedName("profileCompletion")
    val profileCompletion: Int = 0,

    // ========== PREFERENCIAS ==========

    @SerializedName("preferencias")
    val preferencias: Preferencias? = null,

    // ========== GAMIFICACIÓN ==========

    @SerializedName("progreso")
    val progreso: Progreso? = null,

    @SerializedName("badges")
    val badges: List<String> = emptyList(),

    // ========== STATS ==========

    @SerializedName("stats")
    val stats: Stats? = null,

    // ========== METADATOS ==========

    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: String? = null
)

data class Preferencias(
    @SerializedName("nivel")
    val nivel: String = "",

    @SerializedName("tipos")
    val tipos: List<String> = emptyList(),

    @SerializedName("distanciaKm")
    val distanciaKm: Double = 0.0
)

data class Progreso(
    @SerializedName("level")
    val level: Int = 1,

    @SerializedName("xp")
    val xp: Int = 0,

    @SerializedName("rankTitle")
    val rankTitle: String = "Explorer"
)

data class Stats(
    @SerializedName("routesPublished")
    val routesPublished: Int = 0,

    @SerializedName("routesCompleted")
    val routesCompleted: Int = 0,

    @SerializedName("totalKm")
    val totalKm: Double = 0.0,

    @SerializedName("streakDays")
    val streakDays: Int = 0
)
