package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentEditProfileBinding
import com.senderlink.app.viewmodel.EditProfileViewModel

/**
 * Fragment para editar el perfil del usuario
 * VERSIÓN CON CHIPS - Más intuitivo que campos de texto
 *
 * MEJORAS DE UX:
 * - Chips para nivel (single selection - solo uno seleccionado)
 * - Chips para tipos (multiple selection - varios seleccionados)
 * - Validación visual en tiempo real
 */
class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels()

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

        setupToolbar()
        setupObservers()
        setupListeners()

        // Cargar datos del usuario al entrar
        viewModel.loadUserData()
    }

    /**
     * Configura el toolbar con navegación hacia atrás
     */
    private fun setupToolbar() {
        binding.toolbarEditProfile.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    /**
     * Observa los cambios del ViewModel
     */
    private fun setupObservers() {

        // 1. OBSERVAR DATOS DEL USUARIO
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            user?.let {
                // DATOS BÁSICOS
                binding.etNombre.setText(it.nombre)
                binding.etBio.setText(it.bio)
                binding.etComunidad.setText(it.comunidad)
                binding.etProvincia.setText(it.provincia)

                // PREFERENCIAS
                it.preferencias?.let { pref ->
                    // Seleccionar chip de NIVEL
                    seleccionarNivelChip(pref.nivel)

                    // Seleccionar chips de TIPOS
                    seleccionarTiposChips(pref.tipos)

                    // Distancia
                    if (pref.distanciaKm > 0) {
                        binding.etDistancia.setText(pref.distanciaKm.toString())
                    }
                }
            }
        }

        // 2. OBSERVAR ESTADO DE CARGA
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnGuardar.isEnabled = !isLoading
            binding.btnGuardar.text = if (isLoading) "Guardando..." else "Guardar cambios"
        }

        // 3. OBSERVAR ERRORES
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        // 4. OBSERVAR EVENTO DE GUARDADO EXITOSO
        viewModel.savedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success) {
                    Toast.makeText(
                        requireContext(),
                        "✅ Perfil actualizado correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Volver a la pantalla de perfil
                    findNavController().navigateUp()
                }
            }
        }
    }

    /**
     * Selecciona el chip de nivel correspondiente
     */
    private fun seleccionarNivelChip(nivel: String) {
        when (nivel.uppercase()) {
            "BEGINNER", "PRINCIPIANTE" -> binding.chipBeginner.isChecked = true
            "INTERMEDIATE", "INTERMEDIO" -> binding.chipIntermediate.isChecked = true
            "ADVANCED", "AVANZADO" -> binding.chipAdvanced.isChecked = true
            "EXPERT", "EXPERTO" -> binding.chipExpert.isChecked = true
        }
    }

    /**
     * Selecciona los chips de tipos correspondientes
     */
    private fun seleccionarTiposChips(tipos: List<String>) {
        tipos.forEach { tipo ->
            when (tipo.uppercase()) {
                "MONTAÑA", "MONTANA", "MOUNTAIN" -> binding.chipMontana.isChecked = true
                "BOSQUE", "FOREST" -> binding.chipBosque.isChecked = true
                "COSTA", "BEACH", "PLAYA" -> binding.chipCosta.isChecked = true
                "URBANO", "URBAN", "CITY" -> binding.chipUrbano.isChecked = true
                "RURAL" -> binding.chipRural.isChecked = true
            }
        }
    }

    /**
     * Configura los listeners de los botones
     */
    private fun setupListeners() {
        binding.btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }

    /**
     * Recoge los datos del formulario y los envía al ViewModel
     *
     * MEJORA: Ahora lee chips en lugar de campos de texto
     */
    private fun guardarCambios() {
        // DATOS BÁSICOS
        val nombre = binding.etNombre.text.toString()
        val bio = binding.etBio.text.toString()
        val comunidad = binding.etComunidad.text.toString()
        val provincia = binding.etProvincia.text.toString()

        // NIVEL - Obtener el chip seleccionado
        val nivel = when (binding.chipGroupNivel.checkedChipId) {
            R.id.chipBeginner -> "BEGINNER"
            R.id.chipIntermediate -> "INTERMEDIATE"
            R.id.chipAdvanced -> "ADVANCED"
            R.id.chipExpert -> "EXPERT"
            else -> ""
        }

        // TIPOS - Obtener todos los chips seleccionados
        val tiposSeleccionados = mutableListOf<String>()
        if (binding.chipMontana.isChecked) tiposSeleccionados.add("MONTAÑA")
        if (binding.chipBosque.isChecked) tiposSeleccionados.add("BOSQUE")
        if (binding.chipCosta.isChecked) tiposSeleccionados.add("COSTA")
        if (binding.chipUrbano.isChecked) tiposSeleccionados.add("URBANO")
        if (binding.chipRural.isChecked) tiposSeleccionados.add("RURAL")

        // Convertir lista a CSV para el ViewModel
        val tiposCsv = tiposSeleccionados.joinToString(", ")

        // DISTANCIA
        val distancia = binding.etDistancia.text.toString()

        // Validación básica
        if (nombre.isBlank()) {
            Toast.makeText(
                requireContext(),
                "⚠️ El nombre no puede estar vacío",
                Toast.LENGTH_SHORT
            ).show()
            binding.etNombre.requestFocus()
            return
        }

        // Enviar al ViewModel
        viewModel.saveProfile(
            nombre = nombre,
            bio = bio,
            comunidad = comunidad,
            provincia = provincia,
            nivel = nivel,
            tiposCsv = tiposCsv,
            distanciaText = distancia
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}