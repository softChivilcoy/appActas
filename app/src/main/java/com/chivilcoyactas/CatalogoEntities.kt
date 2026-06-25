package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_tipos_faltas")
data class TipoFaltaEntity(
    @PrimaryKey val id: Int,
    val codigo: String,
    val descripcion: String
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