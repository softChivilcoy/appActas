package com.chivilcoyactas.net

import com.google.gson.annotations.SerializedName

data class CabeceraDto(
    @SerializedName("id_local") val idLocal: Long,
    @SerializedName("tipoacta") val tipoActa: String,
    @SerializedName("idinspector") val idInspector: Int,
    @SerializedName("fecha") val fecha: String,
    @SerializedName("hora") val hora: String,
    @SerializedName("latitud") val latitud: Double,
    @SerializedName("longitud") val longitud: Double,
    @SerializedName("idhojaruta") val idHojaRuta: Int?,
    @SerializedName("firmainspector_base64") val firmaInspectorBase64: String?,
    @SerializedName("esoperativo") val esOperativo: Boolean,
    @SerializedName("ejidourbano") val ejidoUrbano: Int,
    @SerializedName("nombrecalle") val nombreCalle: String,
    @SerializedName("alturacalle") val alturaCalle: Int?, // 👈 Cambiado a String? para machear con tu VM
    @SerializedName("detallepiso") val detallePiso: String?,
    @SerializedName("detallereferencia") val detalleReferencia: String?,
    @SerializedName("idjuzgado") val idJuzgado: Int?
)