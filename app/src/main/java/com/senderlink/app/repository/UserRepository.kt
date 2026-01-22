package com.senderlink.app.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.senderlink.app.model.User
import com.senderlink.app.network.UserResponse
import com.senderlink.app.network.CreateUserResponse
import com.senderlink.app.network.RetrofitClient
import com.senderlink.app.network.UserService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Repository que gestiona las operaciones relacionadas con usuarios
 * Actúa como intermediario entre el ViewModel y la capa de red
 */
class UserRepository {

    // Instancia del servicio de usuarios
    private val userService: UserService = RetrofitClient.instance.create(UserService::class.java)

    // Tag para los logs
    private val TAG = "UserRepository"

    /**
     * Obtiene los datos de un usuario por su UID
     *
     * @param uid - ID único del usuario de Firebase
     * @return LiveData con el resultado de la operación
     */
    fun getUserByUid(uid: String): LiveData<Result<User>> {
        val result = MutableLiveData<Result<User>>()

        // Indicamos que estamos cargando
        result.value = Result.Loading

        Log.d(TAG, "Obteniendo usuario con UID: $uid")

        // Hacemos la llamada al backend
        userService.getUserByUid(uid).enqueue(object : Callback<UserResponse> {
            override fun onResponse(
                call: Call<UserResponse>,
                response: Response<UserResponse>
            ) {
                if (response.isSuccessful) {
                    // La respuesta fue exitosa (código 200-299)
                    val userResponse = response.body()

                    if (userResponse?.ok == true) {
                        // Todo correcto, tenemos el usuario
                        Log.d(TAG, "Usuario obtenido: ${userResponse.user.email}")
                        result.value = Result.Success(userResponse.user)
                    } else {
                        // La API respondió pero con un error
                        val error = userResponse?.message ?: "Error desconocido"
                        Log.e(TAG, "Error de API: $error")
                        result.value = Result.Error(error)
                    }
                } else {
                    // Error HTTP (404, 500, etc.)
                    val error = "Error del servidor: ${response.code()}"
                    Log.e(TAG, error)
                    result.value = Result.Error(error)
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                // Error de red o conexión
                Log.e(TAG, "Error de conexión: ${t.message}")
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }

    /**
     * Crea un nuevo usuario en el backend
     *
     * @param user - Datos del usuario a crear
     * @return LiveData con el resultado de la operación
     */
    fun createUser(user: User): LiveData<Result<User>> {
        val result = MutableLiveData<Result<User>>()
        result.value = Result.Loading

        Log.d(TAG, "Creando usuario: ${user.email}")

        userService.createUser(user).enqueue(object : Callback<CreateUserResponse> {
            override fun onResponse(
                call: Call<CreateUserResponse>,
                response: Response<CreateUserResponse>
            ) {
                if (response.isSuccessful) {
                    val createResponse = response.body()

                    if (createResponse?.ok == true) {
                        Log.d(TAG, "Usuario creado correctamente")
                        result.value = Result.Success(createResponse.user)
                    } else {
                        val error = createResponse?.message ?: "Error al crear usuario"
                        Log.e(TAG, error)
                        result.value = Result.Error(error)
                    }
                } else {
                    result.value = Result.Error("Error del servidor: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<CreateUserResponse>, t: Throwable) {
                Log.e(TAG, "Error al crear usuario: ${t.message}")
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }
    fun updateUserProfile(uid: String, req: com.senderlink.app.network.UpdateUserProfileRequest): LiveData<Result<User>> {
        val result = MutableLiveData<Result<User>>()
        result.value = Result.Loading

        Log.d(TAG, "Actualizando perfil de UID: $uid")

        userService.updateUserProfile(uid, req).enqueue(object : Callback<com.senderlink.app.network.UpdateUserProfileResponse> {
            override fun onResponse(
                call: Call<com.senderlink.app.network.UpdateUserProfileResponse>,
                response: Response<com.senderlink.app.network.UpdateUserProfileResponse>
            ) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.ok == true) {
                        Log.d(TAG, "Perfil actualizado correctamente")
                        result.value = Result.Success(body.user)
                    } else {
                        val error = body?.message ?: "Error al actualizar perfil"
                        Log.e(TAG, error)
                        result.value = Result.Error(error)
                    }
                } else {
                    val error = "Error del servidor: ${response.code()}"
                    Log.e(TAG, error)
                    result.value = Result.Error(error)
                }
            }

            override fun onFailure(call: Call<com.senderlink.app.network.UpdateUserProfileResponse>, t: Throwable) {
                Log.e(TAG, "Error actualizando perfil: ${t.message}")
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }


    /**
     * Clase sellada que representa los diferentes estados de una operación
     * Permite manejar: Cargando, Éxito o Error
     */
    sealed class Result<out T> {
        object Loading : Result<Nothing>()
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }
}