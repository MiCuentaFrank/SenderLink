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
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.FragmentDirectChatBinding
import com.senderlink.app.view.adapters.DirectMessageAdapter
import com.senderlink.app.viewmodel.DirectChatViewModel

class DirectChatFragment : Fragment() {

    private var _binding: FragmentDirectChatBinding? = null
    private val binding get() = _binding!!

    private val args: DirectChatFragmentArgs by navArgs()
    private val viewModel: DirectChatViewModel by viewModels()
    private lateinit var adapter: DirectMessageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDirectChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().title = args.otherNombre

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        setupRecycler(uid)
        setupInput()
        setupObservers()

        viewModel.loadMessages(args.chatId)
    }

    private fun setupRecycler(currentUid: String) {
        adapter = DirectMessageAdapter { currentUid }

        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = this@DirectChatFragment.adapter
            itemAnimator = null
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun setupInput() {
        binding.btnSend.isEnabled = false

        binding.btnSend.setOnClickListener { sendCurrentMessage() }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCurrentMessage(); true } else false
        }

        binding.etMessage.addTextChangedListener { text ->
            binding.btnSend.isEnabled = !text.isNullOrBlank() && viewModel.isLoading.value != true
        }
    }

    private fun sendCurrentMessage() {
        val text = binding.etMessage.text?.toString().orEmpty().trim()
        if (text.isBlank()) return
        viewModel.sendMessage(args.chatId, args.otherUid, text)
        binding.etMessage.setText("")
    }

    private fun setupObservers() {
        viewModel.messages.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list) {
                if (list.isNotEmpty()) binding.recyclerMessages.scrollToPosition(list.size - 1)
            }
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = loading && adapter.itemCount > 0
            binding.swipeRefresh.isEnabled = !loading
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
