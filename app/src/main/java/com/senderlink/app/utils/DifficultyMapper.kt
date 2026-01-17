package com.senderlink.app.utils

object DifficultyMapper {

    // Estándar único para toda la app
    const val FACIL = "FACIL"
    const val MODERADA = "MODERADA"
    const val DIFICIL = "DIFICIL"

    fun normalize(raw: String?): String {
        val s = raw
            ?.trim()
            ?.uppercase()
            ?.replace("Á", "A")
            ?.replace("É", "E")
            ?.replace("Í", "I")
            ?.replace("Ó", "O")
            ?.replace("Ú", "U")
            ?: return MODERADA

        return when {
            s.contains("FACIL") || s.contains("EASY") || s == "1" -> FACIL
            s.contains("DIFICIL") || s.contains("HARD") || s == "3" -> DIFICIL
            s.contains("MODERAD") || s.contains("MEDIA") || s.contains("NORMAL") || s.contains("INTERMED") || s == "2" -> MODERADA
            else -> MODERADA
        }
    }
}
