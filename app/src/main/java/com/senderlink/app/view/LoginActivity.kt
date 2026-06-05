package com.senderlink.app.view

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ajustar padding superior del logo para no solaparse con la barra de estado
        val logoBasePaddingTop = binding.logoGroup.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.logoGroup) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.updatePadding(top = logoBasePaddingTop + statusBarHeight)
            insets
        }

        // Ajustar padding inferior del card para no solaparse con la barra de navegación
        val cardBasePaddingBottom = binding.loginFormCard.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.loginFormCard) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.updatePadding(bottom = cardBasePaddingBottom + navBarHeight)
            insets
        }

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
                Toast.makeText(this, "Email o contraseña incorrectos", Toast.LENGTH_SHORT).show()
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

