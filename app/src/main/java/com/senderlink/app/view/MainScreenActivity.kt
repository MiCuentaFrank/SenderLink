package com.senderlink.app.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.senderlink.app.R
import com.senderlink.app.databinding.ActivityMainScreenBinding


class MainScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainScreenBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) Obtener el NavHostFragment + NavController
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 2) Configurar BottomNavigation con restauración de estado
        setupBottomNavWithStateRestore()
    }



    private fun setupBottomNavWithStateRestore() {

        binding.bottomNavigationView.setOnItemSelectedListener { item ->

            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    navController.graph.startDestinationId,
                    inclusive = false,
                    saveState = true
                )
                .build()

            return@setOnItemSelectedListener try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        binding.bottomNavigationView.setOnItemReselectedListener {
            // no-op
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.nav_home ->
                    binding.bottomNavigationView.menu.findItem(R.id.nav_home)?.isChecked = true
                R.id.nav_comunidad ->
                    binding.bottomNavigationView.menu.findItem(R.id.nav_comunidad)?.isChecked = true
                R.id.nav_maps ->
                    binding.bottomNavigationView.menu.findItem(R.id.nav_maps)?.isChecked = true
                R.id.nav_perfil ->
                    binding.bottomNavigationView.menu.findItem(R.id.nav_perfil)?.isChecked = true
            }
        }
    }

}

