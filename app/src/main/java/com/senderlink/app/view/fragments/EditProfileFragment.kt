package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.senderlink.app.databinding.FragmentEditProfileBinding
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
    }

    private fun observeViewModel() {
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.etNombre.setText(it.nombre)
                binding.etBio.setText(it.bio)
                binding.etComunidad.setText(it.comunidad)
                binding.etProvincia.setText(it.provincia)
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
            Toast.makeText(requireContext(), "El nombre es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "💾 Guardando perfil: nombre='$nombre'")

        // ✅ Actualizar perfil en MongoDB Y Firebase
        viewModel.updateProfile(
            nombre = nombre,
            bio = bio,
            comunidad = comunidad,
            provincia = provincia,
            onFirebaseSyncNeeded = { nombreParaFirebase ->
                // ✅ Callback para sincronizar Firebase displayName
                syncFirebaseDisplayName(nombreParaFirebase)
            }
        )
    }

    // ✅ NUEVA FUNCIÓN: Sincronizar displayName en Firebase Auth
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

                    // ✅ Verificar que se guardó
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