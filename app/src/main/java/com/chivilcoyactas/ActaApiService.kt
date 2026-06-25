package com.chivilcoyactas.net

import com.chivilcoyactas.CatalogosMaestrosResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ActaApiService {

    // 🚀 CAMBIO A MULTIPART: Reemplazamos el envío de @Body por partes
    @Multipart
    @POST("api/actas")
    suspend fun enviarActaMultipart(
        @Part("acta_datos") datosActa: RequestBody, // El JSON con todo el árbol de texto
        @Part fotos: List<MultipartBody.Part>        // Los archivos binarios reales (.jpg)
    ): Response<SincronizacionResponse>

    // 👇 AGREGÁ ESTA LÍNEA ACÁ ABAJO 👇
    @GET("api/catalogos")
    suspend fun obtenerCatalogosMaestros(): Response<CatalogosMaestrosResponse>
}

// 📦 Una clase simple para recibir la respuesta que nos devuelva Laravel/Node/Python
data class SincronizacionResponse(
    val success: Boolean,
    val message: String,
    val idServer: Long? // El ID definitivo que generó el servidor web
)