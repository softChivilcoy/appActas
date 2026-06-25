package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_infractor",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("actaId")]
)
data class ActaInfractorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val nombreCompleto: String,
    val dni: String,
    val provincia: String,   // 👈 NUEVO
    val localidad: String,   // 👈 NUEVO
    val cp: String,          // 👈 NUEVO
    val calle: String,
    val altura: String,
    val niegaDatos: Int,

    // Solo Tránsito
    val nroLicencia: String? = null,

    // Solo Inspección
    val vinculoLugar: String? = null,
    val nombreResponsable: String? = null,
    val dniResponsable: String? = null,
    val calleResponsable: String? = null,
    val alturaResponsable: String? = null
)