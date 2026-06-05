package com.senderlink.app.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.ActivityRegisterBinding
import com.senderlink.app.model.User
import com.senderlink.app.repository.UserRepository

/**
 * Activity para registrar nuevos usuarios
 * 1. Registra al usuario en Firebase Auth
 * 2. Crea el registro del usuario en MongoDB (backend)
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val userRepository = UserRepository()

    private val TAG = "RegisterActivity"

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

        // Validar contraseña: mínimo 8 caracteres, al menos una mayúscula, una minúscula y un número
        if (password.length < 8) {
            Toast.makeText(
                this,
                "La contraseña debe tener al menos 8 caracteres",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!password.any { it.isUpperCase() } || !password.any { it.isLowerCase() } || !password.any { it.isDigit() }) {
            Toast.makeText(
                this,
                "La contraseña debe incluir mayúsculas, minúsculas y números",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Validar que las contraseñas coincidan
        if (password != confirmPass) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            return
        }

        // Deshabilitar el botón mientras se registra
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Registrando..."

        // PASO 1: Crear el usuario con Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Usuario registrado en Firebase correctamente")

                    // Obtener el UID del usuario creado
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        // PASO 2: Crear el usuario en MongoDB
                        createUserInBackend(firebaseUser.uid, email)
                    } else {
                        binding.btnRegister.isEnabled = true
                        binding.btnRegister.text = "Registrarse"
                        Toast.makeText(
                            this,
                            "Error: No se pudo obtener el usuario",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Registrarse"
                    val errorMsg = task.exception?.message ?: "Error desconocido"
                    Log.e(TAG, "Error en Firebase Auth: $errorMsg", task.exception)
                    val userMsg = when {
                        errorMsg.contains("email address is already in use", ignoreCase = true) ->
                            "Ese email ya está registrado. Prueba a iniciar sesión."
                        errorMsg.contains("badly formatted", ignoreCase = true) ->
                            "El email no tiene un formato válido."
                        errorMsg.contains("network", ignoreCase = true) ||
                        errorMsg.contains("unable to resolve", ignoreCase = true) ->
                            "Sin conexión. Comprueba tu internet."
                        else -> "Error: $errorMsg"
                    }
                    Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Crea el usuario en la base de datos de MongoDB (backend)
     *
     * @param uid - ID único del usuario de Firebase
     * @param email - Email del usuario
     */
    private fun createUserInBackend(uid: String, email: String) {
        Log.d(TAG, "Creando usuario en MongoDB")

        // Crear el objeto User con los datos mínimos
        val newUser = User(
            uid = uid,
            email = email,
            nombre = "",
            foto = ""
        )

        // Llamar al repository para crear el usuario
        userRepository.createUser(newUser).observe(this) { result ->
            when (result) {
                is UserRepository.Result.Loading -> {
                    Log.d(TAG, "Creando usuario en backend...")
                }
                is UserRepository.Result.Success -> {
                    Log.d(TAG, "Usuario creado en MongoDB correctamente")

                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Registrarse"

                    Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()

                    // Navegar a la pantalla de login
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                is UserRepository.Result.Error -> {
                    Log.e(TAG, "Error al crear usuario en MongoDB")

                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Registrarse"

                    // Mostrar mensaje de advertencia pero permitir continuar
                    Toast.makeText(
                        this,
                        "Usuario creado en Firebase, pero hubo un error al crear el perfil. Intenta iniciar sesión.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Ir al login de todas formas
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }
}