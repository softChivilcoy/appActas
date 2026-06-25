package com.chivilcoyactas

import com.google.gson.annotations.SerializedName

data class CatalogosMaestrosResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("tipos_falta") val tiposFalta: List<TipoFaltaDto>,
    @SerializedName("tipos_vehiculo") val tiposVehiculo: List<TipoVehiculoDto>,
    @SerializedName("tipo_marcas") val tipoMarcas: List<TipoMarcaDto>,
    @SerializedName("tipo_modelos") val tipoModelos: List<TipoModeloDto>,
    @SerializedName("tipo_provincias") val tipoProvincias: List<TipoProvinciaDto>,
    @SerializedName("tipo_localidades") val tipoLocalidades: List<TipoLocalidadDto>,
    @SerializedName("tipos_acta") val tiposActa: List<TipoActaDto>,
    @SerializedName("quincenas") val quincenas: List<QuincenaDto>
)

// --- Clases Hijas (DTOs) ---

data class TipoFaltaDto(
    @SerializedName("id") val id: Int,
    @SerializedName("codigo") val codigoArticulo: String, // 👈 Mapea 'codigo'
    @SerializedName("descripcion") val descripcion: String
)

data class TipoVehiculoDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String
)

data class TipoMarcaDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String
)

data class TipoModeloDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("marca_id") val idMarca: Int // 👈 Mapea 'marca_id'
)

data class TipoProvinciaDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String
)

data class TipoLocalidadDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("provincia_id") val idProvincia: Int // 👈 Mapea 'provincia_id'
)

data class TipoActaDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String
)

data class QuincenaDto(
    @SerializedName("id") val id: Int,
    @SerializedName("dia_inicio") val diaInicio: Int, // 👈 Mapea 'dia_inicio' de Postgres
    @SerializedName("dia_fin") val diaFin: Int,       // 👈 Mapea 'dia_fin' de Postgres
    @SerializedName("id_juzgado") val idJuzgado: Int  // 👈 Mapea 'id_juzgado' de Postgres
)