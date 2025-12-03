package com.senderlink.app.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.senderlink.app.R
import com.senderlink.app.databinding.ActivityMainScreenBinding

class MainScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainScreenBinding

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
    }
}
