package com.chivilcoyactas

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("dni") val dni: String,
    @SerializedName("password") val pass: String,
    @SerializedName("dispositivo_id") val dispositivoId: String // 👈 Agregado para el ANDROID_ID
)


data class ReparticionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("formulario") val formulario: String?, // "TRANSITO" o "INSPECCION"
    @SerializedName("tipos_acta") val tiposActa: List<TipoActaDto>? = emptyList(),
    @SerializedName("tipos_bienes") val tiposBienes: List<TipoBienDto>? = emptyList() // 👈 NUEVO CAMPO
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("idInspector") val idInspector: Int?,
    @SerializedName("nombreInspector") val nombreInspector: String?,
    @SerializedName("legajo") val legajo: String?,
    @SerializedName("puntoEmisionId") val puntoEmisionId: Int,
    @SerializedName("puntoEmisionCodigo") val puntoEmisionCodigo: Int,
    @SerializedName("ultimaSecuencia") val ultimaSecuencia: Int,
    @SerializedName("reparticiones") val reparticiones: List<ReparticionDto>?
)