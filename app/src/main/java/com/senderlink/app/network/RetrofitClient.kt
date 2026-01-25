package com.senderlink.app.network

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "http://192.168.1.20:3000/"  // ✅ Con / al final

    // ✅ Gson configurado para ser más permisivo
    private val gson = GsonBuilder()
        .setLenient()
        .create()

    // ✅ Logging interceptor
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC

        }
    }

    // ✅ Cliente HTTP mejorado
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            // ✅ NUEVO: Interceptor para asegurar headers JSON
            .addInterceptor { chain ->
                val original = chain.request()

                val contentType = original.body?.contentType()?.toString().orEmpty()
                val isMultipart = contentType.startsWith("multipart/")

                val builder = original.newBuilder()

                // Solo forzar JSON si NO es multipart y no viene ya definido
                builder.header("Accept", "application/json")

                if (!isMultipart) {
                    if (original.header("Content-Type") == null) {
                        builder.header("Content-Type", "application/json")
                    }
                }


                chain.proceed(builder.build())
            }

            .build()
    }

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))  // ✅ Usa Gson configurado
            .build()
    }
}