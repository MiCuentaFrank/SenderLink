package com.senderlink.app.repository

import android.util.Log
import com.senderlink.app.model.CreateEventoBody
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.model.JoinLeaveEventoBody
import com.senderlink.app.model.SimpleUidBody
import com.senderlink.app.network.*

/**
 * 🎯 EventRepository - Repository de Rutas Grupales
 */
class EventRepository {

    private val eventService: EventService =
        RetrofitClient.instance.create(EventService::class.java)

    private val TAG = "EventRepository"

    // ========================================
    // CORRECCIÓN DE FLAGS
    // ========================================

    /**
     * ✅ Función auxiliar para corregir flags isParticipant / isOrganizer
     * Calcula los flags basándose en los datos reales del evento
     */
    private fun corregirFlags(evento: EventoGrupal, uid: String?): EventoGrupal {
        if (uid.isNullOrBlank()) return evento

        val esOrganizador = evento.organizadorUid == uid
        val esParticipante = evento.participantes.any { it.uid == uid }

        return evento.copy(
            isOrganizer = esOrganizador,
            isParticipant = esParticipante
        )
    }

    // ========================================
    // LECTURAS (suspend)
    // ========================================

    /**
     * 📋 Obtener lista de eventos (con filtros)
     * ✅ Corrige flags localmente como safety net
     */
    suspend fun getEventos(
        estado: String? = null,
        routeId: String? = null,
        uid: String? = null,
        limit: Int = 20,
        skip: Int = 0
    ): EventosResponse {
        val response = eventService.getEventos(estado, routeId, uid, limit, skip).await()

        if (response.ok && response.data != null && !uid.isNullOrBlank()) {
            val eventosCorregidos = response.data.eventos.map { evento ->
                corregirFlags(evento, uid)
            }
            Log.d(TAG, "✅ getEventos: ${eventosCorregidos.size} eventos con flags corregidos")
            val dataCorregida = response.data.copy(eventos = eventosCorregidos)
            return response.copy(data = dataCorregida)
        }

        return response
    }

    /**
     * 🆔 Obtener evento por ID
     * ✅ Pasa uid para que el backend calcule flags; corrige localmente como safety net
     */
    suspend fun getEventoById(eventoId: String, uid: String? = null): EventoDetailResponse {
        val response = eventService.getEventoById(eventoId, uid).await()

        if (response.ok && response.data != null && !uid.isNullOrBlank()) {
            val eventoCorregido = corregirFlags(response.data, uid)
            Log.d(TAG, "✅ getEventoById: evento con flags corregidos")
            return response.copy(data = eventoCorregido)
        }

        return response
    }

    /**
     * 👤 Mis eventos (organizados por mí)
     */
    suspend fun getEventosByUser(uid: String): UserEventosResponse {
        val response = eventService.getEventosByUser(uid).await()

        if (response.ok && response.data != null) {
            val eventosCorregidos = response.data.map { evento ->
                evento.copy(isOrganizer = true, isParticipant = false)
            }
            Log.d(TAG, "✅ getEventosByUser: ${eventosCorregidos.size} eventos")
            return response.copy(data = eventosCorregidos)
        }

        return response
    }

    /**
     * 👥 Eventos donde participo
     */
    suspend fun getEventosParticipando(uid: String): UserEventosResponse {
        val response = eventService.getEventosParticipando(uid).await()

        if (response.ok && response.data != null) {
            val eventosCorregidos = response.data.map { evento ->
                evento.copy(isParticipant = true, isOrganizer = false)
            }
            Log.d(TAG, "✅ getEventosParticipando: ${eventosCorregidos.size} eventos")
            return response.copy(data = eventosCorregidos)
        }

        return response
    }

    // ========================================
    // ACCIONES (suspend) — sin LiveData ni polling
    // ========================================

    suspend fun createEvento(
        routeId: String,
        organizadorUid: String,
        organizadorNombre: String,
        organizadorFoto: String?,
        fecha: String,
        maxParticipantes: Int = 10,
        descripcion: String? = null,
        nivelRecomendado: String? = null,
        horaEncuentro: String = "09:00"
    ): CreateEventoResponse {
        val body = CreateEventoBody(
            routeId = routeId,
            organizadorUid = organizadorUid,
            organizadorNombre = organizadorNombre,
            organizadorFoto = organizadorFoto,
            fecha = fecha,
            maxParticipantes = maxParticipantes,
            descripcion = descripcion,
            nivelRecomendado = nivelRecomendado,
            puntoEncuentro = null,
            horaEncuentro = horaEncuentro
        )
        return eventService.createEvento(body).await()
    }

    suspend fun joinEvento(
        eventoId: String,
        uid: String,
        nombre: String,
        foto: String?
    ): JoinEventoResponse {
        val body = JoinLeaveEventoBody(uid = uid, nombre = nombre, foto = foto)
        return eventService.joinEvento(eventoId, body).await()
    }

    suspend fun leaveEvento(
        eventoId: String,
        uid: String,
        nombre: String,
        foto: String? = null
    ): LeaveEventoResponse {
        val body = JoinLeaveEventoBody(uid = uid, nombre = nombre, foto = foto)
        return eventService.leaveEvento(eventoId, body).await()
    }

    suspend fun cancelEvento(eventoId: String, uid: String): CancelEventoResponse {
        val body = SimpleUidBody(uid = uid)
        return eventService.cancelEvento(eventoId, body).await()
    }

    suspend fun finishEvento(eventoId: String, uid: String): FinishEventoResponse {
        val body = SimpleUidBody(uid = uid)
        return eventService.finishEvento(eventoId, body).await()
    }
}