package com.senderlink.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // ✅ CAMBIA SOLO ESTO si tu backend usa otra URL
    // Emulador Android + servidor en tu PC:
    private const val BASE_URL = "http://192.168.1.33:3000/"

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
