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
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            Log.d("MainActivity", "🔄 Usuario autenticado: ${currentUser.email}")

            // ✅ Solo si hay usuario
            SyncChecker.verifyAndFixIfNeeded()

            // ✅ Cargar datos del usuario
            UserManager.getInstance().loadCurrentUser()
        } else {
            Log.d("MainActivity", "⚠️ No hay usuario autenticado")
        }
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        UserManager.getInstance().clearCache()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
