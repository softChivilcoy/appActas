package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_alcoholemia",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 🧪 Limpieza del blindaje legal
    )],
    indices = [Index("actaId")]
)
data class ActaAlcoholemiaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val resultadoAlcoholemia: Double,
    val marcaAlcoholimetro: String,
    val modeloAlcoholimetro: String,
    val nroSerieAlcoholimetro: String,
    val codAprobacionAlcoholimetro: String
)