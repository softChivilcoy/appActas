package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ejido_urbano")
data class EjidoUrbanoEntity(
    @PrimaryKey val id: Int = 1,
    val updatedAt: String?,
    val coordenadas: List<Coordenada> // Usará TypeConverter para persistir en SQLite
)