package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

/**
 * 🏠 HomeFragment - OPTIMIZADO CON PAGINACIÓN
 *
 * MEJORAS:
 * - ⚡ Carga paralela con loadAllData()
 * - 💾 Usa caché de destacadas
 * - 🔄 Paginación infinita en destacadas
 */
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

        // ⚡ CARGA PARALELA - Una sola llamada carga TODO
        viewModel.loadAllData(requireContext())
    }

    // =====================================
    // 🗂️ RECYCLERS
    // =====================================
    private fun setupRecyclerView() {

        featuredAdapter = FeaturedRouteAdapter { route ->
            viewModel.markRouteAsRecent(requireContext(), route)
            navigateToDetail(route.id)
        }

        val featuredLayoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

        binding.rvFeaturedRoutes.apply {
            layoutManager = featuredLayoutManager
            adapter = featuredAdapter
            setHasFixedSize(true)

            // 🔄 SCROLL LISTENER - Cargar más al llegar al final
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val lastVisible = featuredLayoutManager.findLastVisibleItemPosition()
                    val totalItems = featuredLayoutManager.itemCount

                    // Cuando faltan 3 items para el final, cargar más
                    if (lastVisible >= totalItems - 3) {
                        viewModel.loadMoreFeaturedRoutes()
                    }
                }
            })
        }

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
        // Observar destacadas
        viewModel.featuredRoutes.observe(viewLifecycleOwner) {
            featuredAdapter.submitList(it)
        }

        // Observar recientes
        viewModel.routes.observe(viewLifecycleOwner) {
            routesAdapter.submitList(it)
        }

        // Observar loading (opcional - puedes mostrar un ProgressBar)
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Aquí podrías mostrar/ocultar un ProgressBar
            // binding.progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observar errores (opcional - puedes mostrar un Toast)
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                // Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =====================================
    // 🧭 NAVEGACIÓN
    // =====================================
    private fun navigateToMapWithFilter(filterType: String) {
        val action =
            HomeFragmentDirections.actionHomeFragmentToMapasFragment(filterType)
        findNavController().navigate(action)
    }

    private fun navigateToDetail(routeId: String?) {
        if (routeId.isNullOrBlank()) return
        val action =
            HomeFragmentDirections.actionHomeFragmentToRouteDetailFragment(routeId)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}