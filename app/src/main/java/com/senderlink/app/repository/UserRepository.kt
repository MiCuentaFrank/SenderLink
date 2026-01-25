package com.senderlink.app.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.senderlink.app.model.User
import com.senderlink.app.network.CreateUserResponse
import com.senderlink.app.network.RetrofitClient
import com.senderlink.app.network.UserResponse
import com.senderlink.app.network.UserService
import com.senderlink.app.network.UploadUserPhotoResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class UserRepository {

    private val userService: UserService = RetrofitClient.instance.create(UserService::class.java)
    private val TAG = "UserRepository"

    fun getUserByUid(uid: String): LiveData<Result<User>> {
        val result = MutableLiveData<Result<User>>()
        result.value = Result.Loading

        Log.d(TAG, "Obteniendo usuario con UID: $uid")

        userService.getUserByUid(uid).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (response.isSuccessful) {
                    val userResponse = response.body()
                    if (userResponse?.ok == true) {
                        Log.d(TAG, "Usuario obtenido: ${userResponse.user.email}")
                        result.value = Result.Success(userResponse.user)
                    } else {
                        val error = userResponse?.message ?: "Error desconocido"
                        Log.e(TAG, "Error de API: $error")
                        result.value = Result.Error(error)
                    }
                } else {
                    val error = "Error del servidor: ${response.code()}"
                    Log.e(TAG, error)
                    result.value = Result.Error(error)
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                Log.e(TAG, "Error de conexión: ${t.message}")
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }

    fun createUser(user: User): LiveData<Result<User>> {
        val result = MutableLiveData<Result<User>>()
        result.value = Result.Loading

        Log.d(TAG, "Creando usuario: ${user.email}")

        userService.createUser(user).enqueue(object : Callback<CreateUserResponse> {
            override fun onResponse(call: Call<CreateUserResponse>, response: Response<CreateUserResponse>) {
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

    fun updateUserProfile(
        uid: String,
        req: com.senderlink.app.network.UpdateUserProfileRequest
    ): LiveData<Result<User>> {
        val result = MutableLiveData<Result<User>>()
        result.value = Result.Loading

        Log.d(TAG, "Actualizando perfil de UID: $uid")

        userService.updateUserProfile(uid, req)
            .enqueue(object : Callback<com.senderlink.app.network.UpdateUserProfileResponse> {
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

                override fun onFailure(
                    call: Call<com.senderlink.app.network.UpdateUserProfileResponse>,
                    t: Throwable
                ) {
                    Log.e(TAG, "Error actualizando perfil: ${t.message}")
                    result.value = Result.Error(t.message ?: "Error de conexión")
                }
            })

        return result
    }

    // =========================================================
    // ✅ SUBIDA FOTO PERFIL (multipart/form-data)
    // =========================================================

    /**
     * Helper: convierte un Uri (galería/cámara) a MultipartBody.Part
     * - Copia a cache porque Retrofit/OkHttp trabajan mejor con File real.
     */
    fun buildPhotoPartFromUri(context: Context, uri: Uri): MultipartBody.Part {
        val contentResolver = context.contentResolver

        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir el Uri de la imagen")

        val tempFile = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")

        FileOutputStream(tempFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }

        val mime = contentResolver.getType(uri) ?: "image/*"
        val requestBody = tempFile.asRequestBody(mime.toMediaTypeOrNull())

        // ✅ IMPORTANTE: el nombre del campo ("photo") debe coincidir con tu backend (multer)
        return MultipartBody.Part.createFormData(
            "photo",
            tempFile.name,
            requestBody
        )
    }

    /**
     * Sube la foto al backend:
     * PUT /api/users/{uid}/photo
     */
    fun uploadUserPhoto(uid: String, photo: MultipartBody.Part): LiveData<Result<User>> {
        val result = MutableLiveData<Result<User>>()
        result.value = Result.Loading

        Log.d(TAG, "Subiendo foto para UID: $uid")

        userService.uploadUserPhoto(uid, photo).enqueue(object : Callback<UploadUserPhotoResponse> {
            override fun onResponse(
                call: Call<UploadUserPhotoResponse>,
                response: Response<UploadUserPhotoResponse>
            ) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.ok == true) {
                        Log.d(TAG, "Foto actualizada OK")
                        result.value = Result.Success(body.user)
                    } else {
                        val error = body?.message ?: "Error al subir la foto"
                        Log.e(TAG, error)
                        result.value = Result.Error(error)
                    }
                } else {
                    val error = "Error del servidor: ${response.code()}"
                    Log.e(TAG, error)
                    result.value = Result.Error(error)
                }
            }

            override fun onFailure(call: Call<UploadUserPhotoResponse>, t: Throwable) {
                Log.e(TAG, "Error subiendo foto: ${t.message}")
                result.value = Result.Error(t.message ?: "Error de conexión")
            }
        })

        return result
    }

    sealed class Result<out T> {
        object Loading : Result<Nothing>()
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }
}
