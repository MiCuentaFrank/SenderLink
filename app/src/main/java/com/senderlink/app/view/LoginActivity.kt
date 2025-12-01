package com.senderlink.app.view

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)


        auth = FirebaseAuth.getInstance()

        binding.btnLogin.setOnClickListener {
            loginUser()
        }
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))

        }
    }
    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        // 1) Validación básica
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return

        }
        // 2) Llamada a Firebase Auth
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task->
            if (task.isSuccessful) {
                Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainScreenActivity::class.java))
                finish()
            }else{
                Toast.makeText(this, "Error en el inicio de sesión: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }

    }
    override fun onStart() {
        super.onStart()

        if (auth.currentUser != null) {
            // Usuario ya logueado → saltar el login
            startActivity(Intent(this, MainScreenActivity::class.java))
            finish()
        }
    }


}

