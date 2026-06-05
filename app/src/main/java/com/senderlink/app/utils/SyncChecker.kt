package com.senderlink.app.utils

import android.util.Log
import androidx.lifecycle.Observer
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.User

/**
 * Utilidad para verificar y corregir sincronización Firebase ↔ MongoDB.
 * Reutiliza los datos ya cargados por UserManager para evitar una llamada
 * extra a la API.
 *
 * USO: Llamar después de UserManager.loadCurrentUser() en MainActivity.
 */
object SyncChecker {

    private const val TAG = "SyncChecker"

    /**
     * Verifica si Firebase displayName está sincronizado con MongoDB nombre.
     * Si no lo está, lo corrige automáticamente.
     * Reutiliza los datos de UserManager en vez de hacer una llamada propia.
     */
    fun verifyAndFixIfNeeded() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "⚠️ No hay usuario autenticado")
            return
        }

        val firebaseDisplayName = FirebaseAuth.getInstance().currentUser?.displayName
        Log.d(TAG, "🔍 Verificando sincronización...")
        Log.d(TAG, "   Firebase displayName: '$firebaseDisplayName'")

        val userManager = UserManager.getInstance()
        val cachedUser = userManager.getCurrentUserData()

        if (cachedUser != null) {
            doCheck(cachedUser.nombre, firebaseDisplayName)
        } else {
            // Esperar a que UserManager cargue el usuario
            val observer = object : Observer<User?> {
                override fun onChanged(user: User?) {
                    if (user != null) {
                        userManager.currentUser.removeObserver(this)
                        doCheck(user.nombre, firebaseDisplayName)
                    }
                }
            }
            userManager.currentUser.observeForever(observer)
        }
    }

    private fun doCheck(mongoNombre: String?, firebaseDisplayName: String?) {
        Log.d(TAG, "   MongoDB nombre: '$mongoNombre'")
        if (mongoNombre != firebaseDisplayName) {
            Log.w(TAG, "⚠️ DESINCRONIZACIÓN DETECTADA")
            Log.w(TAG, "   MongoDB:  '$mongoNombre'")
            Log.w(TAG, "   Firebase: '$firebaseDisplayName'")
            Log.d(TAG, "🔄 Corrigiendo...")
            fixSync(mongoNombre ?: "")
        } else {
            Log.d(TAG, "✅ Sincronización correcta")
        }
    }

    /**
     * Solo verifica sin corregir (para debugging).
     */
    fun checkOnly() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firebaseDisplayName = FirebaseAuth.getInstance().currentUser?.displayName

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📊 Estado de Sincronización")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "UID: ${uid.take(8)}...")
        Log.d(TAG, "Firebase displayName: '$firebaseDisplayName'")

        val userManager = UserManager.getInstance()
        val cachedUser = userManager.getCurrentUserData()

        if (cachedUser != null) {
            logSyncStatus(cachedUser.nombre, firebaseDisplayName)
        } else {
            val observer = object : Observer<User?> {
                override fun onChanged(user: User?) {
                    if (user != null) {
                        userManager.currentUser.removeObserver(this)
                        logSyncStatus(user.nombre, firebaseDisplayName)
                    }
                }
            }
            userManager.currentUser.observeForever(observer)
        }
    }

    private fun logSyncStatus(mongoNombre: String?, firebaseDisplayName: String?) {
        Log.d(TAG, "MongoDB nombre:      '$mongoNombre'")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        if (mongoNombre == firebaseDisplayName) {
            Log.d(TAG, "✅ SINCRONIZADO")
        } else {
            Log.w(TAG, "⚠️ DESINCRONIZADO")
        }
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Corrige la sincronización actualizando Firebase displayName.
     */
    private fun fixSync(nombreCorrecto: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.e(TAG, "❌ No hay usuario para actualizar")
            return
        }

        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(nombreCorrecto)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val newDisplayName = FirebaseAuth.getInstance().currentUser?.displayName
                    Log.d(TAG, "✅ Firebase displayName actualizado a: '$newDisplayName'")
                } else {
                    Log.e(TAG, "❌ Error actualizando displayName: ${task.exception?.message}")
                }
            }
    }
}
