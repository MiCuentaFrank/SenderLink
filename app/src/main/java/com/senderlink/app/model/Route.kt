package com.senderlink.app.model

data class Punto(
    val latitud: Double,
    val longitud: Double,
    val orden: Int
)

data class Route(
    val _id: String,
    val nombre: String,
    val descripcion: String? = null,
    val dificultad: String? = null,
    val distancia: Double? = null,
    val duracion: Int? = null,
    val provincia: String? = null,
    val localidad: String? = null,
    val imagenPortada: String? = null,
    val imagenes: List<String>? = null,
    val puntos: List<Punto> = emptyList()
)
