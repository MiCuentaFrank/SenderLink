package com.senderlink.app.view.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.senderlink.app.databinding.ItemParticipanteBinding
import com.senderlink.app.model.Participante

class ParticipanteAdapter(
    private val organizadorUid: String?
) : ListAdapter<Participante, ParticipanteAdapter.VH>(DIFF) {

    private val TAG = "ParticipanteAdapter"

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Participante>() {
            override fun areItemsTheSame(oldItem: Participante, newItem: Participante): Boolean =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: Participante, newItem: Participante): Boolean =
                oldItem == newItem
        }
    }

    init {
        Log.d(TAG, "✅ Adapter creado con organizadorUid=${organizadorUid?.take(8)}")
    }

    inner class VH(private val binding: ItemParticipanteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Participante) {
            Log.d(TAG, "📌 bind() uid=${item.uid.take(8)} nombre='${item.nombre}'")

            // ✅ Nombre
            val nombreDisplay = item.nombre?.takeIf { it.isNotBlank() } ?: "Usuario"
            binding.tvNombre.text = nombreDisplay
            Log.d(TAG, "   tvNombre.text='$nombreDisplay'")

            // ✅ Chip organizador
            val isOrg = !organizadorUid.isNullOrBlank() && item.uid == organizadorUid
            binding.chipOrganizador.visibility = if (isOrg) View.VISIBLE else View.GONE
            Log.d(TAG, "   chipOrganizador=${if (isOrg) "VISIBLE" else "GONE"}")

            // ✅ Foto circular con Glide (circleCrop hace que sea redonda)
            val photo = item.foto
            if (!photo.isNullOrBlank() && photo != "null" && photo != "") {
                Log.d(TAG, "   Cargando foto: ${photo.take(40)}...")
                Glide.with(binding.root)
                    .load(photo)
                    .placeholder(android.R.drawable.ic_menu_myplaces)
                    .circleCrop()  // ✅ Hace la imagen circular
                    .into(binding.imgParticipante)
            } else {
                Log.d(TAG, "   Sin foto válida, usando placeholder")
                binding.imgParticipante.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        Log.d(TAG, "🔨 onCreateViewHolder()")
        val binding = ItemParticipanteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        Log.d(TAG, "🔗 onBindViewHolder(position=$position) -> uid=${item.uid.take(8)}")
        holder.bind(item)
    }

    override fun submitList(list: List<Participante>?) {
        Log.d(TAG, "📋 submitList() size=${list?.size ?: 0}")
        list?.forEachIndexed { index, p ->
            Log.d(TAG, "   [$index] uid=${p.uid.take(8)} nombre='${p.nombre}'")
        }
        super.submitList(list)
    }

    override fun getItemCount(): Int {
        val count = super.getItemCount()
        Log.d(TAG, "🔢 getItemCount() = $count")
        return count
    }
}