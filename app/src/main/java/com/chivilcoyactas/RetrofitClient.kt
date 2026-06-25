package com.chivilcoyactas.net

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 🌐 NOTA: Cuando tengan el servidor de desarrollo, cambian esta IP por la real.
    // Si prueban local con la compu en la misma red WiFi, usan la IP de su máquina (ej: 192.168.1.50)
    private const val BASE_URL = "http://127.0.0.1:8000/" // "10.0.2.2" apunta al localhost de la PC desde el emulador de Android

    val apiService: ActaApiService by lazy {
        // 🕵️‍♂️ Creamos el interceptor y le decimos que nos muestre todo el CUERPO (BODY) del mensaje
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Se lo asociamos al cliente de red
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient) // 👈 Le pasamos nuestro cliente espía
            .build()
            .create(ActaApiService::class.java)
    }
}