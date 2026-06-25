package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_media",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 📸 Chau registros residuales de fotos
    )],
    indices = [Index("actaId")]
)
data class ActaMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val tipoMedia: String, // "FOTO_1", "FOTO_2", "FIRMA"
    val rutaArchivo: String
)