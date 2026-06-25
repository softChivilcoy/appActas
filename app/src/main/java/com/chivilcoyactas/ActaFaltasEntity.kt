package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_faltas",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 🚀 Limpieza automática
    )],
    indices = [Index("actaId")]
)
data class ActaFaltasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val codigoFalta: String,      // ID de la ordenanza
    val descripcion: String,
    val observaciones: String? = null
)