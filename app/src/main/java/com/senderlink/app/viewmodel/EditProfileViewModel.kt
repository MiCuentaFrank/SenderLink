package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.User
import com.senderlink.app.repository.UserRepository

class EditProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "EditProfileViewModel"

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

    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> get() = _updateSuccess

    fun loadCurrentUser() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "No hay usuario autenticado"
            return
        }

        val uid = currentUser.uid
        _loadUserTrigger.value = uid

        // Observar resultado
        userResult.observeForever { result ->
            when (result) {
                is UserRepository.Result.Loading -> {
                    _isLoading.value = true
                }
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
    }

    /**
     * ✅ Actualizar perfil con callback para sincronizar Firebase
     */
    fun updateProfile(
        nombre: String,
        bio: String,
        comunidad: String,
        provincia: String,
        onFirebaseSyncNeeded: (String) -> Unit
    ) {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "No hay usuario autenticado"
            return
        }

        val uid = currentUser.uid

        // ✅ Estado inicial UI
        _isLoading.value = true
        _updateSuccess.value = false
        _errorMessage.value = null

        Log.d(TAG, "📤 Actualizando perfil en MongoDB uid=$uid ...")

        // ✅ Construimos el request que espera tu backend
        val req = com.senderlink.app.network.UpdateUserProfileRequest(
            nombre = nombre,
            bio = bio,
            comunidad = comunidad,
            provincia = provincia
        )

        userRepository.updateUserProfile(uid, req).observeForever { result ->
            when (result) {

                is UserRepository.Result.Loading -> {
                    _isLoading.value = true
                }

                is UserRepository.Result.Success -> {
                    _isLoading.value = false
                    _updateSuccess.value = true

                    Log.d(TAG, "✅ Perfil actualizado en MongoDB")
                    Log.d(TAG, "🔄 Sincronizando Firebase displayName...")

                    // ✅ sincroniza Firebase displayName
                    onFirebaseSyncNeeded(nombre)

                    // ✅ opcional: refrescar datos en pantalla con lo que devuelve el backend
                    _userData.value = result.data
                }

                is UserRepository.Result.Error -> {
                    _isLoading.value = false
                    _updateSuccess.value = false
                    _errorMessage.value = result.message

                    Log.e(TAG, "❌ Error actualizando perfil: ${result.message}")
                }
            }
        }
    }



    fun clearError() {
        _errorMessage.value = null
    }
}