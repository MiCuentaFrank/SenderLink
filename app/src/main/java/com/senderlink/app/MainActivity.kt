package com.senderlink.app.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.ActivityMainBinding
import com.senderlink.app.utils.SyncChecker
import com.senderlink.app.utils.UserManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // ✅ evita volver atrás a MainActivity
        }

        initializeUserManager()
    }

    private fun initializeUserManager() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            Log.d("MainActivity", "⚠️ No hay usuario autenticado")
            return
        }

        Log.d("MainActivity", "🔄 Usuario autenticado: ${currentUser.email}")

        // Forzar refresco del token antes de cualquier llamada a la API.
        // getIdToken(false) puede devolver un token expirado en arranque en frío;
        // con true el token se cachea y el interceptor de Retrofit lo reutiliza.
        currentUser.getIdToken(true)
            .addOnSuccessListener {
                UserManager.getInstance().loadCurrentUser()
                SyncChecker.verifyAndFixIfNeeded()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "❌ Error refrescando token Firebase: ${e.message}")
            }
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        UserManager.getInstance().clearCache()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
