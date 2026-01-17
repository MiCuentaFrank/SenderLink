package com.senderlink.app.model

import com.senderlink.app.utils.DifficultyMapper

data class FilterOptions(
    val distanciaMaxima: Int = 5000, // en metros
    val dificultades: List<String> = listOf(
        DifficultyMapper.FACIL,
        DifficultyMapper.MODERADA,
        DifficultyMapper.DIFICIL
    ),// lista de dificultades a filtrar

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