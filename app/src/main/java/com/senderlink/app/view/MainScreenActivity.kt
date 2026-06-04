package com.senderlink.app.view

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.senderlink.app.R
import com.senderlink.app.databinding.ActivityMainScreenBinding
import com.senderlink.app.utils.UserManager

class MainScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainScreenBinding
    private val userManager = UserManager.getInstance()
    private var statusBarHeight = 0

    // Destinos donde el AppBar debe ser visible
    private val appBarDestinations = setOf(
        R.id.nav_home,
        R.id.nav_comunidad,
        R.id.nav_maps
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) Obtener el NavHostFragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 2) Conectar BottomNavigation con NavController
        NavigationUI.setupWithNavController(binding.bottomNavigationView, navController)

        // 3) Cargar usuario y observar para actualizar el saludo
        userManager.loadCurrentUser()
        userManager.currentUser.observe(this) { _ ->
            binding.tvUserGreeting.text = "¡Hola, ${userManager.getUserName()}!"
        }

        // 4) Mostrar/ocultar AppBar según el destino activo
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showAppBar = destination.id in appBarDestinations
            binding.appBarLayout.visibility = if (showAppBar) View.VISIBLE else View.GONE
            binding.statusBarScrim.visibility = if (showAppBar) View.GONE else View.VISIBLE
            (binding.navHostFragment.layoutParams as? ConstraintLayout.LayoutParams)?.let {
                it.topMargin = if (showAppBar) 0 else statusBarHeight
                binding.navHostFragment.requestLayout()
            }
        }

        // 5) Edge-to-edge: ajustar tamaño del scrim y padding del bottom nav
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = bars.top
            binding.statusBarScrim.layoutParams.height = statusBarHeight
            binding.bottomNavigationView.setPadding(0, 0, 0, bars.bottom)
            windowInsets
        }
    }
}