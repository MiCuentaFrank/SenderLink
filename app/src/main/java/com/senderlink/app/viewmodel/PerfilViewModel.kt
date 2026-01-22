package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.User
import com.senderlink.app.repository.UserRepository

/**
 * ViewModel para la pantalla de Perfil
 * Gestiona los datos del usuario y la lógica de negocio
 */
class PerfilViewModel : ViewModel() {

    // Repository para acceder a los datos
    private val userRepository = UserRepository()

    // Firebase Auth para obtener el UID del usuario actual
    private val firebaseAuth = FirebaseAuth.getInstance()

    // LiveData privado que controla cuándo cargar el usuario
    private val _loadUserTrigger = MutableLiveData<String>()

    /**
     * LiveData público que expone el resultado de cargar el usuario
     * Usamos switchMap para reaccionar cuando _loadUserTrigger cambia
     */
    val userResult: LiveData<UserRepository.Result<User>> =
        _loadUserTrigger.switchMap { uid ->
            // Cuando _loadUserTrigger cambia, llamamos al repository
            userRepository.getUserByUid(uid)
        }

    // LiveData para el estado de carga
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    // LiveData para mensajes de error (ahora nullable)
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    // LiveData para los datos del usuario (simplificado para la UI)
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> get() = _userData

    /**
     * Carga los datos del usuario actual desde el backend
     * Usa el UID del usuario autenticado en Firebase
     */
    fun loadUserData() {
        // Obtener el usuario actual de Firebase
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            _errorMessage.value = "No hay usuario autenticado"
            return
        }

        // Obtener el UID y disparar la carga
        val uid = currentUser.uid
        _loadUserTrigger.value = uid
    }

    /**
     * Procesa el resultado de la carga del usuario
     * Este método debe ser llamado desde el Fragment observando userResult
     */
    fun handleUserResult(result: UserRepository.Result<User>) {
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

    /**
     * Obtiene el email del usuario actual de Firebase
     * Es un método auxiliar por si no tenemos los datos del backend
     */
    fun getCurrentUserEmail(): String {
        return firebaseAuth.currentUser?.email ?: "No disponible"
    }

    /**
     * Limpia el mensaje de error
     */
    fun clearError() {
        _errorMessage.value = null
    }
}