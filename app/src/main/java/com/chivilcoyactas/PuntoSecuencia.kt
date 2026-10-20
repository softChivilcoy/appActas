package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "punto_secuencia")
data class PuntoSecuenciaEntity(
    @PrimaryKey
    val androidId: String,
    val serie: String,
    val puntoCodigo: Int,
    val anio: Int,
    val ultimaSecuencia: Int
)