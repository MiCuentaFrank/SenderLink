package com.senderlink.app.network

import com.senderlink.app.model.User
import retrofit2.Call
import retrofit2.http.*
import okhttp3.MultipartBody

/**
 * Interfaz que define los endpoints de la API relacionados con usuarios
 *
 * ARQUITECTURA:
 * - Retrofit convierte estas funciones en llamadas HTTP
 * - Cada función devuelve Call<T> que se ejecuta de forma asíncrona
 * - Las rutas incluyen "api/" porque BASE_URL = "http://IP:3000/"
 *
 * ENDPOINTS DISPONIBLES:
 * 1. getUserByUid - Obtener usuario por UID
 * 2. createUser - Crear nuevo usuario
 * 3. updateUser - Actualizar usuario completo
 * 4. updateUserProfile - Actualizar solo perfil (seguro)
 */
interface UserService {

    /**
     * Obtener un usuario por su UID de Firebase
     *
     * ENDPOINT: GET /api/users/:uid
     * EJEMPLO: GET /api/users/abc123
     *
     * @param uid - UID del usuario en Firebase
     * @return Respuesta con los datos del usuario
     */
    @GET("api/users/{uid}")
    fun getUserByUid(@Path("uid") uid: String): Call<UserResponse>

    /**
     * Crear un nuevo usuario en el backend
     *
     * ENDPOINT: POST /api/users
     * BODY: { uid, email, nombre?, foto? }
     *
     * @param user - Objeto User con los datos mínimos (uid, email)
     * @return Respuesta con el usuario creado
     */
    @POST("api/users")
    fun createUser(@Body user: User): Call<CreateUserResponse>

    /**
     * Actualizar datos de un usuario (cualquier campo)
     *
     * ENDPOINT: PUT /api/users/:uid
     * BODY: { campo1: valor1, campo2: valor2, ... }
     *
     * ⚠️ NOTA: Este endpoint permite actualizar CUALQUIER campo.
     * Para actualizaciones de perfil seguras, usar updateUserProfile
     *
     * @param uid - UID del usuario
     * @param updates - Mapa de campos a actualizar
     * @return Respuesta con el usuario actualizado
     */
    @PUT("api/users/{uid}")
    fun updateUser(
        @Path("uid") uid: String,
        @Body updates: Map<String, Any>
    ): Call<UpdateUserResponse>

    /**
     * Actualizar SOLO perfil (campos editables por el usuario)
     *
     * ENDPOINT: PUT /api/users/:uid/profile
     * BODY: UpdateUserProfileRequest
     *
     * ✅ SEGURO: Solo permite actualizar campos de perfil (whitelist en backend)
     * ✅ CALCULA: profileCompletion automáticamente
     *
     * CAMPOS PERMITIDOS:
     * - nombre, foto, bio
     * - comunidad, provincia
     * - preferencias (nivel, tipos, distanciaKm)
     *
     * @param uid - UID del usuario
     * @param body - Datos del perfil a actualizar
     * @return Respuesta con el usuario actualizado
     */
    @PUT("api/users/{uid}/profile")
    fun updateUserProfile(
        @Path("uid") uid: String,
        @Body body: UpdateUserProfileRequest
    ): Call<UpdateUserProfileResponse>

    /**
     * Subir / actualizar foto de perfil
     *
     * ENDPOINT: PUT /api/users/:uid/photo
     * CONTENT-TYPE: multipart/form-data
     *
     * @param uid UID del usuario
     * @param photo Imagen de perfil
     */
    @Multipart
    @POST("api/users/{uid}/photo")   // <-- POST, no PUT
    fun uploadUserPhoto(
        @Path("uid") uid: String,
        @Part photo: MultipartBody.Part
    ): Call<UploadUserPhotoResponse>




}

// ========================================
// REQUEST MODELS
// ========================================

/**
 * Request para actualizar perfil
 *
 * Todos los campos son opcionales (valores por defecto)
 * Solo se envían al backend los que se especifiquen
 */
data class UpdateUserProfileRequest(
    val nombre: String = "",
    val foto: String = "",
    val bio: String = "",
    val comunidad: String = "",
    val provincia: String = "",
    val preferencias: PreferenciasRequest = PreferenciasRequest()
)

/**
 * Preferencias de rutas
 *
 * Se envían como parte de UpdateUserProfileRequest
 */
data class PreferenciasRequest(
    val nivel: String = "",
    val tipos: List<String> = emptyList(),
    val distanciaKm: Double = 0.0
)

// ========================================
// RESPONSE MODELS (ya definidos en ApiResponse.kt)
// ========================================

/**
 * Respuesta al actualizar perfil
 *
 * EJEMPLO:
 * {
 *   "ok": true,
 *   "message": "Perfil actualizado correctamente",
 *   "user": { ... }
 * }
 */
data class UpdateUserProfileResponse(
    val ok: Boolean,
    val message: String? = null,
    val user: User
)