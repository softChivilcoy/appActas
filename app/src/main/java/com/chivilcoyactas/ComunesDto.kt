package com.chivilcoyactas.net

import com.google.gson.annotations.SerializedName

// 👤 Datos Completos del Infractor / Responsable
data class InfractorDto(
    @SerializedName("nombrecompleto") val nombreCompleto: String,
    @SerializedName("dni") val dni: String,
    @SerializedName("provincia") val provincia: String,   // 🚀 AGREGADO
    @SerializedName("localidad") val localidad: String,   // 🚀 AGREGADO
    @SerializedName("cp") val cp: String,                 // 🚀 AGREGADO
    @SerializedName("calle") val calle: String,
    @SerializedName("altura") val altura: String,
    @SerializedName("niegadatos") val niegaDatos: Int,
    @SerializedName("vinculolugar") val vinculoLugar: String? // 👈 Agregado el vínculo para Inspección General

)

// 📝 Faltas (Queda igual)
data class FaltaDto(
    @SerializedName("codigofalta") val codigoArticulo: String,
    @SerializedName("descripcion") val descripcion: String
)

// 👥 Testigos (Enviamos solo lo original que se cargó en la calle)
data class TestigoDto(
    @SerializedName("dnioriginal") val dniOriginal: String,             // 👈 Sacados los guiones bajos
    @SerializedName("nombreoriginal") val nombreOriginal: String,         // 👈 Sacados los guiones bajos
    @SerializedName("domiciliooriginal") val domicilioOriginal: String,   // 👈 Sacados los guiones bajos
    @SerializedName("provinciaoriginal") val provinciaOriginal: String,
    @SerializedName("localidadoriginal") val localidadOriginal: String,   // 👈 Sacados los guiones bajos
    @SerializedName("cporiginal") val cpOriginal: String                 // 👈 Sacados los guiones bajos
)