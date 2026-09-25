package com.chivilcoyactas

import com.google.gson.annotations.SerializedName

data class CatalogosMaestrosResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("alcoholimetros") val alcoholimetros: List<AlcoholimetrosDto>,
    @SerializedName("tipos_falta") val tiposFalta: List<TipoFaltaDto>,
    @SerializedName("tipos_vehiculo") val tiposVehiculo: List<TipoVehiculoDto>,
    @SerializedName("tipo_marcas") val tipoMarcas: List<TipoMarcaDto>,
    @SerializedName("tipo_modelos") val tipoModelos: List<TipoModeloDto>,
    @SerializedName("tipo_provincias") val tipoProvincias: List<TipoProvinciaDto>,
    @SerializedName("tipo_localidades") val tipoLocalidades: List<TipoLocalidadDto>,
    @SerializedName("tipos_acta") val tiposActa: List<TipoActaDto>,
    @SerializedName("quincenas") val quincenas: List<QuincenaDto>,
    @SerializedName("categorias_inspeccion") val categoriasInspeccion: List<CategoriaInspeccionDto>?,
    @SerializedName("param_checklist_vehicular") val paramChecklistVehicular: List<CheckVehicularDto>? ,
    @SerializedName("tipo_vehiculo_checklist") val tipoVehiculoChecklist: List<TipoVehiculoCheckDto>?  // 👈 AGREGADO
)

// --- Clases Hijas (DTOs) ---

data class AlcoholimetrosDto(
    @SerializedName("id") val id: Int,
    @SerializedName("marca") val marca: String, // 👈 Mapea 'codigo'
    @SerializedName("modelo") val modelo: String,
    @SerializedName("nro_serie") val nroSerie: String,
    @SerializedName("cod_homologacion") val codHomologacion: String,
    @SerializedName("alcoholimetro_id") val alcoholimetroId: Int
)
data class TipoFaltaDto(
    @SerializedName("id") val id: Int,
    @SerializedName("codigo") val codigoArticulo: String, // 👈 Mapea 'codigo'
    @SerializedName("descripcion_corta") val descripcionCorta: String,
    @SerializedName("descripcion_plantilla") val descripcionPlantilla: String?
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
    @SerializedName("nombre") val nombre: String,
    @SerializedName("formulario") val formulario: String?
)

data class QuincenaDto(
    @SerializedName("id") val id: Int,
    @SerializedName("dia_inicio") val diaInicio: Int, // 👈 Mapea 'dia_inicio' de Postgres
    @SerializedName("dia_fin") val diaFin: Int,       // 👈 Mapea 'dia_fin' de Postgres
    @SerializedName("id_juzgado") val idJuzgado: Int  // 👈 Mapea 'id_juzgado' de Postgres
)

data class CategoriaInspeccionDto( // 👈 AGREGADO
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String
)

data class CheckVehicularDto( // 👈 AGREGADO
    @SerializedName("id") val id: Int,
    @SerializedName("codigo_clave") val codigoClave: String,
    @SerializedName("etiqueta_visible") val etiquetaVisible: String,
    @SerializedName("tipo_dato") val tipoDato: String,
    @SerializedName("opciones_lista") val opcionesLista: String?,
    @SerializedName("orden_ui") val ordenUi: Int,
    @SerializedName("sector_vehiculo") val sectorVehiculo: String
)

data class TipoVehiculoCheckDto( // 👈 AGREGADO
    @SerializedName("tipo_vehiculo_id") val tipoVehiculoId: Int,
    @SerializedName("param_checklist_id") val paramChecklistId: Int,
    @SerializedName("orden_ui") val ordenUi: Int,
    @SerializedName("es_obligatorio") val esObligatorio: Boolean
)