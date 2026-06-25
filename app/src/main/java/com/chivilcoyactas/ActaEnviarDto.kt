package com.chivilcoyactas.net

import com.google.gson.annotations.SerializedName

data class FotoDto(
    @SerializedName("tipo_media") val tipoMedia: String,     // "FOTO_1", "FOTO_2"
    @SerializedName("foto_base64") val fotoBase64: String    // La imagen convertida a texto string
)
data class ActaEnviarDto(
    @SerializedName("cabecera") val cabecera: CabeceraDto,
    @SerializedName("infractor") val infractor: InfractorDto?,
    @SerializedName("faltas") val faltas: List<FaltaDto>,
    @SerializedName("testigos") val testigos: List<TestigoDto>,
    @SerializedName("datos_transito") val datosTransito: DatosTransitoDto?,
    @SerializedName("datos_inspeccion") val datosInspeccion: DatosInspeccionDto?,
    @SerializedName("fotos") val fotos: List<FotoDto> // 👈 📸 AGREGADO
)