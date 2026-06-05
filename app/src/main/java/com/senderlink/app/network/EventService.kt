package com.senderlink.app.network

import com.senderlink.app.model.CreateEventoBody
import com.senderlink.app.model.JoinLeaveEventoBody
import com.senderlink.app.model.SimpleUidBody
import com.senderlink.app.model.UpdateEventoBody
import retrofit2.Call
import retrofit2.http.*

/**
 * 🎯 EventService - Servicio de Rutas Grupales
 * BACKEND: /api/events/...
 */
interface EventService {

    // ========================================
    // GET - Consultas
    // ========================================

    /**
     * 📋 Listar eventos (con filtros)
     * GET /api/events?estado=&routeId=&uid=&limit=&skip=
     *
     * ✅ CLAVE: si pasas uid, backend devuelve isParticipant / isOrganizer
     */
    @GET("api/events")
    fun getEventos(
        @Query("estado") estado: String? = null,
        @Query("routeId") routeId: String? = null,
        @Query("uid") uid: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("skip") skip: Int = 0
    ): Call<EventosResponse>

    /**
     * 🆔 Obtener evento por ID
     * ✅ uid opcional: si se pasa, backend devuelve isParticipant / isOrganizer
     */
    @GET("api/events/{id}")
    fun getEventoById(
        @Path("id") eventoId: String,
        @Query("uid") uid: String? = null
    ): Call<EventoDetailResponse>

    /**
     * 👤 Mis eventos (organizados por mí)
     */
    @GET("api/events/user/{uid}")
    fun getEventosByUser(@Path("uid") uid: String): Call<UserEventosResponse>

    /**
     * 👥 Eventos donde participo
     */
    @GET("api/events/participating/{uid}")
    fun getEventosParticipando(@Path("uid") uid: String): Call<UserEventosResponse>

    // ========================================
    // POST - Crear y acciones
    // ========================================

    @POST("api/events")
    fun createEvento(@Body body: CreateEventoBody): Call<CreateEventoResponse>

    @POST("api/events/{id}/join")
    fun joinEvento(
        @Path("id") eventoId: String,
        @Body body: JoinLeaveEventoBody
    ): Call<JoinEventoResponse>

    @POST("api/events/{id}/leave")
    fun leaveEvento(
        @Path("id") eventoId: String,
        @Body body: JoinLeaveEventoBody
    ): Call<LeaveEventoResponse>

    @POST("api/events/{id}/cancel")
    fun cancelEvento(
        @Path("id") eventoId: String,
        @Body body: SimpleUidBody
    ): Call<CancelEventoResponse>

    @POST("api/events/{id}/finish")
    fun finishEvento(
        @Path("id") eventoId: String,
        @Body body: SimpleUidBody
    ): Call<FinishEventoResponse>

    // ========================================
    // PUT - Actualizar
    // ========================================

    @PUT("api/events/{id}")
    fun updateEvento(
        @Path("id") eventoId: String,
        @Body body: UpdateEventoBody
    ): Call<UpdateEventoResponse>
}
