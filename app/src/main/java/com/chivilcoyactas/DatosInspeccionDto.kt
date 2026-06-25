package com.chivilcoyactas.net

import com.google.gson.annotations.SerializedName

data class DatosInspeccionDto(
    @SerializedName("tipo_inspeccion") val tipoInspeccion: String,
    @SerializedName("tipo_inmueble") val tipoInmueble: String, // Las categorías unidas por coma
    @SerializedName("nro_referencia_acta") val nroReferenciaActa: String?,
    @SerializedName("se_procede_a") val seProcedeA: String,
    @SerializedName("comercio") val comercio: ComercioDto?,
    @SerializedName("catastro") val catastro: CatastroDto?
)

data class ComercioDto(
    @SerializedName("nombre_comercio") val nombreComercio: String,
    @SerializedName("nro_habilitacion") val nroHabilitacion: String?,
    @SerializedName("rubro") val rubro: String
)

data class CatastroDto(
    @SerializedName("circ") val circ: String?,
    @SerializedName("secc") val secc: String?,
    @SerializedName("ch_nro") val chNro: String?,
    @SerializedName("ch_let") val chLet: String?,
    @SerializedName("quin_nro") val quinNro: String?,
    @SerializedName("quin_let") val quinLet: String?,
    @SerializedName("frac_nro") val fracNro: String?,
    @SerializedName("frac_letra") val fracLetra: String?,
    @SerializedName("mz_nro") val mzNro: String?,
    @SerializedName("mz_let") val mzLet: String?,
    @SerializedName("parc_nro") val parcNro: String?,
    @SerializedName("parc_let") val parcLet: String?,
    @SerializedName("sub_parc") val subParc: String?,
    @SerializedName("uf") val uf: String?
)