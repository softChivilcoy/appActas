package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categorias_inspeccion")
data class CategoriaEntity(
    val nombre: String,           // Primero el que siempre vas a mandar
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)