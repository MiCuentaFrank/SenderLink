package com.senderlink.app.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {


    private const val BASE_URL = "http://192.168.1.20:3000/"

    // ✅ Cliente HTTP con timeouts extendidos
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)  // Tiempo para conectar
            .readTimeout(60, TimeUnit.SECONDS)     // Tiempo para leer respuesta
            .writeTimeout(30, TimeUnit.SECONDS)    // Tiempo para enviar datos
            .build()
    }

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)  // ✅ Usamos el cliente personalizado
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}