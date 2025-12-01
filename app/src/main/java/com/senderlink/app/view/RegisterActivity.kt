package com.senderlink.app.view

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.ActivityRegisterBinding


class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        binding.btnRegister.setOnClickListener {
            registerUser()
        }

    }

    private fun registerUser() {

        // Obtener los valores de los EditText
        val email = binding.etEmailRegister.text.toString().trim()
        val password = binding.etPasswordRegister.text.toString().trim()
        val confirmPass = binding.etConfirmPassword.text.toString().trim()

        // Validar que los campos no estén vacíos
        if (email.isEmpty() || password.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        // Validar que el email tenga un formato válido
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email no válido", Toast.LENGTH_SHORT).show()
            return
        }
        // Validar que la contraseña tenga al menos 6 caracteres
        if (password.length < 6) {
            Toast.makeText(
                this,
                "La contraseña debe tener al menos 6 caracteres",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        // Validar que las contraseñas coincidan
        if (password != confirmPass) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            return

        }
        // Crear el usuario con Firebase Auth
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else {
                Toast.makeText(
                    this,
                    "Error en el registro: ${task.exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()

            }
        }
    }
}
