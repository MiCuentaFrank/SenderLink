package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.senderlink.app.databinding.FragmentSocialBinding
import com.senderlink.app.view.adapters.SocialPagerAdapter
import com.senderlink.app.viewmodel.RutasGrupalesViewModel

class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAB_COMUNIDAD = 0
        const val TAB_RUTAS_GRUPALES = 1
        private const val TAG = "SocialFragment"
    }

    private val rutasGrupalesViewModel: RutasGrupalesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Antes era binding.appBarSocial (ya no existe)
        // ✅ Ahora aplicamos insets al root para respetar status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = top)
            insets
        }

        binding.viewPager.adapter = SocialPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Comunidad" else "Rutas Grupales"
        }.attach()

        val initialTab = arguments?.getInt("initialTab", TAB_COMUNIDAD) ?: TAB_COMUNIDAD
        val routeId = arguments?.getString("routeId")

        Log.d(TAG, "Args -> initialTab=$initialTab routeId=$routeId")

        val navPreferredTab = rutasGrupalesViewModel.navPreferredTab.value

        if (navPreferredTab == null) {
            if (routeId != null) {
                rutasGrupalesViewModel.setRouteFilter(routeId)
                rutasGrupalesViewModel.setShowAllDisponibles(false)
                Log.d(TAG, "✅ Con routeId manual → filtrar por ruta")
            } else {
                rutasGrupalesViewModel.openFromNavComunidad()
                Log.d(TAG, "✅ Desde nav_comunidad → mostrar TODAS")
            }
        } else {
            Log.d(TAG, "✅ Desde RouteDetail detectado, no tocar config")
        }

        binding.viewPager.post {
            binding.viewPager.setCurrentItem(initialTab, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
