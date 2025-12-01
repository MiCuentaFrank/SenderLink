package com.senderlink.app.view.fragments
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.senderlink.app.databinding.FragmentHomeBinding


class HomeFragment : Fragment(){
    // Variable privada que guarda el binding (puede ser null)
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!! // Versión segura del binding (no permite null)
    // Se llama cuando el Fragment tiene que "dibujar" su vista
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //  Inflamos el layout del Fragment
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    //Se llama cuando la vista del fragment se destruye
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



}