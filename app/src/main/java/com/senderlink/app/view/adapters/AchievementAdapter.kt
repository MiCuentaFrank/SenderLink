package com.senderlink.app.view.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.senderlink.app.R
import com.senderlink.app.databinding.ItemAchievementBinding

class AchievementAdapter(
    private val locked: Boolean = false
) : RecyclerView.Adapter<AchievementAdapter.VH>() {

    private var items: List<String> = emptyList()

    inner class VH(val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val badge = items[position]
        val b = holder.binding

        val (iconRes, label) = badgeInfo(badge)
        b.imgAch.setImageResource(iconRes)
        b.txtAch.text = label

        if (locked) {
            b.imgAch.alpha = 0.3f
            b.txtAch.alpha = 0.3f
        } else {
            b.imgAch.alpha = 1f
            b.txtAch.alpha = 1f
        }
    }

    override fun getItemCount() = items.size

    fun submitList(badges: List<String>) {
        items = badges
        notifyDataSetChanged()
    }

    companion object {
        fun badgeInfo(badge: String): Pair<Int, String> = when (badge) {
            "FIRST_POST"  -> Pair(R.drawable.ic_forest,   "Primera publicación")
            "FIRST_EVENT" -> Pair(R.drawable.ic_add,      "Primer evento")
            "TEAM_PLAYER" -> Pair(R.drawable.ic_people,   "Jugador de equipo")
            else          -> Pair(R.drawable.ic_forest,   badge)
        }

        val ALL_BADGES = listOf("FIRST_POST", "FIRST_EVENT", "TEAM_PLAYER")
    }
}
