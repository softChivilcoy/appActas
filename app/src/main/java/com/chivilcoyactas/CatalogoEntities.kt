package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alcoholimetros")
data class AlcoholimetrosEntity(
    @PrimaryKey val id: Int,
    val marca: String? = "",
    val modelo: String? = "",
    val nroSerie: String? = "S/C",
    val codHomologacion: String? = "S/C"
)

@Entity(tableName = "local_tipos_faltas")
data class TipoFaltaEntity(
    @PrimaryKey val id: Int,
    val codigo: String,
    val descripcionCorta: String,
    val descripcionPlantilla: String? // 👈 Nuevo campo
)

@Entity(tableName = "local_tipos_vehiculos")
data class TipoVehiculoEntity(
    @PrimaryKey val id: Int,
    val nombre: String
)

@Entity(tableName = "local_tipo_marcas")
data class TipoMarcaEntity(
    @PrimaryKey val id: Int,
    val nombre: String
)

@Entity(tableName = "local_tipo_modelos")
data class TipoModeloEntity(
    @PrimaryKey val id: Int,
    val nombre: String,
    val marcaId: Int // Para filtrar por marca
)

@Entity(tableName = "local_tipo_provincias")
data class TipoProvinciaEntity(
    @PrimaryKey val id: Int,
    val nombre: String
)

@Entity(tableName = "local_tipo_localidades")
data class TipoLocalidadEntity(
    @PrimaryKey val id: Int,
    val nombre: String,
    val provinciaId: Int // Para filtrar por provincia
)

@Entity(tableName = "local_tipos_actas")
data class TipoActaEntity(
    @PrimaryKey val id: Int,
    val nombre: String
)

@Entity(tableName = "configuracion_quincenas")
data class QuincenaEntity(
    @PrimaryKey val id: Int,
    val diaInicio: Int,
    val diaFin: Int,
    val idJuzgado: Int
)

@Entity(tableName = "param_checklist_vehicular")
data class CheckVehicularEntity(
    @PrimaryKey val id: Int,
    val codigoClave: String,
    val etiquetaVisible: String,
    val tipoDato: String,
    val opcionesLista: String?,
    val ordenUi: Int,
    val sectorVehiculo: String
)
@Entity(
    tableName = "tipo_vehiculo_checklist",
    primaryKeys = ["tipoVehiculoId", "paramChecklistId"] // 👈 Clave compuesta
)
data class TipoVehiculoCheckEntity(
    val tipoVehiculoId: Int,
    val paramChecklistId: Int,
    val ordenUi: Int,
    val esObligatorio: Boolean
)