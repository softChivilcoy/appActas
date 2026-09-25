package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_vehiculo",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 🚀 Limpieza automática
    )],
    indices = [Index("actaId")]
)
data class ActaVehiculoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val dominio: String,
    val marca: String,            // p. ej. "FORD" o "MARCA RARA TRICICLO"
    val idMarca: Int? = null,     // p. ej. 14 (o null si tipeó texto libre)
    val modelo: String,           // p. ej. "FOCUS" o "MODELO UNICO"
    val idModelo: Int? = null,    // p. ej. 102 (o null si tipeó texto libre)
    val tipoVehiculo: String,     // Moto, Auto, etc.
    val idTipoVehiculo: Int? = null,
    val color: String? = null
)