package com.senderlink.app.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ✅ Utilidad para verificar y corregir sincronización Firebase ↔ MongoDB
 *
 * USO:
 * En MainActivity.onCreate() o donde corresponda:
 *
 * SyncChecker.verifyAndFixIfNeeded()
 */
object SyncChecker {

    private const val TAG = "SyncChecker"
    private val userRepository = UserRepository()

    /**
     * Verifica si Firebase displayName está sincronizado con MongoDB nombre
     * Si no lo está, lo corrige automáticamente
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

        // Obtener nombre de MongoDB
        CoroutineScope(Dispatchers.Main).launch {
            userRepository.getUserByUid(uid).observeForever { result ->
                when (result) {
                    is UserRepository.Result.Success -> {
                        val mongoNombre = result.data.nombre
                        Log.d(TAG, "   MongoDB nombre: '$mongoNombre'")

                        if (mongoNombre != firebaseDisplayName) {
                            Log.w(TAG, "⚠️ DESINCRONIZACIÓN DETECTADA")
                            Log.w(TAG, "   MongoDB:  '$mongoNombre'")
                            Log.w(TAG, "   Firebase: '$firebaseDisplayName'")
                            Log.d(TAG, "🔄 Corrigiendo...")

                            fixSync(mongoNombre)
                        } else {
                            Log.d(TAG, "✅ Sincronización correcta")
                        }
                    }
                    is UserRepository.Result.Error -> {
                        Log.e(TAG, "❌ Error obteniendo usuario de MongoDB: ${result.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Corrige la sincronización actualizando Firebase displayName
     */
    private fun fixSync(nombreCorrecto: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e(TAG, "❌ No hay usuario para actualizar")
            return
        }

        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(nombreCorrecto)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Firebase displayName actualizado a: '$nombreCorrecto'")

                    // Verificar
                    val newDisplayName = FirebaseAuth.getInstance().currentUser?.displayName
                    Log.d(TAG, "   Nuevo displayName: '$newDisplayName'")
                } else {
                    Log.e(TAG, "❌ Error actualizando displayName: ${task.exception?.message}")
                }
            }
    }

    /**
     * Solo verifica sin corregir (para debugging)
     */
    fun checkOnly() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firebaseDisplayName = FirebaseAuth.getInstance().currentUser?.displayName

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📊 Estado de Sincronización")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "UID: ${uid.take(8)}...")
        Log.d(TAG, "Firebase displayName: '$firebaseDisplayName'")

        CoroutineScope(Dispatchers.Main).launch {
            userRepository.getUserByUid(uid).observeForever { result ->
                when (result) {
                    is UserRepository.Result.Success -> {
                        val mongoNombre = result.data.nombre
                        Log.d(TAG, "MongoDB nombre:      '$mongoNombre'")

                        if (mongoNombre == firebaseDisplayName) {
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.d(TAG, "✅ SINCRONIZADO")
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        } else {
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.w(TAG, "⚠️ DESINCRONIZADO")
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

/**
 * EJEMPLO DE USO EN MAINACTIVITY:
 *
 * class MainActivity : AppCompatActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         // Verificar y corregir automáticamente
 *         SyncChecker.verifyAndFixIfNeeded()
 *
 *         // O solo verificar (para debugging)
 *         // SyncChecker.checkOnly()
 *     }
 * }
 */