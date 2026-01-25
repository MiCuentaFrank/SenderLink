package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentGroupChatBinding
import com.senderlink.app.view.adapters.GroupMessageAdapter
import com.senderlink.app.viewmodel.GroupChatViewModel

class GroupChatFragment : Fragment() {

    private val TAG = "GroupChatFragment"

    private var _binding: FragmentGroupChatBinding? = null
    private val binding get() = _binding!!

    private val args: GroupChatFragmentArgs by navArgs()
    private val viewModel: GroupChatViewModel by viewModels()

    private lateinit var adapter: GroupMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated chatId='${args.chatId}' eventName='${args.eventName}' eventoId='${args.eventoId}'")

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "currentUid=$uid")

        if (uid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Debes iniciar sesión para usar el chat", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        if (args.chatId.isBlank()) {
            Toast.makeText(requireContext(), "Chat no disponible", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        // ✅ Si quieres reflejar el nombre del evento aunque no haya toolbar en el fragment:
        requireActivity().title = args.eventName

        setupMenu()      // ✅ reemplaza a setupToolbar()
        setupRecycler()
        setupInput()
        setupObservers()

        Log.d(TAG, "loadMessages(chatId=${args.chatId})")
        viewModel.loadMessages(args.chatId, limit = 50)
    }

    /**
     * ✅ Menú del fragment (sin Toolbar propia).
     * Aparecerá si el Activity tiene soporte de menú / top bar.
     */
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
                menuInflater.inflate(R.menu.menu_group_chat, menu)
                Log.d(TAG, "Menu created (GroupChat). Items=${menu.size()}")
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                Log.d(TAG, "Menu clicked itemId=${menuItem.itemId}")
                return when (menuItem.itemId) {
                    R.id.action_ver_participantes -> {
                        Log.d(TAG, "➡️ Participantes clicked -> showParticipantes()")
                        showParticipantes()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecycler() {
        adapter = GroupMessageAdapter(
            currentUidProvider = { FirebaseAuth.getInstance().currentUser?.uid }
        )

        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = this@GroupChatFragment.adapter
            itemAnimator = null
        }

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "SwipeRefresh -> refresh()")
            viewModel.refresh()
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            Log.d(TAG, "btnSend clicked")
            sendCurrentMessage()
        }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                Log.d(TAG, "IME_ACTION_SEND")
                sendCurrentMessage()
                true
            } else false
        }

        binding.etMessage.addTextChangedListener { text ->
            val hasText = !text.isNullOrBlank()
            binding.btnSend.isEnabled = hasText && (viewModel.isLoading.value != true)
        }

        binding.btnSend.isEnabled = false
    }

    private fun sendCurrentMessage() {
        val text = binding.etMessage.text?.toString().orEmpty().trim()
        Log.d(TAG, "sendCurrentMessage textLen=${text.length}")

        if (text.isBlank()) return
        if (viewModel.isLoading.value == true) return

        viewModel.sendMessage(args.chatId, text)
        binding.etMessage.setText("")
    }

    private fun setupObservers() {
        viewModel.messages.observe(viewLifecycleOwner) { list ->
            Log.d(TAG, "messages observed size=${list.size}")
            adapter.submitList(list) {
                if (list.isNotEmpty()) binding.recyclerMessages.scrollToPosition(list.size - 1)
            }
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            Log.d(TAG, "isLoading observed=$loading")
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = loading && adapter.itemCount > 0
            binding.swipeRefresh.isEnabled = !loading

            val hasText = !binding.etMessage.text.isNullOrBlank()
            binding.btnSend.isEnabled = hasText && !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                Log.e(TAG, "error observed='$err'")
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        viewModel.participantsData.observe(viewLifecycleOwner) { data ->
            Log.d(TAG, "participantsData observed data=${data != null} participantes=${data?.participantes?.size}")

            if (data == null) return@observe

            if (!isAdded || childFragmentManager.isStateSaved) {
                Log.w(TAG, "Skip show bottomsheet: isAdded=$isAdded stateSaved=${childFragmentManager.isStateSaved}")
                return@observe
            }

            ParticipantesBottomSheet.newInstance(
                participantes = ArrayList(data.participantes),
                organizadorUid = data.organizadorUid
            ).show(childFragmentManager, "ParticipantesBottomSheet")
        }
    }

    private fun showParticipantes() {
        Log.d(TAG, "➡️ showParticipantes eventoId='${args.eventoId}' chatId='${args.chatId}'")

        if (args.eventoId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "⚠️ Falta eventoId para cargar participantes", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "❌ eventoId vacío: no puedo cargar participantes")
            return
        }

        val cached = viewModel.participantsData.value
        if (cached != null) {
            Log.d(TAG, "⚡ Cache hit: participantes=${cached.participantes.size}")
            ParticipantesBottomSheet.newInstance(
                participantes = ArrayList(cached.participantes),
                organizadorUid = cached.organizadorUid
            ).show(childFragmentManager, "ParticipantesBottomSheet")
            return
        }

        Toast.makeText(requireContext(), "Cargando participantes...", Toast.LENGTH_SHORT).show()
        viewModel.loadParticipants(args.eventoId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        _binding = null
    }
}
