package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentRutasGrupalesBinding
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.view.adapters.EventoAdapter
import com.senderlink.app.viewmodel.RutasGrupalesViewModel

class RutasGrupalesFragment : Fragment() {

    private var _binding: FragmentRutasGrupalesBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventoAdapter: EventoAdapter
    private val viewModel: RutasGrupalesViewModel by activityViewModels()

    private var currentTab = TAB_DISPONIBLES
    private var routeIdFilter: String? = null
    private var showAllDisponibles: Boolean = false

    // ✅ NUEVO: Para evitar múltiples llamadas durante inicialización
    private var isInitialized = false

    // ✅ NUEVO: Para scroll automático
    private var pendingScrollToEventId: String? = null

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



        setupRecyclerView()
        setupTabs()
        setupButtons()

        // ✅ IMPORTANTE: Observar PRIMERO antes de cargar datos
        observeViewModel()

        // Estado inicial desde VM (route context)
        routeIdFilter = viewModel.routeFilterId.value
        showAllDisponibles = viewModel.showAllDisponibles.value == true
        setupFiltroRutaUI()

        // ✅ Marcar como inicializado ANTES de hacer cualquier carga
        isInitialized = true

        // ✅ NUEVO: Verificar si hay navegación pendiente desde RouteDetail
        val preferredTab = viewModel.navPreferredTab.value
        if (preferredTab != null) {
            Log.d(TAG, "✅ Navegación automática a tab $preferredTab")
            selectTab(preferredTab, fromUser = false)

            // Scroll automático si hay evento seleccionado
            pendingScrollToEventId = viewModel.selectedEventId.value

            // Limpiar para que no se repita
            viewModel.clearNavigation()
        } else {
            // Carga normal: ir al tab de disponibles
            selectTab(TAB_DISPONIBLES, fromUser = false)
        }

        // ✅ Una sola carga inicial
        refreshCurrentTab()
    }



    private fun setupRecyclerView() {
        eventoAdapter = EventoAdapter(
            onEventoClick = { evento -> showEventoInfo(evento) },
            onJoinClick = { evento -> viewModel.joinEvento(evento) },
            onLeaveClick = { evento -> viewModel.leaveEvento(evento) },
            onChatClick = { evento -> tryNavigateToChat(evento) },
            onCancelClick = { evento -> confirmCancelEvento(evento) },

            // ✅ CORREGIDO: Navegación global usando Bundle
            onVerRutaClick = { evento ->
                val routeId = evento.getRouteId()
                if (!routeId.isNullOrBlank()) {
                    Log.d(TAG, "🗺️ Navegando a ruta: $routeId")

                    // ✅ Usar acción GLOBAL en lugar de acción local
                    val bundle = Bundle().apply {
                        putString("routeId", routeId)
                    }

                    try {
                        // ✅ Navegar usando la acción global
                        findNavController().navigate(
                            R.id.action_global_routeDetailFragment,
                            bundle
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error navegando a ruta: ${e.message}")
                        Toast.makeText(
                            requireContext(),
                            "No se pudo abrir la ruta",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },

            myUidProvider = { viewModel.getUidForUi() }
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
                val position = tab?.position ?: TAB_DISPONIBLES

                // ✅ Solo refrescar si ya estamos inicializados y el usuario cambió el tab
                if (isInitialized) {
                    selectTab(position, fromUser = true)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (isInitialized) {
                    refreshCurrentTab()
                }
            }
        })
    }

    private fun selectTab(position: Int, fromUser: Boolean) {
        currentTab = position
        binding.tabLayout.getTabAt(position)?.select()

        if (fromUser) {
            refreshCurrentTab()
        }
    }

    private fun setupButtons() {
        binding.fabRefresh.setOnClickListener {
            refreshCurrentTab()
        }
    }

    private fun setupFiltroRutaUI() {
        val hasRouteContext = !routeIdFilter.isNullOrBlank()
        binding.cardFiltroRuta.visibility = if (hasRouteContext) View.VISIBLE else View.GONE

        if (hasRouteContext) {
            binding.switchVerTodas.setOnCheckedChangeListener(null)
            binding.switchVerTodas.isChecked = showAllDisponibles
            binding.switchVerTodas.setOnCheckedChangeListener { _, isChecked ->
                showAllDisponibles = isChecked
                viewModel.setShowAllDisponibles(isChecked)

                // ✅ Solo refrescar si estamos en Disponibles
                if (currentTab == TAB_DISPONIBLES) {
                    refreshCurrentTab()
                }
            }
        }
    }

    private fun refreshCurrentTab() {
        // ✅ Evitar llamadas antes de que el fragment esté listo
        if (!isInitialized) {
            Log.d(TAG, "⚠️ refreshCurrentTab ignorado - fragment no inicializado")
            return
        }

        Log.d(TAG, "refreshCurrentTab tab=$currentTab routeIdFilter=$routeIdFilter showAll=$showAllDisponibles")

        when (currentTab) {
            TAB_DISPONIBLES -> {
                if (!routeIdFilter.isNullOrBlank() && !showAllDisponibles) {
                    viewModel.loadEventosPorRuta(routeIdFilter!!)
                } else {
                    viewModel.loadEventosDisponibles()
                }
            }
            TAB_MIS_EVENTOS -> viewModel.loadMisEventos()
            TAB_PARTICIPO -> viewModel.loadEventosParticipando()
        }
    }

    private fun observeViewModel() {
        // ✅ IMPORTANTE: removeObservers primero para evitar duplicados
        viewModel.eventos.removeObservers(viewLifecycleOwner)
        viewModel.eventosRuta.removeObservers(viewLifecycleOwner)
        viewModel.misEventos.removeObservers(viewLifecycleOwner)
        viewModel.eventosParticipando.removeObservers(viewLifecycleOwner)
        viewModel.routeFilterId.removeObservers(viewLifecycleOwner)
        viewModel.showAllDisponibles.removeObservers(viewLifecycleOwner)
        viewModel.navPreferredTab.removeObservers(viewLifecycleOwner)
        viewModel.isLoading.removeObservers(viewLifecycleOwner)
        viewModel.error.removeObservers(viewLifecycleOwner)
        viewModel.successMessage.removeObservers(viewLifecycleOwner)

        // ✅ DISPONIBLES (global)
        viewModel.eventos.observe(viewLifecycleOwner) { list ->
            if (currentTab == TAB_DISPONIBLES && (routeIdFilter.isNullOrBlank() || showAllDisponibles)) {
                updateList(list)
            }
        }

        // ✅ POR RUTA (pero "Disponibles" debe filtrar: NO organizer/participant)
        viewModel.eventosRuta.observe(viewLifecycleOwner) { list ->
            if (currentTab == TAB_DISPONIBLES && !routeIdFilter.isNullOrBlank() && !showAllDisponibles) {
                val disponiblesRuta = list.filter { ev ->
                    ev.isOrganizer != true && ev.isParticipant != true
                }
                updateList(disponiblesRuta)
            }
        }

        // ✅ MIS EVENTOS (si hay contexto ruta, filtra por esa ruta)
        viewModel.misEventos.observe(viewLifecycleOwner) { list ->
            if (currentTab == TAB_MIS_EVENTOS) {
                val finalList =
                    if (!routeIdFilter.isNullOrBlank() && !showAllDisponibles)
                        list.filter { it.getRouteId() == routeIdFilter }
                    else list
                updateList(finalList)
            }
        }

        // ✅ PARTICIPO (si hay contexto ruta, filtra por esa ruta)
        viewModel.eventosParticipando.observe(viewLifecycleOwner) { list ->
            if (currentTab == TAB_PARTICIPO) {
                val finalList =
                    if (!routeIdFilter.isNullOrBlank() && !showAllDisponibles)
                        list.filter { it.getRouteId() == routeIdFilter }
                    else list
                updateList(finalList)
            }
        }

        // ✅ Filtro desde otras pantallas (RouteDetail/Social)
        viewModel.routeFilterId.observe(viewLifecycleOwner) { rid ->
            // ✅ Solo actualizar si cambió
            if (routeIdFilter != rid) {
                routeIdFilter = rid
                setupFiltroRutaUI()

                // Solo refrescar si ya estamos inicializados y en el tab correcto
                if (isInitialized && currentTab == TAB_DISPONIBLES) {
                    refreshCurrentTab()
                }
            }
        }

        viewModel.showAllDisponibles.observe(viewLifecycleOwner) { showAll ->
            if (showAllDisponibles != (showAll == true)) {
                showAllDisponibles = showAll == true
                setupFiltroRutaUI()
            }
        }

        // ✅ NUEVO: observar tab preferido desde RouteDetail
        viewModel.navPreferredTab.observe(viewLifecycleOwner) { tabIndex ->
            if (tabIndex != null && isInitialized) {
                Log.d(TAG, "✅ Cambio automático a tab $tabIndex")
                selectTab(tabIndex, fromUser = false)
                refreshCurrentTab()
            }
        }

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
        Log.d(TAG, "updateList size=${eventos.size}")

        if (eventos.isEmpty()) {
            binding.rvEventos.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            eventoAdapter.submitList(emptyList())
        } else {
            binding.rvEventos.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            eventoAdapter.submitList(eventos)

            // ✅ NUEVO: Scroll automático al evento si hay uno pendiente
            pendingScrollToEventId?.let { eventId ->
                val position = eventos.indexOfFirst { it.id == eventId }
                if (position >= 0) {
                    Log.d(TAG, "✅ Scroll automático a posición $position (evento $eventId)")

                    // Delay pequeño para que el RecyclerView se haya renderizado
                    binding.rvEventos.post {
                        binding.rvEventos.smoothScrollToPosition(position)

                        // ✅ Opcional: highlight temporal de la card
                        binding.rvEventos.postDelayed({
                            val viewHolder = binding.rvEventos.findViewHolderForAdapterPosition(position)
                            viewHolder?.itemView?.let { itemView ->
                                // Efecto visual temporal (opcional)
                                itemView.alpha = 0.3f
                                itemView.animate()
                                    .alpha(1.0f)
                                    .setDuration(500)
                                    .start()
                            }
                        }, 300)
                    }

                    // Limpiar para que no se repita
                    pendingScrollToEventId = null
                }
            }
        }
    }

    private fun showEventoInfo(evento: EventoGrupal) {
        Toast.makeText(
            requireContext(),
            "Evento de ${evento.organizadorNombre} · ${evento.estado}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun tryNavigateToChat(evento: EventoGrupal) {
        val canEnter = (evento.isParticipant == true) || (evento.isOrganizer == true)

        Log.d(TAG, "➡️ tryNavigateToChat eventoId=${evento.id} chatId=${evento.chatId} canEnter=$canEnter")

        if (!canEnter) {
            Toast.makeText(requireContext(), "🔒 Únete al evento para acceder al chat", Toast.LENGTH_SHORT).show()
            return
        }

        val chatId = evento.chatId
        val eventName = "Chat de ${evento.organizadorNombre}"

        val bundle = Bundle().apply {
            putString("chatId", evento.chatId)
            putString("eventName", "Chat de ${evento.organizadorNombre}")
            putString("eventoId", evento.id)
        }

        findNavController().navigate(R.id.action_global_groupChatFragment, bundle)


    }


    private fun confirmCancelEvento(evento: EventoGrupal) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Cancelar Evento")
            .setMessage("¿Estás seguro de que quieres cancelar este evento?")
            .setPositiveButton("Sí, cancelar") { _, _ ->
                viewModel.cancelEvento(evento)
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isInitialized = false
        _binding = null
    }
}