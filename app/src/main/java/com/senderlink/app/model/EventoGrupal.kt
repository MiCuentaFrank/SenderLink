package com.senderlink.app.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * 🎯 EventoGrupal - Modelo de Ruta Grupal
 *
 * routeId puede venir:
 *  - como String: "6946..."
 *  - como Object (populate): { _id, name, coverImage, ... }
 *
 * Por eso se guarda como JsonElement
 */
data class EventoGrupal(

    @SerializedName("_id")
    val id: String,

    // 🗺️ RUTA (string u objeto)
    @SerializedName("routeId")
    val routeIdRaw: JsonElement,

    // 👤 ORGANIZADOR
    @SerializedName("organizadorUid")
    val organizadorUid: String,

    @SerializedName("organizadorNombre")
    val organizadorNombre: String,

    @SerializedName("organizadorFoto")
    val organizadorFoto: String? = null,

    // 📅 FECHA Y HORA
    @SerializedName("fecha")
    val fecha: String, // ISO 8601

    @SerializedName("horaEncuentro")
    val horaEncuentro: String = "09:00",

    // 👥 PARTICIPANTES
    @SerializedName("participantes")
    val participantes: List<Participante> = emptyList(),

    // 🔢 LÍMITE
    @SerializedName("maxParticipantes")
    val maxParticipantes: Int = 10,

    // 📊 ESTADO
    @SerializedName("estado")
    val estado: String, // ABIERTO, COMPLETO, FINALIZADO, CANCELADO

    // 💬 CHAT
    @SerializedName("chatId")
    val chatId: String,

    // 📝 INFO
    @SerializedName("descripcion")
    val descripcion: String? = null,

    @SerializedName("nivelRecomendado")
    val nivelRecomendado: String? = null,

    @SerializedName("puntoEncuentro")
    val puntoEncuentro: PuntoEncuentro? = null,

    // 📊 VIRTUALS (backend)
    @SerializedName("numParticipantes")
    val numParticipantes: Int? = null,

    @SerializedName("plazasDisponibles")
    val plazasDisponibles: Int? = null,

    // ✅ FLAGS calculados en backend
    @SerializedName("isParticipant")
    val isParticipant: Boolean? = null,

    @SerializedName("isOrganizer")
    val isOrganizer: Boolean? = null,

    // 📅 METADATOS
    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: String? = null
) {

    // ==========================================
    // ✅ FUNCIONES PARA EXTRAER DATOS DE routeIdRaw
    // ==========================================

    /**
     * ✅ Devuelve SIEMPRE el ID real de la ruta
     * Puede ser string simple o dentro de un objeto populate
     */
    fun getRouteId(): String {
        return try {
            when {
                routeIdRaw.isJsonPrimitive -> routeIdRaw.asString
                routeIdRaw.isJsonObject -> routeIdRaw.asJsonObject.get("_id")?.asString ?: ""
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * ✅ Nombre de la ruta (si viene populated)
     * Usada por EventoAdapter
     */
    fun getNombreRuta(): String? {
        return try {
            if (routeIdRaw.isJsonObject) {
                routeIdRaw.asJsonObject.get("name")?.asString
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ✅ NUEVO: Imagen de portada de la ruta (si viene populated)
     * Usada por EventoAdapter
     */
    fun getCoverImage(): String? {
        return try {
            if (routeIdRaw.isJsonObject) {
                routeIdRaw.asJsonObject.get("coverImage")?.asString
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * @Deprecated Use getNombreRuta() instead
     */
    @Deprecated("Use getNombreRuta()", ReplaceWith("getNombreRuta()"))
    fun getRouteNameOrNull(): String? = getNombreRuta()

    // ==========================================
    // FUNCIONES DE VALIDACIÓN
    // ==========================================

    fun isOrganizador(uid: String): Boolean = organizadorUid == uid

    fun isParticipante(uid: String): Boolean = participantes.any { it.uid == uid }

    fun getNumParticipantes(): Int = numParticipantes ?: participantes.size

    fun getPlazasDisponibles(): Int =
        plazasDisponibles ?: (maxParticipantes - participantes.size)

    fun hasPlazasDisponibles(): Boolean = getPlazasDisponibles() > 0

    // ==========================================
    // FUNCIONES DE ESTADO
    // ==========================================

    fun isAbierto(): Boolean = estado == Estado.ABIERTO
    fun isCompleto(): Boolean = estado == Estado.COMPLETO
    fun isFinalizado(): Boolean = estado == Estado.FINALIZADO
    fun isCancelado(): Boolean = estado == Estado.CANCELADO

    // ==========================================
    // FUNCIONES DE PERMISOS
    // ==========================================

    fun canJoin(uid: String): Boolean {
        return !isParticipante(uid) &&
                isAbierto() &&
                hasPlazasDisponibles() &&
                !isFinalizado() &&
                !isCancelado()
    }

    fun canLeave(uid: String): Boolean {
        return isParticipante(uid) && !isOrganizador(uid)
    }

    // ==========================================
    // CONSTANTES
    // ==========================================

    object Estado {
        const val ABIERTO = "ABIERTO"
        const val COMPLETO = "COMPLETO"
        const val FINALIZADO = "FINALIZADO"
        const val CANCELADO = "CANCELADO"
    }
}

// ==========================================
// MODELOS AUXILIARES
// ==========================================

data class PuntoEncuentro(
    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("lat")
    val lat: Double? = null,

    @SerializedName("lng")
    val lng: Double? = null
) {
    fun isValid(): Boolean = lat != null && lng != null && lat != 0.0 && lng != 0.0
}