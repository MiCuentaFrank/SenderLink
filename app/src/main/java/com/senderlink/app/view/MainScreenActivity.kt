package com.senderlink.app.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.senderlink.app.R

import com.senderlink.app.databinding.ActivityMainScreenBinding
import com.senderlink.app.view.fragments.HomeFragment
import com.senderlink.app.view.fragments.ComunidadFragment
import com.senderlink.app.view.fragments.MapasFragment
import com.senderlink.app.view.fragments.PerfilFragment

class MainScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainScreenBinding

    override fun onStart() {
        super.onStart()

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            // Si no hay usuario logueado, volver al Login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding= ActivityMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Fragment inicial al abrir la app
        replaceFragment(HomeFragment())

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_comunidad -> {
                    replaceFragment(ComunidadFragment())
                    true
                }
                R.id.nav_maps -> {
                    replaceFragment(MapasFragment())
                    true
                }
                R.id.nav_perfil -> {
                    replaceFragment(PerfilFragment())
                    true
                }
                else -> false
            }
        }



    }
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }

}