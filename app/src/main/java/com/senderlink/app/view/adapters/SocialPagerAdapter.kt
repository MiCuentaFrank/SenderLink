package com.senderlink.app.view.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.senderlink.app.view.fragments.ComunidadFragment
import com.senderlink.app.view.fragments.RutasGrupalesFragment

class SocialPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment {
        return if (position == 0) ComunidadFragment() else RutasGrupalesFragment()
    }
}
