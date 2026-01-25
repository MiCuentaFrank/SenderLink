package com.senderlink.app.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.network.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
    // MÉTODOS SÍNCRONOS (coroutines + await)
    // ========================================

    /**
     * 📋 Obtener lista de eventos (con filtros)
     * ✅ AHORA corrige flags localmente por si el backend no los envía correctamente
     */
    suspend fun getEventos(
        estado: String? = null,
        routeId: String? = null,
        uid: String? = null,
        limit: Int = 20,
        skip: Int = 0
    ): EventosResponse {
        val response = eventService.getEventos(estado, routeId, uid, limit, skip).await()

        // ✅ CORRECCIÓN: Si tenemos UID, corregimos los flags localmente
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
     * ✅ También corrige flags si se proporciona uid
     */
    suspend fun getEventoById(eventoId: String, uid: String? = null): EventoDetailResponse {
        val response = eventService.getEventoById(eventoId).await()

        // ✅ CORRECCIÓN: Si tenemos UID, corregimos los flags localmente
        if (response.ok && response.data != null && !uid.isNullOrBlank()) {
            val eventoCorregido = corregirFlags(response.data, uid)
            Log.d(TAG, "✅ getEventoById: evento con flags corregidos")
            return response.copy(data = eventoCorregido)
        }

        return response
    }

    /**
     * 👤 Mis eventos
     * ✅ CORRECCIÓN: Forzar isOrganizer = true porque son eventos que YO organizo
     */
    suspend fun getEventosByUser(uid: String): UserEventosResponse {
        val response = eventService.getEventosByUser(uid).await()

        // ✅ Corregir flags: si son "mis eventos", soy el organizador
        if (response.ok && response.data != null) {
            val eventosCorregidos = response.data.map { evento ->
                evento.copy(
                    isOrganizer = true,    // ← Siempre true (tú organizas)
                    isParticipant = false  // ← Siempre false (no eres participante de tu propio evento)
                )
            }

            Log.d(TAG, "✅ getEventosByUser: ${eventosCorregidos.size} eventos con flags corregidos")

            return response.copy(data = eventosCorregidos)
        }

        return response
    }

    /**
     * 👥 Eventos donde participo
     * ✅ CORRECCIÓN: Forzar isParticipant = true porque son eventos donde participo
     */
    suspend fun getEventosParticipando(uid: String): UserEventosResponse {
        val response = eventService.getEventosParticipando(uid).await()

        // ✅ Corregir flags: si estoy participando, no soy el organizador
        if (response.ok && response.data != null) {
            val eventosCorregidos = response.data.map { evento ->
                evento.copy(
                    isParticipant = true,   // ← Siempre true (estás participando)
                    isOrganizer = false     // ← Siempre false (no organizas estos eventos)
                )
            }

            Log.d(TAG, "✅ getEventosParticipando: ${eventosCorregidos.size} eventos con flags corregidos")

            return response.copy(data = eventosCorregidos)
        }

        return response
    }

    // ========================================
    // MÉTODOS ASÍNCRONOS (LiveData)
    // ========================================

    fun createEvento(
        routeId: String,
        organizadorUid: String,
        organizadorNombre: String,
        organizadorFoto: String?,
        fecha: String,
        maxParticipantes: Int = 10,
        descripcion: String? = null,
        nivelRecomendado: String? = null,
        horaEncuentro: String = "09:00"
    ): LiveData<Result<EventoGrupal>> {

        val result = MutableLiveData<Result<EventoGrupal>>()
        result.value = Result.Loading

        Log.d(TAG, "Creando evento para ruta: $routeId")

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

        eventService.createEvento(body).enqueue(object : Callback<CreateEventoResponse> {
            override fun onResponse(
                call: Call<CreateEventoResponse>,
                response: Response<CreateEventoResponse>
            ) {
                if (response.isSuccessful) {
                    val createResponse = response.body()
                    if (createResponse?.ok == true) {
                        Log.d(TAG, "✅ Evento creado: ${createResponse.data.id}")
                        result.value = Result.Success(createResponse.data)
                    } else {
                        val error = createResponse?.message ?: "Error al crear evento"
                        Log.e(TAG, "❌ $error")
                        result.value = Result.Error(error)
                    }
                } else {
                    val error = "Error del servidor: ${response.code()}"
                    Log.e(TAG, "❌ $error")
                    result.value = Result.Error(error)
                }
            }

            override fun onFailure(call: Call<CreateEventoResponse>, t: Throwable) {
                val error = t.message ?: "Error de conexión"
                Log.e(TAG, "❌ Error al crear evento: $error", t)
                result.value = Result.Error(error)
            }
        })

        return result
    }

    fun joinEvento(
        eventoId: String,
        uid: String,
        nombre: String,
        foto: String?
    ): LiveData<Result<EventoGrupal>> {

        val result = MutableLiveData<Result<EventoGrupal>>()
        result.value = Result.Loading

        Log.d(TAG, "Usuario $uid uniéndose a evento $eventoId")

        val body = JoinLeaveEventoBody(uid = uid, nombre = nombre, foto = foto)

        eventService.joinEvento(eventoId, body).enqueue(object : Callback<JoinEventoResponse> {
            override fun onResponse(
                call: Call<JoinEventoResponse>,
                response: Response<JoinEventoResponse>
            ) {
                if (response.isSuccessful) {
                    val joinResponse = response.body()
                    if (joinResponse?.ok == true) {
                        result.value = Result.Success(joinResponse.data)
                    } else {
                        val error = joinResponse?.message ?: "Error al unirse al evento"
                        result.value = Result.Error(error)
                    }
                } else {
                    result.value = Result.Error("Error del servidor: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<JoinEventoResponse>, t: Throwable) {
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }

    fun leaveEvento(
        eventoId: String,
        uid: String,
        nombre: String,
        foto: String? = null
    ): LiveData<Result<EventoGrupal>> {

        val result = MutableLiveData<Result<EventoGrupal>>()
        result.value = Result.Loading

        val body = JoinLeaveEventoBody(uid = uid, nombre = nombre, foto = foto)

        eventService.leaveEvento(eventoId, body).enqueue(object : Callback<LeaveEventoResponse> {
            override fun onResponse(
                call: Call<LeaveEventoResponse>,
                response: Response<LeaveEventoResponse>
            ) {
                if (response.isSuccessful) {
                    val leaveResponse = response.body()
                    if (leaveResponse?.ok == true) {
                        result.value = Result.Success(leaveResponse.data)
                    } else {
                        result.value = Result.Error(
                            leaveResponse?.message ?: "Error al salir del evento"
                        )
                    }
                } else {
                    result.value = Result.Error("Error del servidor: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<LeaveEventoResponse>, t: Throwable) {
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }

    fun cancelEvento(eventoId: String, uid: String): LiveData<Result<EventoGrupal>> {

        val result = MutableLiveData<Result<EventoGrupal>>()
        result.value = Result.Loading

        val body = SimpleUidBody(uid = uid)

        eventService.cancelEvento(eventoId, body).enqueue(object : Callback<CancelEventoResponse> {
            override fun onResponse(
                call: Call<CancelEventoResponse>,
                response: Response<CancelEventoResponse>
            ) {
                if (response.isSuccessful) {
                    val cancelResponse = response.body()
                    if (cancelResponse?.ok == true) {
                        result.value = Result.Success(cancelResponse.data)
                    } else {
                        result.value = Result.Error(
                            cancelResponse?.message ?: "Error al cancelar evento"
                        )
                    }
                } else {
                    result.value = Result.Error("Error del servidor: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<CancelEventoResponse>, t: Throwable) {
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }

    fun finishEvento(eventoId: String, uid: String): LiveData<Result<EventoGrupal>> {

        val result = MutableLiveData<Result<EventoGrupal>>()
        result.value = Result.Loading

        val body = SimpleUidBody(uid = uid)

        eventService.finishEvento(eventoId, body).enqueue(object : Callback<FinishEventoResponse> {
            override fun onResponse(
                call: Call<FinishEventoResponse>,
                response: Response<FinishEventoResponse>
            ) {
                if (response.isSuccessful) {
                    val finishResponse = response.body()
                    if (finishResponse?.ok == true) {
                        result.value = Result.Success(finishResponse.data)
                    } else {
                        result.value = Result.Error(
                            finishResponse?.message ?: "Error al finalizar evento"
                        )
                    }
                } else {
                    result.value = Result.Error("Error del servidor: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<FinishEventoResponse>, t: Throwable) {
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }

    // ========================================
    // RESULT
    // ========================================

    sealed class Result<out T> {
        object Loading : Result<Nothing>()
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }
}