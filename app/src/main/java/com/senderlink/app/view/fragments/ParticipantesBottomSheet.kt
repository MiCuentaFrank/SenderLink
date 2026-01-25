package com.senderlink.app.view.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.senderlink.app.databinding.BottomSheetParticipantesBinding
import com.senderlink.app.model.Participante
import com.senderlink.app.view.adapters.ParticipanteAdapter

class ParticipantesBottomSheet : BottomSheetDialogFragment() {

    private val TAG = "ParticipantesBS"

    private var _binding: BottomSheetParticipantesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ParticipanteAdapter

    companion object {
        private const val ARG_PARTICIPANTES = "participantes"
        private const val ARG_ORGANIZADOR_UID = "organizador_uid"

        fun newInstance(
            participantes: ArrayList<Participante>,
            organizadorUid: String?
        ): ParticipantesBottomSheet {
            Log.d("ParticipantesBS", "📦 newInstance() participantes.size=${participantes.size} organizadorUid=${organizadorUid?.take(8)}")
            return ParticipantesBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARTICIPANTES, participantes)
                    putString(ARG_ORGANIZADOR_UID, organizadorUid)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "🎨 onCreateView()")
        _binding = BottomSheetParticipantesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "✅ onViewCreated()")

        val participantes =
            arguments?.getParcelableArrayList<Participante>(ARG_PARTICIPANTES) ?: arrayListOf()
        val organizadorUid = arguments?.getString(ARG_ORGANIZADOR_UID)

        Log.d(TAG, "📋 Argumentos recibidos: participantes.size=${participantes.size} organizadorUid=${organizadorUid?.take(8)}")

        participantes.forEachIndexed { index, p ->
            Log.d(TAG, "   [$index] uid=${p.uid.take(8)} nombre='${p.nombre}'")
        }

        adapter = ParticipanteAdapter(organizadorUid)

        binding.recyclerParticipantes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ParticipantesBottomSheet.adapter
            itemAnimator = null

            Log.d(TAG, "🔧 RecyclerView configurado:")
            Log.d(TAG, "   layoutManager=${layoutManager}")
            Log.d(TAG, "   adapter=${adapter}")
            Log.d(TAG, "   visibility=${visibility} (0=VISIBLE, 4=INVISIBLE, 8=GONE)")
            Log.d(TAG, "   height=${height} width=${width}")
        }

        render(participantes)
    }

    private fun render(participantes: List<Participante>) {
        Log.d(TAG, "🎬 render() participantes.size=${participantes.size}")

        binding.progressLoading.visibility = View.GONE

        val count = participantes.size
        binding.tvSubtitle.text = when (count) {
            0 -> "0 participantes"
            1 -> "1 participante"
            else -> "$count participantes"
        }

        val empty = participantes.isEmpty()
        binding.recyclerParticipantes.visibility = if (empty) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE

        Log.d(TAG, "   recyclerParticipantes.visibility=${binding.recyclerParticipantes.visibility}")
        Log.d(TAG, "   tvEmpty.visibility=${binding.tvEmpty.visibility}")
        Log.d(TAG, "   Llamando adapter.submitList()...")

        adapter.submitList(participantes)

        Log.d(TAG, "   adapter.itemCount=${adapter.itemCount}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "💀 onDestroyView()")
        _binding = null
    }
}