package com.chivilcoyactas.net

import com.chivilcoyactas.CatalogosMaestrosResponse
import com.chivilcoyactas.LoginRequest
import com.chivilcoyactas.LoginResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ActaApiService {
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // 🚀 CAMBIO A MULTIPART: Incluye fotos y partes opcionales para firmas
    @Multipart
    @POST("api/actas")
    suspend fun enviarActaMultipart(
        @Part("acta_datos") datosActa: RequestBody,
        @Part fotos: List<MultipartBody.Part>,
        @Part firmaInfractor: MultipartBody.Part?, // 👈 AGREGADO (Nullable)
        @Part firmaTestigo: MultipartBody.Part?    // 👈 AGREGADO (Nullable)
    ): Response<SincronizacionResponse>

    @GET("api/catalogos")
    suspend fun obtenerCatalogosMaestros(): Response<CatalogosMaestrosResponse>
}

// 📦 Clase para recibir la respuesta de Laravel
data class SincronizacionResponse(
    val success: Boolean,
    val message: String,
    val idServer: Long? // El ID definitivo que generó el servidor web
)