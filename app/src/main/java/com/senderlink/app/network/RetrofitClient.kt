package com.senderlink.app.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.GsonBuilder
import com.senderlink.app.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    // TODO: Configurar certificate pinning con el dominio y SHA-256 del certificado real
    // Para obtener los hashes, ejecutar:
    //   openssl s_client -connect TU_DOMINIO:443 | openssl x509 -pubkey -noout |
    //   openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
    // Ejemplo:
    //   .add("tu-dominio.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    //   .add("tu-dominio.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
    private val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // .add("api.senderlink.com", "sha256/YOUR_PIN_HASH_HERE")
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .certificatePinner(certificatePinner)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(loggingInterceptor)
                }
            }
            // Interceptor de autenticación Firebase
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    try {
                        val tokenResult = Tasks.await(user.getIdToken(false))
                        tokenResult.token?.let { token ->
                            builder.header("Authorization", "Bearer $token")
                        }
                    } catch (_: Exception) {
                        // Continuar sin token si falla
                    }
                }
                chain.proceed(builder.build())
            }
            // Interceptor de headers JSON
            .addInterceptor { chain ->
                val original = chain.request()
                val contentType = original.body?.contentType()?.toString().orEmpty()
                val isMultipart = contentType.startsWith("multipart/")
                val builder = original.newBuilder()
                builder.header("Accept", "application/json")
                if (!isMultipart && original.header("Content-Type") == null) {
                    builder.header("Content-Type", "application/json")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
