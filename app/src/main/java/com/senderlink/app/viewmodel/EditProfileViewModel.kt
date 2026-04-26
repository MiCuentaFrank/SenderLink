package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.User
import com.senderlink.app.network.UpdateUserProfileRequest
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

    // Observers para evitar memory leaks
    private var userResultObserver: Observer<UserRepository.Result<User>>? = null
    private var updateObserver: Observer<UserRepository.Result<User>>? = null
    private var updateLiveData: LiveData<UserRepository.Result<User>>? = null

    fun loadCurrentUser() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "No hay usuario autenticado"
            return
        }

        val uid = currentUser.uid

        // Eliminar observer anterior ANTES de cambiar el trigger
        userResultObserver?.let { userResult.removeObserver(it) }

        _loadUserTrigger.value = uid

        val observer = Observer<UserRepository.Result<User>> { result ->
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
                    _errorMessage.value = "Error al cargar el perfil"
                }
            }
        }
        userResultObserver = observer
        userResult.observeForever(observer)
    }

    /**
     * Actualizar perfil con callback para sincronizar Firebase
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

        Log.d(TAG, "Actualizando perfil en MongoDB...")

        // ✅ Construimos el request que espera tu backend
        val req = UpdateUserProfileRequest(
            nombre = nombre,
            bio = bio,
            comunidad = comunidad,
            provincia = provincia
        )

        // Eliminar observer anterior de update
        updateObserver?.let { updateLiveData?.removeObserver(it) }

        val liveData = userRepository.updateUserProfile(uid, req)
        val observer = Observer<UserRepository.Result<User>> { result ->
            when (result) {
                is UserRepository.Result.Loading -> {
                    _isLoading.value = true
                }
                is UserRepository.Result.Success -> {
                    _isLoading.value = false
                    _updateSuccess.value = true
                    Log.d(TAG, "Perfil actualizado en MongoDB")
                    onFirebaseSyncNeeded(nombre)
                    _userData.value = result.data
                }
                is UserRepository.Result.Error -> {
                    _isLoading.value = false
                    _updateSuccess.value = false
                    _errorMessage.value = "Error al actualizar el perfil"
                    Log.e(TAG, "Error actualizando perfil")
                }
            }
        }
        updateLiveData = liveData
        updateObserver = observer
        liveData.observeForever(observer)
    }



    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        userResultObserver?.let { userResult.removeObserver(it) }
        updateObserver?.let { updateLiveData?.removeObserver(it) }
    }
}