package com.senderlink.app.view


import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.senderlink.app.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 2) Inflamos el layout usando ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        // 3) Le decimos a la Activity que la vista raiz es binding.root
        setContentView(binding.root)
        // 4) acceder a los elementos del layout usando binding
        binding.btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }


    }
}
