package com.senderlink.app.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 🎯 EventService - Servicio de Rutas Grupales
 *
 * BASE URL: http://TU_IP:3000/
 * Rutas reales: /api/events/...
 * BACKEND: eventController.js
 */
interface EventService {

    // ========================================
    // GET - Consultas
    // ========================================

    /**
     * 📋 Listar eventos (con filtros)
     * GET /api/events
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
     * GET /api/events/:id
     */
    @GET("api/events/{id}")
    fun getEventoById(
        @Path("id") eventoId: String
    ): Call<EventoDetailResponse>

    /**
     * 👤 Eventos organizados por usuario
     */
    @GET("api/events/user/{uid}")
    fun getEventosByUser(
        @Path("uid") uid: String
    ): Call<UserEventosResponse>

    /**
     * 👥 Eventos donde participa el usuario
     */
    @GET("api/events/participating/{uid}")
    fun getEventosParticipando(
        @Path("uid") uid: String
    ): Call<UserEventosResponse>

    // ========================================
    // POST - Crear y acciones
    // ========================================

    /**
     * ➕ Crear evento
     * POST /api/events
     */
    @POST("api/events")
    fun createEvento(
        @Body body: CreateEventoBody
    ): Call<CreateEventoResponse>

    /**
     * 👍 Unirse a evento
     * POST /api/events/:id/join
     * Body: { uid, nombre, foto? }
     */
    @POST("api/events/{id}/join")
    fun joinEvento(
        @Path("id") eventoId: String,
        @Body body: JoinLeaveEventoBody
    ): Call<JoinEventoResponse>

    /**
     * 👎 Salir de evento
     * POST /api/events/:id/leave
     * Body: { uid }
     */
    @POST("api/events/{id}/leave")
    fun leaveEvento(
        @Path("id") eventoId: String,
        @Body body: JoinLeaveEventoBody
    ): Call<LeaveEventoResponse>

    /**
     * ❌ Cancelar evento
     * POST /api/events/:id/cancel
     * Body: { uid }
     */
    @POST("api/events/{id}/cancel")
    fun cancelEvento(
        @Path("id") eventoId: String,
        @Body body: SimpleUidBody
    ): Call<CancelEventoResponse>

    /**
     * ✅ Finalizar evento
     * POST /api/events/:id/finish
     * Body: { uid }
     */
    @POST("api/events/{id}/finish")
    fun finishEvento(
        @Path("id") eventoId: String,
        @Body body: SimpleUidBody
    ): Call<FinishEventoResponse>

    // ========================================
    // PUT - Actualizar
    // ========================================

    /**
     * ✏️ Actualizar evento
     * PUT /api/events/:id
     */
    @PUT("api/events/{id}")
    fun updateEvento(
        @Path("id") eventoId: String,
        @Body body: UpdateEventoBody
    ): Call<UpdateEventoResponse>
}
