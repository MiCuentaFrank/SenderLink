package com.senderlink.app.view.fragments

import android.os.Bundle
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
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentHomeBinding
import com.senderlink.app.view.adapters.FeaturedRouteAdapter
import com.senderlink.app.view.adapters.RouteAdapter
import com.senderlink.app.viewmodel.HomeViewModel

/**
 * 🏠 HomeFragment - OPTIMIZADO
 *
 * MEJORAS:
 * - ⚡ Carga secuencial (destacadas primero)
 * - 🎨 Skeleton loading mientras carga
 * - 🔄 Pull-to-refresh
 * - 📄 Paginación infinita (destacadas)
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var featuredAdapter: FeaturedRouteAdapter
    private lateinit var routesAdapter: RouteAdapter

    private var isSkeletonVisible = false

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
        setupSwipeRefresh()
        observeViewModel()

        // ⚡ CARGA INICIAL (solo si no hay datos ya cargados)
        if (viewModel.featuredRoutes.value.isNullOrEmpty()) {
            showSkeletonLoading()
            viewModel.loadAllData(requireContext())
        } else {
            hideSkeletonLoading()
        }
    }

    // =====================================
    // 🗂️ RECYCLERS
    // =====================================
    private fun setupRecyclerView() {

        // ✅ DESTACADAS (horizontal scroll)
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

            // 📄 Paginación infinita - Cargar más al llegar al final
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val lastVisible = featuredLayoutManager.findLastVisibleItemPosition()
                    val totalItems = featuredLayoutManager.itemCount

                    if (totalItems > 0 && lastVisible >= totalItems - 3) {
                        viewModel.loadMoreFeaturedRoutes()
                    }
                }
            })
        }

        // ✅ RECIENTES (grid 2 columnas)
        routesAdapter = RouteAdapter { route ->
            viewModel.markRouteAsRecent(requireContext(), route)
            navigateToDetail(route.id)
        }

        binding.rvRoutes.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = routesAdapter

            // 🔥 CLAVE para RV dentro de NestedScrollView con wrap_content
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

    }

    // =====================================
    // 🔄 PULL TO REFRESH
    // =====================================
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            showSkeletonLoading()
            viewModel.refresh(requireContext())
        }
    }

    // =====================================
    // 👀 OBSERVERS
    // =====================================
    private fun observeViewModel() {

        viewModel.featuredRoutes.observe(viewLifecycleOwner) { routes ->
            featuredAdapter.submitList(routes)
            binding.rvFeaturedRoutes.visibility = if (routes.isNullOrEmpty()) View.GONE else View.VISIBLE
            binding.rvFeaturedRoutes.requestLayout()
            hideSkeletonLoading()
        }

        viewModel.routes.observe(viewLifecycleOwner) { routes ->
            routesAdapter.submitList(routes)
            binding.rvRoutes.post { binding.rvRoutes.requestLayout() }
        }


        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading

            // Si termina de cargar y ya hay algo, fuera shimmer
            if (!isLoading) {
                if (!viewModel.featuredRoutes.value.isNullOrEmpty() || !viewModel.routes.value.isNullOrEmpty()) {
                    hideSkeletonLoading()
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                hideSkeletonLoading()
            }
        }
    }

    // =====================================
    // 🎨 SKELETON LOADING (SHIMMER OVERLAY)
    // =====================================
    private fun showSkeletonLoading() {
        isSkeletonVisible = true
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.shimmerLayout.startShimmer()
        binding.contentLayout.visibility = View.GONE
    }

    private fun hideSkeletonLoading() {
        isSkeletonVisible = false
        binding.shimmerLayout.stopShimmer()
        binding.shimmerLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
    }



    // =====================================
    // 🧭 NAVEGACIÓN
    // =====================================
    private fun navigateToDetail(routeId: String?) {
        if (routeId.isNullOrBlank()) return

        try {
            val bundle = Bundle().apply { putString("routeId", routeId) }
            findNavController().navigate(
                R.id.action_global_routeDetailFragment,
                bundle
            )
        } catch (e: Exception) {
            try {
                val action = HomeFragmentDirections
                    .actionHomeFragmentToRouteDetailFragment(routeId)
                findNavController().navigate(action)
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Error abriendo ruta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =====================================
    // 🔄 LIFECYCLE
    // =====================================
    override fun onResume() {
        super.onResume()
        if (isSkeletonVisible) binding.shimmerLayout.startShimmer()
    }

    override fun onPause() {
        super.onPause()
        if (isSkeletonVisible) binding.shimmerLayout.stopShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
