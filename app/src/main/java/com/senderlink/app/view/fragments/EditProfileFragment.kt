package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentEditProfileBinding
import com.senderlink.app.network.PreferenciasRequest
import com.senderlink.app.utils.ProvinciasUtils
import com.senderlink.app.viewmodel.EditProfileViewModel

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels()
    private val TAG = "EditProfileFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeViewModel()
        viewModel.loadCurrentUser()
    }

    private fun setupViews() {
        binding.btnGuardar.setOnClickListener {
            saveProfile()
        }

        binding.toolbarEditProfile.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Limpiar error del nombre en tiempo real cuando el usuario escribe
        binding.etNombre.addTextChangedListener {
            if (!it.isNullOrBlank()) {
                binding.tilNombre.error = null
            }
        }

        // Autocompletado de provincias españolas
        val provinciaAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            ProvinciasUtils.provincias
        )
        binding.etProvincia.setAdapter(provinciaAdapter)

        // Auto-rellenar comunidad al seleccionar una provincia del desplegable
        binding.etProvincia.setOnItemClickListener { _, _, _, _ ->
            val provinciaSeleccionada = binding.etProvincia.text.toString()
            val comunidad = ProvinciasUtils.getComunidad(provinciaSeleccionada)
            if (comunidad != null && binding.etComunidad.text.isNullOrBlank()) {
                binding.etComunidad.setText(comunidad)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.etNombre.setText(it.nombre)
                binding.etBio.setText(it.bio)
                binding.etComunidad.setText(it.comunidad)
                binding.etProvincia.setText(it.provincia)

                // Populate preferences chips from saved user data
                val prefs = it.preferencias
                if (prefs != null) {
                    // Nivel (single-select)
                    val nivelChipId = when (prefs.nivel) {
                        "INTERMEDIATE" -> R.id.chipIntermediate
                        "ADVANCED"     -> R.id.chipAdvanced
                        "EXPERT"       -> R.id.chipExpert
                        else           -> R.id.chipBeginner
                    }
                    binding.chipGroupNivel.check(nivelChipId)

                    // Tipos (multi-select)
                    binding.chipMontana.isChecked = prefs.tipos.contains("MONTAÑA")
                    binding.chipBosque.isChecked  = prefs.tipos.contains("BOSQUE")
                    binding.chipCosta.isChecked   = prefs.tipos.contains("COSTA")
                    binding.chipUrbano.isChecked  = prefs.tipos.contains("URBANO")
                    binding.chipRural.isChecked   = prefs.tipos.contains("RURAL")

                    // Distancia
                    if (prefs.distanciaKm > 0) {
                        val kmText = if (prefs.distanciaKm % 1.0 == 0.0)
                            prefs.distanciaKm.toInt().toString()
                        else
                            prefs.distanciaKm.toString()
                        binding.etDistancia.setText(kmText)
                    }
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnGuardar.isEnabled = !isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.updateSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun saveProfile() {
        val nombre = binding.etNombre.text.toString().trim()
        val bio = binding.etBio.text.toString().trim()
        val comunidad = binding.etComunidad.text.toString().trim()
        val provincia = binding.etProvincia.text.toString().trim()

        if (nombre.isEmpty()) {
            binding.tilNombre.error = "El nombre es obligatorio"
            binding.etNombre.requestFocus()
            return
        }

        // Read nivel chip
        val nivel = when (binding.chipGroupNivel.checkedChipId) {
            R.id.chipIntermediate -> "INTERMEDIATE"
            R.id.chipAdvanced     -> "ADVANCED"
            R.id.chipExpert       -> "EXPERT"
            else                  -> "BEGINNER"
        }

        // Read tipos chips (multi-select)
        val tipos = mutableListOf<String>()
        if (binding.chipMontana.isChecked) tipos.add("MONTAÑA")
        if (binding.chipBosque.isChecked)  tipos.add("BOSQUE")
        if (binding.chipCosta.isChecked)   tipos.add("COSTA")
        if (binding.chipUrbano.isChecked)  tipos.add("URBANO")
        if (binding.chipRural.isChecked)   tipos.add("RURAL")

        // Read distancia
        val distanciaKm = binding.etDistancia.text.toString().toDoubleOrNull() ?: 0.0

        val preferencias = PreferenciasRequest(
            nivel = nivel,
            tipos = tipos,
            distanciaKm = distanciaKm
        )

        Log.d(TAG, "💾 Guardando perfil: nombre='$nombre' nivel=$nivel tipos=$tipos km=$distanciaKm")

        viewModel.updateProfile(
            nombre = nombre,
            bio = bio,
            comunidad = comunidad,
            provincia = provincia,
            preferencias = preferencias,
            onFirebaseSyncNeeded = { nombreParaFirebase ->
                syncFirebaseDisplayName(nombreParaFirebase)
            }
        )
    }

    private fun syncFirebaseDisplayName(nombre: String) {
        Log.d(TAG, "🔄 Sincronizando Firebase displayName='$nombre'")

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e(TAG, "❌ No hay usuario autenticado en Firebase")
            return
        }

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(nombre)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Firebase displayName actualizado correctamente")

                    val currentDisplayName = FirebaseAuth.getInstance().currentUser?.displayName
                    Log.d(TAG, "   displayName actual en Firebase: '$currentDisplayName'")
                } else {
                    Log.e(TAG, "❌ Error actualizando Firebase displayName: ${task.exception?.message}")
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
