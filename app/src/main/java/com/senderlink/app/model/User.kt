package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Usuario
 *
 * COINCIDE CON: Backend Node.js (userController.js)
 *
 * Este modelo representa la estructura de un usuario en la base de datos
 * y cómo se envía/recibe en las peticiones HTTP.
 */
data class User(
    // ID de MongoDB (generado automáticamente)
    @SerializedName("_id")
    val id: String = "",

    // UID de Firebase Authentication (único)
    @SerializedName("uid")
    val uid: String = "",

    // Email del usuario
    @SerializedName("email")
    val email: String = "",

    // ========== DATOS DEL PERFIL ==========

    // Nombre o nick del usuario
    @SerializedName("nombre")
    val nombre: String = "",

    // URL de la foto de perfil
    @SerializedName("foto")
    val foto: String = "",

    // Biografía del usuario
    @SerializedName("bio")
    val bio: String = "",

    // Comunidad autónoma
    @SerializedName("comunidad")
    val comunidad: String = "",

    // Provincia
    @SerializedName("provincia")
    val provincia: String = "",

    // Porcentaje de completado del perfil (0-100)
    @SerializedName("profileCompletion")
    val profileCompletion: Int = 0,

    // ========== PREFERENCIAS ==========

    // Preferencias de rutas del usuario
    @SerializedName("preferencias")
    val preferencias: Preferencias? = null,

    // ========== METADATOS ==========

    // Fecha de creación (ISO 8601)
    @SerializedName("createdAt")
    val createdAt: String? = null,

    // Fecha de última actualización (ISO 8601)
    @SerializedName("updatedAt")
    val updatedAt: String? = null
)

/**
 * Preferencias de rutas del usuario
 *
 * Estos campos indican qué tipo de rutas prefiere el usuario
 */
data class Preferencias(
    // Nivel preferido: BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    @SerializedName("nivel")
    val nivel: String = "",

    // Tipos de rutas preferidas: [MONTAÑA, BOSQUE, COSTA, etc]
    @SerializedName("tipos")
    val tipos: List<String> = emptyList(),

    // Distancia típica que recorre (en kilómetros)
    @SerializedName("distanciaKm")
    val distanciaKm: Double = 0.0
)