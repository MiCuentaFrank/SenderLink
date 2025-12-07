package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.senderlink.app.databinding.FragmentHomeBinding
import com.senderlink.app.view.adapters.RouteAdapter
import com.senderlink.app.viewmodel.HomeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels()

    private lateinit var routeAdapter: RouteAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Configuramos el RecyclerView
        routeAdapter = RouteAdapter()
        binding.rvRutas.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = routeAdapter
        }

        // 2) Observamos las rutas que vienen del ViewModel
        homeViewModel.routes.observe(viewLifecycleOwner) { rutas ->
            routeAdapter.submitList(rutas)
        }

        // 3) (Opcional) Observamos errores
        homeViewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                println("ERROR HOME: $errorMsg")
            }
        }

        // 4) Cargar las rutas desde el backend
        homeViewModel.loadRoutes()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
