package com.senderlink.app.view.fragments


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.senderlink.app.databinding.FragmentComunidadBinding

class ComunidadFragment : Fragment() {

    // Binding real (puede ser null)
    private var _binding: FragmentComunidadBinding? = null

    // Getter seguro
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComunidadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Aquí controlas las vistas del fragment
        binding.tvComunidadTitle.text = "Comunidad SenderLink"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

