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
    // MÉTODOS SÍNCRONOS (coroutines + await)
    // ========================================

    /**
     * 📋 Obtener lista de eventos (con filtros)
     *
     * ✅ Para RouteDetail (CLAVE):
     * routeId + uid => backend devuelve isParticipant / isOrganizer
     */
    suspend fun getEventos(
        estado: String? = null,
        routeId: String? = null,
        uid: String? = null,
        limit: Int = 20,
        skip: Int = 0
    ): EventosResponse {
        return eventService.getEventos(estado, routeId, uid, limit, skip).await()
    }

    suspend fun getEventoById(eventoId: String): EventoDetailResponse {
        return eventService.getEventoById(eventoId).await()
    }

    suspend fun getEventosByUser(uid: String): UserEventosResponse {
        return eventService.getEventosByUser(uid).await()
    }

    suspend fun getEventosParticipando(uid: String): UserEventosResponse {
        return eventService.getEventosParticipando(uid).await()
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

        val body = JoinLeaveEventoBody(
            uid = uid,
            nombre = nombre,
            foto = foto
        )

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

    // ✅ PASO 1: FUNCIÓN CORREGIDA
    fun leaveEvento(
        eventoId: String,
        uid: String,
        nombre: String,
        foto: String? = null
    ): LiveData<Result<EventoGrupal>> {

        val result = MutableLiveData<Result<EventoGrupal>>()
        result.value = Result.Loading

        // ✅ Ahora pasamos nombre y foto correctamente
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