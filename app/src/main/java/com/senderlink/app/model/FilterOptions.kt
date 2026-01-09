package com.senderlink.app.model

data class FilterOptions(
    val distanciaMaxima: Int = 5000, // en metros
    val dificultades: List<String> = listOf("Fácil", "Media", "Difícil"),
    val duracionMaxima: Int? = null, // en minutos
    val desnivelMaximo: Int? = null, // en metros
    val ordenarPor: OrdenarPor = OrdenarPor.DISTANCIA
)

enum class OrdenarPor {
    DISTANCIA,
    VALORACION,
    DIFICULTAD,
    DURACION
}