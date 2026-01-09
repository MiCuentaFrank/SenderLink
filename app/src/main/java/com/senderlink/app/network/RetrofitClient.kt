package com.senderlink.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * **Cliente Retrofit centralizado para la app SenderLink.**
 *
 * Este objeto expone una única instancia de Retrofit usando lazy loading,
 * garantizando que solo se construya una vez durante toda la ejecución.
 *
 *  Características:
 * - Define la URL base de la API.
 * - Usa Gson para convertir JSON ↔ objetos Kotlin.
 * - Implementa el patrón Singleton mediante `object` y `by lazy`.
 */
object RetrofitClient {

    /**
     * URL base de la API REST del backend de SenderLink.
     *
     * IMPORTANTE:
     * - Si estás usando un móvil físico, asegúrate de poner tu IP local.
     * - Finaliza SIEMPRE con una barra `/`.
     */
    private const val BASE_URL = "http://192.168.1.33:3000/api/"

    /**
     * Instancia única de Retrofit utilizada en toda la aplicación.
     *
     * Se crea solo cuando se accede por primera vez gracias a `lazy`.
     */
    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // Conversión JSON
            .build()
    }
}
