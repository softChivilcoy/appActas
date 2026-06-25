package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_medidas_preventivas",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 🚨 Chau retenciones y observaciones asociadas
    )],
    indices = [Index("actaId")]
)
data class ActaMedidaPreventivaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val realizoAlcoholemia: Boolean = false,
    val retencionVehiculo: Boolean = false,
    val retencionLicencia: Boolean = false,
    val retencionAnimal: Boolean = false,
    val observacionesMedida: String? = null
)