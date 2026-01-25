package com.senderlink.app.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.User
import com.senderlink.app.repository.UserRepository

/**
 * 🎯 UserManager - Gestor Singleton del Usuario Actual
 *
 * RESPONSABILIDADES:
 * 1. Mantener en caché los datos del usuario actual
 * 2. Proporcionar acceso rápido a los datos del perfil
 * 3. Sincronizar automáticamente cuando el perfil cambia
 * 4. Evitar requests innecesarios al backend
 *
 * USO:
 * ```kotlin
 * // Obtener instancia
 * val userManager = UserManager.getInstance()
 *
 * // Cargar datos del usuario
 * userManager.loadCurrentUser()
 *
 * // Observar cambios
 * userManager.currentUser.observe(viewLifecycleOwner) { user ->
 *     user?.let {
 *         tvName.text = it.nombre
 *     }
 * }
 *
 * // Obtener nombre rápido
 * val nombre = userManager.getUserName()
 * ```
 */
class UserManager private constructor() {

    private val repository = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    private val TAG = "UserManager"

    // 📦 Usuario actual en caché
    private val _currentUser = MutableLiveData<User?>(null)
    val currentUser: LiveData<User?> = _currentUser

    // 🔄 Estado de carga
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // ❌ Errores
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    companion object {
        @Volatile
        private var INSTANCE: UserManager? = null

        /**
         * Obtiene la instancia única del UserManager
         */
        fun getInstance(): UserManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserManager().also { INSTANCE = it }
            }
        }
    }

    /**
     * 🔄 Carga los datos del usuario actual desde el backend
     *
     * - Si ya están en caché, no hace nada (usa forceRefresh = true para forzar)
     * - Automáticamente obtiene el UID de Firebase Auth
     * - Actualiza el caché con los datos más recientes
     *
     * @param forceRefresh - Si true, recarga aunque ya esté en caché
     */
    fun loadCurrentUser(forceRefresh: Boolean = false) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Log.w(TAG, "⚠️ No hay usuario autenticado")
            _error.value = "No hay usuario autenticado"
            return
        }

        // Si ya tenemos datos y no forzamos refresh, no hacer nada
        if (_currentUser.value != null && !forceRefresh) {
            Log.d(TAG, "✅ Usuario ya en caché, saltando carga")
            return
        }

        Log.d(TAG, "🔄 Cargando usuario desde backend: $uid")
        _isLoading.value = true

        repository.getUserByUid(uid).observeForever { result ->
            when (result) {
                is UserRepository.Result.Loading -> {
                    _isLoading.value = true
                }
                is UserRepository.Result.Success -> {
                    _isLoading.value = false
                    _currentUser.value = result.data
                    _error.value = null
                    Log.d(TAG, "✅ Usuario cargado: ${result.data.nombre}")
                }
                is UserRepository.Result.Error -> {
                    _isLoading.value = false
                    _error.value = result.message
                    Log.e(TAG, "❌ Error cargando usuario: ${result.message}")
                }
            }
        }
    }

    /**
     * 🔄 Recarga el usuario (alias para loadCurrentUser con forceRefresh = true)
     */
    fun refreshUser() {
        loadCurrentUser(forceRefresh = true)
    }

    /**
     * 📝 Obtiene el nombre del usuario con fallbacks
     *
     * ORDEN DE PRIORIDAD:
     * 1. Nombre del perfil (User.nombre)
     * 2. Parte del email antes del @
     * 3. "Usuario_" + primeros 6 caracteres del UID
     *
     * @return Nombre del usuario garantizado (nunca null ni vacío)
     */
    fun getUserName(): String {
        val user = _currentUser.value

        return when {
            // 1. Nombre del perfil
            !user?.nombre.isNullOrBlank() -> user!!.nombre

            // 2. Email
            !user?.email.isNullOrBlank() -> user!!.email.split("@")[0]

            // 3. DisplayName de Firebase
            !auth.currentUser?.displayName.isNullOrBlank() -> auth.currentUser!!.displayName!!

            // 4. Parte del email de Firebase
            !auth.currentUser?.email.isNullOrBlank() -> auth.currentUser!!.email!!.split("@")[0]

            // 5. Último recurso: UID
            else -> "Usuario_${auth.currentUser?.uid?.take(6) ?: "guest"}"
        }
    }

    /**
     * 📷 Obtiene la foto del usuario
     *
     * @return URL de la foto o null si no tiene
     */
    fun getUserPhoto(): String? {
        return _currentUser.value?.foto?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.photoUrl?.toString()
    }

    /**
     * 🆔 Obtiene el UID del usuario actual
     *
     * @return UID de Firebase o null si no está autenticado
     */
    fun getUserUid(): String? {
        return auth.currentUser?.uid
    }

    /**
     * 📧 Obtiene el email del usuario
     *
     * @return Email del usuario
     */
    fun getUserEmail(): String {
        return _currentUser.value?.email
            ?: auth.currentUser?.email
            ?: ""
    }

    /**
     * ✅ Verifica si el perfil está completo
     *
     * @return true si profileCompletion >= 80%
     */
    fun isProfileComplete(): Boolean {
        return (_currentUser.value?.profileCompletion ?: 0) >= 80
    }

    /**
     * 🗑️ Limpia el caché del usuario
     *
     * Útil al hacer logout
     */
    fun clearCache() {
        _currentUser.value = null
        _error.value = null
        Log.d(TAG, "🗑️ Caché limpiado")
    }

    /**
     * 💾 Actualiza el usuario en caché después de editar el perfil
     *
     * @param updatedUser - Usuario actualizado desde el backend
     */
    fun updateCache(updatedUser: User) {
        _currentUser.value = updatedUser
        Log.d(TAG, "💾 Caché actualizado: ${updatedUser.nombre}")
    }

    /**
     * 📊 Obtiene el objeto User completo (puede ser null si aún no se cargó)
     *
     * @return User o null
     */
    fun getCurrentUserData(): User? {
        return _currentUser.value
    }

    /**
     * ⚠️ Limpia los errores
     */
    fun clearError() {
        _error.value = null
    }
}