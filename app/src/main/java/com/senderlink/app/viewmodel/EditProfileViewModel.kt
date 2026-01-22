package com.senderlink.app.viewmodel

import androidx.lifecycle.*
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.User
import com.senderlink.app.network.PreferenciasRequest
import com.senderlink.app.network.UpdateUserProfileRequest
import com.senderlink.app.repository.UserRepository

/**
 * ViewModel para EditProfileFragment
 *
 * PATRÓN ARQUITECTÓNICO:
 * - Usa switchMap para reaccionar a triggers
 * - MediatorLiveData para consolidar múltiples fuentes
 * - Event wrapper para eventos de una sola vez (navegación)
 *
 * VENTAJAS:
 * - No usa observeForever (evita memory leaks)
 * - Separa estado de UI del estado de datos
 * - Maneja rotaciones correctamente
 */
class EditProfileViewModel : ViewModel() {

    private val repo = UserRepository()

    // UID del usuario actual de Firebase
    private val uid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    // --- EVENT WRAPPER ---
    // Evita que eventos se repitan tras rotaciones
    class Event<out T>(private val content: T) {
        private var handled = false
        fun getContentIfNotHandled(): T? {
            if (handled) return null
            handled = true
            return content
        }
    }

    // --- TRIGGERS ---
    // Estos LiveData "disparan" acciones cuando cambian
    private val loadTrigger = MutableLiveData<Unit>()
    private val saveTrigger = MutableLiveData<UpdateUserProfileRequest>()

    // --- RESULTADOS DEL REPOSITORY ---
    // switchMap: cuando loadTrigger cambia, ejecuta getUserByUid
    private val loadResult: LiveData<UserRepository.Result<User>> = loadTrigger.switchMap {
        val u = uid
        if (u == null) {
            liveData { emit(UserRepository.Result.Error("No hay usuario autenticado")) }
        } else {
            repo.getUserByUid(u)
        }
    }

    // switchMap: cuando saveTrigger cambia, ejecuta updateUserProfile
    private val saveResult: LiveData<UserRepository.Result<User>> = saveTrigger.switchMap { req ->
        val u = uid
        if (u == null) {
            liveData { emit(UserRepository.Result.Error("No hay usuario autenticado")) }
        } else {
            repo.updateUserProfile(u, req)
        }
    }

    // --- ESTADO PÚBLICO CONSOLIDADO ---
    // MediatorLiveData combina múltiples fuentes en una sola

    private val _userData = MediatorLiveData<User?>()
    val userData: LiveData<User?> = _userData

    private val _isLoading = MediatorLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MediatorLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _savedEvent = MutableLiveData<Event<Boolean>>()
    val savedEvent: LiveData<Event<Boolean>> = _savedEvent

    init {
        // Valores iniciales
        _isLoading.value = false
        _errorMessage.value = null

        // CONSOLIDAR DATOS DE USUARIO
        // Escucha loadResult Y saveResult y actualiza _userData
        _userData.addSource(loadResult) { handleResult(it, fromSave = false) }
        _userData.addSource(saveResult) { handleResult(it, fromSave = true) }

        // CONSOLIDAR LOADING
        // Si cualquiera está Loading, mostrar loading
        _isLoading.addSource(loadResult) {
            _isLoading.value = it is UserRepository.Result.Loading
        }
        _isLoading.addSource(saveResult) {
            _isLoading.value = it is UserRepository.Result.Loading
        }

        // CONSOLIDAR ERRORES
        // Si cualquiera tiene error, mostrarlo
        _errorMessage.addSource(loadResult) {
            if (it is UserRepository.Result.Error) _errorMessage.value = it.message
        }
        _errorMessage.addSource(saveResult) {
            if (it is UserRepository.Result.Error) _errorMessage.value = it.message
        }
    }

    /**
     * Maneja el resultado de cargar o guardar
     *
     * @param result - Resultado del repository
     * @param fromSave - true si viene de guardar, false si viene de cargar
     */
    private fun handleResult(result: UserRepository.Result<User>, fromSave: Boolean) {
        when (result) {
            is UserRepository.Result.Success -> {
                _userData.value = result.data
                // Solo emitir evento de guardado si viene de saveProfile
                if (fromSave) {
                    _savedEvent.value = Event(true)
                }
            }
            is UserRepository.Result.Error -> {
                // El error ya se propaga por _errorMessage
            }
            is UserRepository.Result.Loading -> {
                // El loading ya se propaga por _isLoading
            }
        }
    }

    /**
     * Carga los datos del usuario actual
     * TRIGGER: Cambia loadTrigger -> dispara switchMap -> llama repo.getUserByUid
     */
    fun loadUserData() {
        loadTrigger.value = Unit
    }

    /**
     * Guarda los cambios del perfil
     *
     * @param nombre - Nombre del usuario
     * @param bio - Biografía
     * @param comunidad - Comunidad autónoma
     * @param provincia - Provincia
     * @param nivel - Nivel preferido (BEGINNER, INTERMEDIATE, etc)
     * @param tiposCsv - Tipos de rutas separados por coma
     * @param distanciaText - Distancia típica en km (como texto)
     */
    fun saveProfile(
        nombre: String,
        bio: String,
        comunidad: String,
        provincia: String,
        nivel: String,
        tiposCsv: String,
        distanciaText: String
    ) {
        // Procesar tipos: convertir CSV a lista
        val tipos = tiposCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Procesar distancia: convertir texto a número
        val distancia = distanciaText
            .replace(",", ".") // Por si usan coma como decimal
            .toDoubleOrNull() ?: 0.0

        // Crear el objeto de request
        val req = UpdateUserProfileRequest(
            nombre = nombre.trim(),
            bio = bio.trim(),
            comunidad = comunidad.trim(),
            provincia = provincia.trim(),
            preferencias = PreferenciasRequest(
                nivel = nivel.trim(),
                tipos = tipos,
                distanciaKm = distancia
            )
        )

        // TRIGGER: Cambiar saveTrigger -> dispara switchMap -> llama repo.updateUserProfile
        saveTrigger.value = req
    }

    /**
     * Limpia el mensaje de error
     * Se llama después de mostrar el Toast
     */
    fun clearError() {
        _errorMessage.value = null
    }
}