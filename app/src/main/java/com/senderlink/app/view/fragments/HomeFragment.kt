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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.senderlink.app.databinding.FragmentHomeBinding
import com.senderlink.app.view.adapters.FeaturedRouteAdapter
import com.senderlink.app.view.adapters.RouteAdapter
import com.senderlink.app.viewmodel.HomeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var featuredAdapter: FeaturedRouteAdapter
    private lateinit var routesAdapter: RouteAdapter

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

        setupRecyclerView()
        observeViewModel()
        viewModel.loadRecentsFromStorage(requireContext())


        // Cargar datos iniciales
        viewModel.loadFeaturedRoutes(reset = true)

    }

    private fun setupRecyclerView() {
        // Adapter de rutas destacadas con navegación
        featuredAdapter = FeaturedRouteAdapter { route ->
            viewModel.markRouteAsRecent(requireContext(), route)
            navigateToDetail(route.id)
        }



        // Layout manager para el carrusel horizontal
        val featuredLayoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

        binding.rvFeaturedRoutes.apply {
            layoutManager = featuredLayoutManager
            adapter = featuredAdapter
            setHasFixedSize(true)

            // Detectar cuando llega al final para cargar más
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val lastVisiblePosition = featuredLayoutManager.findLastVisibleItemPosition()
                    val totalItems = featuredLayoutManager.itemCount

                    // Cargar más cuando esté a 3 items del final
                    if (lastVisiblePosition >= totalItems - 3) {
                        Log.d("HOME_FRAGMENT", "Cargando más rutas destacadas...")
                        viewModel.loadFeaturedRoutes()
                    }
                }
            })
        }

        // Adapter de rutas recientes con navegación
        routesAdapter = RouteAdapter { route ->
            viewModel.markRouteAsRecent(requireContext(), route)
            navigateToDetail(route.id)
        }



        binding.rvRoutes.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = routesAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        // Rutas destacadas
        viewModel.featuredRoutes.observe(viewLifecycleOwner) { routes ->
            Log.d("HOME_FRAGMENT", "Rutas destacadas actualizadas: ${routes.size}")
            featuredAdapter.submitList(routes)
        }

        // Rutas recientes
        viewModel.routes.observe(viewLifecycleOwner) { routes ->
            Log.d("HOME_FRAGMENT", "Rutas recientes recibidas: ${routes.size}")
            routesAdapter.submitList(routes)
        }
    }


    private fun navigateToDetail(routeId: String?) {
        try {
            if (routeId.isNullOrBlank()) {
                Log.e("HOME_FRAGMENT", "Error: routeId es null o vacío")
                Toast.makeText(requireContext(), "Error: ID de ruta inválido", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d("HOME_FRAGMENT", "Navegando a ruta con ID: $routeId")
            val action = HomeFragmentDirections.actionHomeFragmentToRouteDetailFragment(routeId)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e("HOME_FRAGMENT", "Error al navegar: ${e.message}", e)
            Toast.makeText(requireContext(), "Error al abrir la ruta", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}