package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentSaveRouteBinding
import com.senderlink.app.utils.DistanceFormatter
import com.senderlink.app.viewmodel.TrackingViewModel
import kotlinx.coroutines.launch

class SaveRouteFragment : Fragment() {

    private var _binding: FragmentSaveRouteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackingViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSaveRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mostrar preview de stats
        binding.tvDistancePreview.text = DistanceFormatter.format(viewModel.totalDistanceM.value / 1000.0)
        binding.tvDurationPreview.text = formatTime(viewModel.elapsedSeconds.value)
        binding.tvPointsPreview.text   = "${viewModel.trackPoints.value.size} pts GPS"

        // Spinner de dificultad
        val difficulties = listOf("FACIL", "MODERADA", "DIFICIL")
        binding.spinnerDifficulty.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, difficulties
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.btnSave.setOnClickListener { saveRoute() }

        // Observar resultado
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createRouteResult.collect { state ->
                when (state) {
                    is TrackingViewModel.CreateRouteState.Loading -> {
                        binding.btnSave.isEnabled    = false
                        binding.progressSave.visibility = View.VISIBLE
                    }
                    is TrackingViewModel.CreateRouteState.Success -> {
                        binding.progressSave.visibility = View.GONE
                        Toast.makeText(requireContext(), "¡Ruta guardada! +XP", Toast.LENGTH_LONG).show()
                        viewModel.resetCreateResult()
                        findNavController().navigate(R.id.action_saveRouteFragment_to_nav_perfil)
                    }
                    is TrackingViewModel.CreateRouteState.Error -> {
                        binding.progressSave.visibility = View.GONE
                        binding.btnSave.isEnabled    = true
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetCreateResult()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun saveRoute() {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.etName.error = "El nombre es obligatorio"
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val difficulty  = binding.spinnerDifficulty.selectedItem.toString()
        val description = binding.etDescription.text?.toString()?.trim() ?: ""

        viewModel.createRoute(uid, name, description, difficulty)
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%dh %02dm %02ds".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
