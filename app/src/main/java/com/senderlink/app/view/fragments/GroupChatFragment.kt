package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.FragmentGroupChatBinding
import com.senderlink.app.view.adapters.GroupMessageAdapter
import com.senderlink.app.viewmodel.GroupChatViewModel

class GroupChatFragment : Fragment() {

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

        // ✅ Hardening básico (sin cambiar tu flujo)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
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

        setupToolbar()
        setupRecycler()
        setupInput()
        setupObservers()

        // Carga inicial
        viewModel.loadMessages(args.chatId, limit = 50)
    }

    private fun setupToolbar() {
        binding.toolbarGroupChat.apply {
            title = args.eventName
            setNavigationOnClickListener { findNavController().navigateUp() }
        }
    }

    private fun setupRecycler() {
        adapter = GroupMessageAdapter(
            currentUidProvider = { FirebaseAuth.getInstance().currentUser?.uid }
        )

        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@GroupChatFragment.adapter
            itemAnimator = null
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener { sendCurrentMessage() }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
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
        if (text.isBlank()) return

        // Evitar doble envío si está cargando
        if (viewModel.isLoading.value == true) return

        viewModel.sendMessage(args.chatId, text)
        binding.etMessage.setText("")
    }

    private fun setupObservers() {
        viewModel.messages.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list) {
                if (list.isNotEmpty()) {
                    binding.recyclerMessages.scrollToPosition(list.size - 1)
                }
            }
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = loading && adapter.itemCount > 0

            // actualizar estado del botón enviar (si hay texto)
            val hasText = !binding.etMessage.text.isNullOrBlank()
            binding.btnSend.isEnabled = hasText && !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
