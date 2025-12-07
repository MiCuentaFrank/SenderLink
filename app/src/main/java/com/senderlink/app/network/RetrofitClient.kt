package com.senderlink.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "http://192.168.1.33:3000/api/"

    // Si usas móvil físico en la misma red WiFi:
    // private const val BASE_URL = "http://TU_IP_LOCAL:3000/api/"

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
