package com.chivilcoyactas.net

import com.google.gson.annotations.SerializedName

data class DatosTransitoDto(
    @SerializedName("vehiculo") val vehiculo: VehiculoDto?,
    @SerializedName("retencion_licencia") val retencionLicencia: Boolean,
    @SerializedName("retencion_vehiculo") val retencionVehiculo: Boolean,
    @SerializedName("secuestro_inventario") val secuestroInventario: String?,
    @SerializedName("alcoholemia") val alcoholemia: AlcoholemiaDto? // 👈 Objeto anidado con el blindaje
)

data class VehiculoDto(
    @SerializedName("dominio_patente") val dominioPatente: String,
    @SerializedName("marca") val marca: String?,
    @SerializedName("modelo") val modelo: String?,
    @SerializedName("tipo_vehiculo") val tipoVehiculo: String
)

// 🛡️ El escudo legal para Node / Python
data class AlcoholemiaDto(
    @SerializedName("resultado_alcoholemia") val resultadoAlcoholemia: Double,
    @SerializedName("marca_alcoholimetro") val marcaAlcoholimetro: String,
    @SerializedName("modelo_alcoholimetro") val modeloAlcoholimetro: String,
    @SerializedName("nro_serie_alcoholimetro") val nroSerieAlcoholimetro: String,
    @SerializedName("cod_aprobacion_alcoholimetro") val codAprobacionAlcoholimetro: String
)