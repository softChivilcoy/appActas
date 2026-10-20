package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_bienes_retenidos",
    foreignKeys = [
        ForeignKey(
            entity = ActaEntity::class, // O tu entidad principal de Acta
            parentColumns = ["idLocal"],
            childColumns = ["actaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["actaId"])]
)
data class ActaBienRetenidoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actaId: Long,
    val tipoBienId: Int,
    val observacion: String? = null
)