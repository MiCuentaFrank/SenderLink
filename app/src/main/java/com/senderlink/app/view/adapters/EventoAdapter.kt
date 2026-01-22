package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemEventoBinding
import com.senderlink.app.model.EventoGrupal
import java.text.SimpleDateFormat
import java.util.*

class EventoAdapter(
    private val onEventoClick: (EventoGrupal) -> Unit,
    private val onJoinClick: (EventoGrupal) -> Unit,
    private val onLeaveClick: (EventoGrupal) -> Unit,
    private val onChatClick: (EventoGrupal) -> Unit
) : ListAdapter<EventoGrupal, EventoAdapter.EventoViewHolder>(DIFF_CALLBACK) {

    inner class EventoViewHolder(val binding: ItemEventoBinding) :
        RecyclerView.ViewHolder(binding.root)

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventoViewHolder {
        val binding = ItemEventoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventoViewHolder, position: Int) {
        val evento = getItem(position)

        holder.binding.apply {
            tvOrganizadorNombre.text = evento.organizadorNombre

            Glide.with(holder.itemView.context)
                .load(evento.organizadorFoto)
                .placeholder(android.R.drawable.ic_menu_myplaces)
                .error(android.R.drawable.ic_menu_myplaces)
                .circleCrop()
                .into(imgOrganizador)

            tvFecha.text = formatFecha(evento.fecha)
            tvHora.text = evento.horaEncuentro

            if (evento.descripcion.isNullOrBlank()) {
                tvDescripcion.visibility = View.GONE
            } else {
                tvDescripcion.visibility = View.VISIBLE
                tvDescripcion.text = evento.descripcion
            }

            val numPart = evento.getNumParticipantes()
            val maxPart = evento.maxParticipantes
            tvParticipantes.text = "$numPart/$maxPart participantes"

            progressParticipantes.max = maxPart
            progressParticipantes.progress = numPart

            setupEstado(evento)
            setupBotonAccion(evento)
            setupBotonChat(evento)

            root.setOnClickListener { onEventoClick(evento) }
        }
    }

    private fun ItemEventoBinding.setupEstado(evento: EventoGrupal) {
        when (evento.estado) {
            EventoGrupal.Estado.ABIERTO -> {
                chipEstado.text = "Abierto"
                chipEstado.setChipBackgroundColorResource(R.color.success)
                chipEstado.visibility = View.VISIBLE
            }
            EventoGrupal.Estado.COMPLETO -> {
                chipEstado.text = "Completo"
                chipEstado.setChipBackgroundColorResource(R.color.warning)
                chipEstado.visibility = View.VISIBLE
            }
            EventoGrupal.Estado.FINALIZADO -> {
                chipEstado.text = "Finalizado"
                chipEstado.setChipBackgroundColorResource(R.color.text_muted)
                chipEstado.visibility = View.VISIBLE
            }
            EventoGrupal.Estado.CANCELADO -> {
                chipEstado.text = "Cancelado"
                chipEstado.setChipBackgroundColorResource(R.color.error)
                chipEstado.visibility = View.VISIBLE
            }
            else -> chipEstado.visibility = View.GONE
        }
    }

    private fun ItemEventoBinding.setupBotonAccion(evento: EventoGrupal) {
        // Si está cancelado o finalizado -> nada
        if (evento.isCancelado() || evento.isFinalizado()) {
            btnJoin.visibility = View.GONE
            return
        }

        // ✅ Si ya participa y NO es organizador -> puede salir
        if (evento.isParticipant == true && evento.isOrganizer != true) {
            btnJoin.visibility = View.VISIBLE
            btnJoin.text = "Salir"
            btnJoin.isEnabled = true
            btnJoin.setOnClickListener { onLeaveClick(evento) }
            return
        }

        // ✅ Si es organizador -> oculto (o pon “Organizador” deshabilitado si quieres)
        if (evento.isOrganizer == true) {
            btnJoin.visibility = View.GONE
            return
        }

        // ✅ Si NO participa -> puede unirse si hay plazas
        if ((evento.estado == EventoGrupal.Estado.ABIERTO) && evento.hasPlazasDisponibles()) {
            btnJoin.visibility = View.VISIBLE
            btnJoin.text = "Unirse"
            btnJoin.isEnabled = true
            btnJoin.setOnClickListener { onJoinClick(evento) }
            return
        }

        // Completo
        if (evento.estado == EventoGrupal.Estado.COMPLETO) {
            btnJoin.visibility = View.VISIBLE
            btnJoin.text = "Completo"
            btnJoin.isEnabled = false
            return
        }

        btnJoin.visibility = View.GONE
    }

    private fun ItemEventoBinding.setupBotonChat(evento: EventoGrupal) {
        // ✅ REGLA CLAVE: solo participantes u organizador
        val canEnter = (evento.isParticipant == true) || (evento.isOrganizer == true)

        if (!canEnter || evento.isCancelado() || evento.isFinalizado()) {
            btnChat.visibility = View.GONE
        } else {
            btnChat.visibility = View.VISIBLE
            btnChat.setOnClickListener { onChatClick(evento) }
        }
    }

    private fun formatFecha(fechaIso: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("d 'de' MMMM, yyyy", Locale("es", "ES"))
            val date = inputFormat.parse(fechaIso)
            date?.let { outputFormat.format(it) } ?: fechaIso
        } catch (e: Exception) {
            fechaIso
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EventoGrupal>() {
            override fun areItemsTheSame(oldItem: EventoGrupal, newItem: EventoGrupal): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EventoGrupal, newItem: EventoGrupal): Boolean =
                oldItem == newItem
        }
    }
}
