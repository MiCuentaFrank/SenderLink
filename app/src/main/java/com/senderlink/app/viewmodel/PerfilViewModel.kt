package com.senderlink.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.User
import com.senderlink.app.repository.UserRepository
import okhttp3.MultipartBody

/**
 * ViewModel para la pantalla de Perfil
 * Gestiona los datos del usuario y la lógica de negocio
 */
class PerfilViewModel : ViewModel() {

    private val userRepository = UserRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()

    // Trigger para cargar usuario
    private val _loadUserTrigger = MutableLiveData<String>()

    val userResult: LiveData<UserRepository.Result<User>> =
        _loadUserTrigger.switchMap { uid ->
            userRepository.getUserByUid(uid)
        }

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> get() = _userData

    // ✅ Trigger PRO para subida de foto (sin observeForever)
    private data class UploadPhotoParams(
        val uid: String,
        val context: Context,
        val photoUri: Uri
    )

    private val _uploadPhotoTrigger = MutableLiveData<UploadPhotoParams>()

    val updatePhotoResult: LiveData<UserRepository.Result<User>> =
        _uploadPhotoTrigger.switchMap { params ->
            val part: MultipartBody.Part =
                userRepository.buildPhotoPartFromUri(params.context, params.photoUri)

            userRepository.uploadUserPhoto(params.uid, part)
        }

    fun loadUserData() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "No hay usuario autenticado"
            return
        }
        _loadUserTrigger.value = currentUser.uid
    }

    fun handleUserResult(result: UserRepository.Result<User>) {
        when (result) {
            is UserRepository.Result.Loading -> _isLoading.value = true
            is UserRepository.Result.Success -> {
                _isLoading.value = false
                _userData.value = result.data
            }
            is UserRepository.Result.Error -> {
                _isLoading.value = false
                _errorMessage.value = result.message
            }
        }
    }

    /**
     * ✅ Dispara la subida de foto (el Fragment pasa context)
     */
    fun updateProfilePhoto(context: Context, photoUri: Uri) {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "No hay usuario autenticado"
            return
        }

        _isLoading.value = true
        _uploadPhotoTrigger.value = UploadPhotoParams(
            uid = currentUser.uid,
            context = context.applicationContext,
            photoUri = photoUri
        )
    }

    /**
     * ✅ Llama esto desde el Fragment observando updatePhotoResult
     */
    fun handleUpdatePhotoResult(result: UserRepository.Result<User>) {
        when (result) {
            is UserRepository.Result.Loading -> _isLoading.value = true
            is UserRepository.Result.Success -> {
                _isLoading.value = false
                _userData.value = result.data // refresca UI con user actualizado (photoUrl)
            }
            is UserRepository.Result.Error -> {
                _isLoading.value = false
                _errorMessage.value = result.message
            }
        }
    }

    fun getCurrentUserEmail(): String {
        return firebaseAuth.currentUser?.email ?: "No disponible"
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
