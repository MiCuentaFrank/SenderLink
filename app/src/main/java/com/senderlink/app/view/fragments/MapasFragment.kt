package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.senderlink.app.databinding.FragmentMapasBinding

class MapasFragment : Fragment() {

    private var _binding: FragmentMapasBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Aquí manejas vistas o lógica del fragment
        binding.tvMapasTitle.text = "Explora Mapas 🔍"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
