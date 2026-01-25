package com.senderlink.app.view

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.senderlink.app.R
import com.senderlink.app.databinding.ActivityMainScreenBinding

class MainScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainScreenBinding
    private lateinit var navController: NavController

    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBar: AppBarLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Views
        toolbar = binding.toolbar
        appBar = binding.appBarLayout

        // ✅ Toolbar global
        setSupportActionBar(toolbar)

        // ✅ 1) Pintar la STATUS BAR del mismo color que tu AppBar (verde)
        window.statusBarColor = getColor(R.color.sl_primary)

        // ✅ 2) Iconos BLANCOS en status bar (porque el fondo es verde oscuro)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = false

        // ✅ (Opcional recomendado) Iconos oscuros en nav bar si tu bottom nav es blanca
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightNavigationBars = true

        // ✅ NavController
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // ✅ Solo insets abajo (gestos / nav bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = bottomInset)
            insets
        }

        setupBottomNavWithStateRestore()
    }

    private fun setupBottomNavWithStateRestore() {

        binding.bottomNavigationView.setOnItemSelectedListener { item ->

            // ✅ HOME especial
            if (item.itemId == R.id.nav_home) {
                if (navController.currentDestination?.id == R.id.nav_home) {
                    return@setOnItemSelectedListener true
                }
                navController.popBackStack(R.id.nav_home, false)
                Log.d("MainScreenActivity", "✅ Navegando a Home con backstack limpio")
                return@setOnItemSelectedListener true
            }

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
                Log.e("MainScreenActivity", "Error al navegar: ${e.message}")
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
