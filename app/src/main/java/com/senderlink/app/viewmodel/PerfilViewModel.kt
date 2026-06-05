package com.senderlink.app.viewmodel

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

    // Trigger para subir foto de perfil vía backend (multer + Firebase Admin)
    private val _photoPartTrigger = MutableLiveData<MultipartBody.Part>()

    val updatePhotoResult: LiveData<UserRepository.Result<User>> =
        _photoPartTrigger.switchMap { part ->
            val uid = firebaseAuth.currentUser?.uid
                ?: return@switchMap MutableLiveData<UserRepository.Result<User>>().apply {
                    value = UserRepository.Result.Error("No hay usuario autenticado")
                }
            userRepository.uploadUserPhoto(uid, part)
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
                _errorMessage.value = "Error de conexión. Intenta de nuevo."
            }
        }
    }

    /**
     * Sube la foto de perfil via el backend (PUT /api/users/{uid}/photo).
     */
    fun uploadProfilePhoto(part: MultipartBody.Part) {
        _isLoading.value = true
        _photoPartTrigger.value = part
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
                _errorMessage.value = "Error de conexión. Intenta de nuevo."
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
