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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.senderlink.app.databinding.FragmentRutasGrupalesBinding
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.view.adapters.EventoAdapter
import com.senderlink.app.viewmodel.RutasGrupalesViewModel

class RutasGrupalesFragment : Fragment() {

    private var _binding: FragmentRutasGrupalesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RutasGrupalesViewModel by viewModels()
    private lateinit var eventoAdapter: EventoAdapter

    private var currentTab = TAB_DISPONIBLES

    // ✅ filtro opcional si vienes desde RouteDetail
    private var routeIdFilter: String? = null

    companion object {
        private const val TAB_DISPONIBLES = 0
        private const val TAB_MIS_EVENTOS = 1
        private const val TAB_PARTICIPO = 2

        private const val TAG = "RutasGrupalesFrag"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRutasGrupalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        routeIdFilter = arguments?.getString("routeId")
        Log.d(TAG, "routeIdFilter=$routeIdFilter")

        setupRecyclerView()
        setupTabs()
        setupButtons()
        observeViewModel()

        loadInitial()
    }

    private fun loadInitial() {
        if (!routeIdFilter.isNullOrBlank()) {
            currentTab = TAB_DISPONIBLES
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(TAB_DISPONIBLES))
            viewModel.loadEventosPorRuta(routeIdFilter!!)
        } else {
            viewModel.loadEventosDisponibles()
        }
    }

    private fun setupRecyclerView() {
        eventoAdapter = EventoAdapter(
            onEventoClick = { evento -> showEventoInfo(evento) },
            onJoinClick = { evento -> viewModel.joinEvento(evento) },
            onLeaveClick = { evento -> viewModel.leaveEvento(evento) },
            onChatClick = { evento -> tryNavigateToChat(evento) }
        )

        binding.rvEventos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventoAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    TAB_DISPONIBLES -> {
                        currentTab = TAB_DISPONIBLES
                        if (!routeIdFilter.isNullOrBlank()) {
                            viewModel.loadEventosPorRuta(routeIdFilter!!)
                        } else {
                            viewModel.loadEventosDisponibles()
                        }
                    }
                    TAB_MIS_EVENTOS -> {
                        currentTab = TAB_MIS_EVENTOS
                        viewModel.loadMisEventos()
                    }
                    TAB_PARTICIPO -> {
                        currentTab = TAB_PARTICIPO
                        viewModel.loadEventosParticipando()
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                refreshCurrentTab()
            }
        })
    }

    private fun setupButtons() {
        binding.fabRefresh.setOnClickListener { refreshCurrentTab() }

        binding.btnCrearEvento.setOnClickListener {
            Toast.makeText(requireContext(), "Crear evento (pendiente conectar)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            TAB_DISPONIBLES -> {
                if (!routeIdFilter.isNullOrBlank()) viewModel.loadEventosPorRuta(routeIdFilter!!)
                else viewModel.loadEventosDisponibles()
            }
            TAB_MIS_EVENTOS -> viewModel.loadMisEventos()
            TAB_PARTICIPO -> viewModel.loadEventosParticipando()
        }
    }

    private fun observeViewModel() {
        viewModel.eventos.observe(viewLifecycleOwner) { if (currentTab == TAB_DISPONIBLES) updateList(it) }
        viewModel.misEventos.observe(viewLifecycleOwner) { if (currentTab == TAB_MIS_EVENTOS) updateList(it) }
        viewModel.eventosParticipando.observe(viewLifecycleOwner) { if (currentTab == TAB_PARTICIPO) updateList(it) }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
                refreshCurrentTab()
            }
        }
    }

    private fun updateList(eventos: List<EventoGrupal>) {
        if (eventos.isEmpty()) {
            binding.rvEventos.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.rvEventos.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            eventoAdapter.submitList(eventos)
        }
    }

    private fun showEventoInfo(evento: EventoGrupal) {
        Toast.makeText(
            requireContext(),
            "Evento de ${evento.organizadorNombre} · ${evento.estado}",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * ✅ FUNCIÓN CORREGIDA: Navegación segura al chat
     *
     * Esta función ahora maneja correctamente la navegación desde cualquier
     * fragmento, no solo desde RutasGrupalesFragment.
     */
    private fun tryNavigateToChat(evento: EventoGrupal) {
        // 1. Verificar permisos de acceso
        val canEnter = (evento.isParticipant == true) || (evento.isOrganizer == true)

        if (!canEnter) {
            Toast.makeText(
                requireContext(),
                "🔒 Únete al evento para acceder al chat",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 2. Preparar los datos de navegación
        val chatId = evento.chatId
        val eventName = "Chat de ${evento.organizadorNombre}"

        Log.d(TAG, "📱 Intentando navegar al chat: chatId=$chatId")

        try {
            // 3. Intentar navegación usando SafeArgs (lo ideal)
            val action = RutasGrupalesFragmentDirections
                .actionRutasGrupalesFragmentToGroupChatFragment(
                    chatId = chatId,
                    eventName = eventName
                )
            findNavController().navigate(action)

            Log.d(TAG, "✅ Navegación exitosa usando SafeArgs")

        } catch (e: IllegalArgumentException) {
            // 4. Si falla (porque estamos en otro fragmento), usar navegación genérica
            Log.w(TAG, "⚠️ SafeArgs no disponible desde este destino, usando navegación genérica")

            try {
                // Crear Bundle manualmente con los argumentos
                val bundle = Bundle().apply {
                    putString("chatId", chatId)
                    putString("eventName", eventName)
                }

                // Navegar usando el ID de destino directamente
                findNavController().navigate(
                    com.senderlink.app.R.id.groupChatFragment,
                    bundle
                )

                Log.d(TAG, "✅ Navegación exitosa usando Bundle")

            } catch (e2: Exception) {
                // 5. Si aún así falla, mostrar error al usuario
                Log.e(TAG, "❌ Error navegando al chat: ${e2.message}", e2)

                Toast.makeText(
                    requireContext(),
                    "No se puede abrir el chat en este momento",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            // 6. Capturar cualquier otro error inesperado
            Log.e(TAG, "❌ Error inesperado al navegar: ${e.message}", e)

            Toast.makeText(
                requireContext(),
                "Error al abrir el chat",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}