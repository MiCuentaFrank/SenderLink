package com.senderlink.app.view.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemEventoBinding
import com.senderlink.app.model.EventoGrupal
import java.text.SimpleDateFormat
import java.util.*

class EventoAdapter(
    private val onEventoClick: (EventoGrupal) -> Unit,
    private val onJoinClick: (EventoGrupal) -> Unit,
    private val onLeaveClick: (EventoGrupal) -> Unit,
    private val onChatClick: (EventoGrupal) -> Unit,
    private val onCancelClick: (EventoGrupal) -> Unit,
    private val onVerRutaClick: (EventoGrupal) -> Unit,
    private val myUidProvider: () -> String?
) : ListAdapter<EventoGrupal, EventoAdapter.VH>(DIFF_CALLBACK) {

    private val TAG = "EventoAdapter"

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EventoGrupal>() {
            override fun areItemsTheSame(oldItem: EventoGrupal, newItem: EventoGrupal): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: EventoGrupal, newItem: EventoGrupal): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class VH(val b: ItemEventoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEventoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val evento = getItem(position)
        val b = holder.b

        // Click en la card completa
        b.root.setOnClickListener { onEventoClick(evento) }

        // ========================================
        // INFORMACIÓN BÁSICA
        // ========================================

        // ✅ Nombre del organizador (tvOrganizadorNombre en el XML)
        b.tvOrganizadorNombre.text = evento.organizadorNombre

        // ✅ Fecha y hora separados
        b.tvFecha.text = formatearFecha(evento.fecha)
        b.tvHora.text = evento.horaEncuentro

        // ✅ Participantes con barra de progreso
        val current = evento.participantes.size
        val max = evento.maxParticipantes
        b.tvParticipantes.text = "$current/$max participantes"
        b.progressParticipantes.max = max
        b.progressParticipantes.progress = current

        // ✅ Descripción (si existe)
        if (!evento.descripcion.isNullOrBlank()) {
            b.tvDescripcion.text = evento.descripcion
            b.tvDescripcion.visibility = View.VISIBLE
        } else {
            b.tvDescripcion.visibility = View.GONE
        }

        // ========================================
        // CHIP DE ESTADO
        // ========================================
        val chipColor = when (evento.estado) {
            "ABIERTO" -> R.color.success
            "COMPLETO" -> R.color.warning
            "CANCELADO" -> R.color.error
            "FINALIZADO" -> R.color.text_secondary
            else -> R.color.sl_primary
        }

        b.chipEstado.text = evento.estado
        b.chipEstado.chipBackgroundColor =
            ContextCompat.getColorStateList(b.root.context, chipColor)

        // ========================================
        // BOTÓN VER RUTA (SIEMPRE VISIBLE)
        // ========================================
        b.btnVerRuta.visibility = View.VISIBLE
        b.btnVerRuta.setOnClickListener {
            Log.d(TAG, "Ver ruta clicked: ${evento.id}")
            onVerRutaClick(evento)
        }

        // ========================================
        // BOTONES SEGÚN ROL
        // ========================================
        val myUid = myUidProvider()

        Log.d(TAG, """
            Evento: ${evento.id.take(8)}
            organizadorUid: ${evento.organizadorUid.take(8)}
            myUid: ${myUid?.take(8)}
            isOrganizer (backend): ${evento.isOrganizer}
            isParticipant (backend): ${evento.isParticipant}
        """.trimIndent())

        // Usar flags del backend si están disponibles, sino calcular localmente
        val isOrganizer = evento.isOrganizer ?: (evento.organizadorUid == myUid)
        val isParticipant = evento.isParticipant ?: evento.participantes.any { it.uid == myUid }

        // Ocultar todos los botones primero
        b.btnJoin.visibility = View.GONE
        b.btnCancel.visibility = View.GONE
        b.btnChat.visibility = View.GONE

        when {
            // ========================================
            // CASO 1: SOY EL ORGANIZADOR
            // ========================================
            isOrganizer -> {
                Log.d(TAG, "✅ Es organizador")

                // Botón CANCELAR (solo si el evento NO está cancelado ya)
                val estadoPermiteCancelar = evento.estado != "CANCELADO" && evento.estado != "FINALIZADO"

                if (estadoPermiteCancelar) {
                    b.btnCancel.visibility = View.VISIBLE
                    b.btnCancel.setOnClickListener {
                        Log.d(TAG, "Cancelar evento clicked")
                        onCancelClick(evento)
                    }
                    Log.d(TAG, "   Botón CANCELAR visible")
                }

                // Botón CHAT (siempre visible para organizador)
                b.btnChat.visibility = View.VISIBLE
                b.btnChat.setOnClickListener {
                    Log.d(TAG, "Chat clicked (organizador)")
                    onChatClick(evento)
                }
                Log.d(TAG, "   Botón CHAT visible")
            }

            // ========================================
            // CASO 2: SOY PARTICIPANTE (pero NO organizador)
            // ========================================
            isParticipant -> {
                Log.d(TAG, "✅ Es participante")

                // Botón SALIR (solo si evento está ABIERTO o COMPLETO)
                if (evento.estado == "ABIERTO" || evento.estado == "COMPLETO") {
                    b.btnJoin.visibility = View.VISIBLE
                    b.btnJoin.text = "Salir"
                    b.btnJoin.setOnClickListener {
                        Log.d(TAG, "Salir del evento clicked")
                        onLeaveClick(evento)
                    }
                    Log.d(TAG, "   Botón SALIR visible")
                }

                // Botón CHAT (siempre visible si eres participante)
                b.btnChat.visibility = View.VISIBLE
                b.btnChat.setOnClickListener {
                    Log.d(TAG, "Chat clicked (participante)")
                    onChatClick(evento)
                }
                Log.d(TAG, "   Botón CHAT visible")
            }

            // ========================================
            // CASO 3: NO SOY NI ORGANIZADOR NI PARTICIPANTE
            // ========================================
            evento.estado == "ABIERTO" -> {
                Log.d(TAG, "✅ Evento disponible para unirse")

                // Botón UNIRSE
                b.btnJoin.visibility = View.VISIBLE
                b.btnJoin.text = "Unirse"
                b.btnJoin.setOnClickListener {
                    Log.d(TAG, "Unirse al evento clicked")
                    onJoinClick(evento)
                }
                Log.d(TAG, "   Botón UNIRSE visible")
            }

            else -> {
                Log.d(TAG, "⚠️ Evento no disponible (estado: ${evento.estado})")
            }
        }
    }

    /**
     * Formatea la fecha del evento
     * Ejemplo: "2026-02-24T09:00:00.000Z" → "24 de febrero, 2026"
     */
    private fun formatearFecha(fechaISO: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(fechaISO)

            val formatoSalida = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "ES"))
            date?.let { formatoSalida.format(it) } ?: fechaISO
        } catch (e: Exception) {
            Log.e(TAG, "Error formateando fecha: ${e.message}")
            fechaISO
        }
    }
}