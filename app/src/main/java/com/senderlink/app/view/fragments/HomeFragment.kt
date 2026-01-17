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
        setupCategoryCards()
        observeViewModel()

        viewModel.loadRecentsFromStorage(requireContext())
        viewModel.loadFeaturedRoutes(reset = true)
    }

    // =====================================
    // 🎯 ACCESOS DIRECTOS (FILTRO REAL)
    // =====================================
    private fun setupCategoryCards() {

        binding.cardTodas.setOnClickListener {
            viewModel.clearDifficultyFilter()
        }

        binding.cardModeradas.setOnClickListener {
            viewModel.applyDifficultyFilter(setOf("MODERADA"))
        }

        binding.cardDificiles.setOnClickListener {
            viewModel.applyDifficultyFilter(setOf("DIFICIL"))
        }

        binding.cardCercanas.setOnClickListener {
            // Esto sí navega al mapa, está bien así
            navigateToMapWithFilter("NEARBY")
        }
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

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int
                ) {
                    val lastVisible =
                        featuredLayoutManager.findLastVisibleItemPosition()
                    if (lastVisible >= featuredLayoutManager.itemCount - 3) {
                        viewModel.loadFeaturedRoutes()
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
        viewModel.featuredRoutes.observe(viewLifecycleOwner) {
            featuredAdapter.submitList(it)
        }

        viewModel.routes.observe(viewLifecycleOwner) {
            routesAdapter.submitList(it)
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
