package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_comercio",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 🚀 Limpieza automática
    )],
    indices = [Index("actaId")]
)
data class ActaComercioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val nombreComercio: String,
    val nroHabilitacionMunicipal: String?,
    val rubroComercio: String
)