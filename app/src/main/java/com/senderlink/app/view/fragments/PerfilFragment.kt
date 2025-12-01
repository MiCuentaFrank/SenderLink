package com.senderlink.app.view.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.FragmentPerfilBinding
import com.senderlink.app.view.LoginActivity

class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Aquí luego mostraremos el email del usuario con Firebase
        binding.tvPerfilTitle.text = "Mi Perfil"

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
