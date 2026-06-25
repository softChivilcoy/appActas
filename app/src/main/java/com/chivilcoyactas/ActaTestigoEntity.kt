package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_testigos",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 🚀 Limpieza automática
    )],
    indices = [Index("actaId")]
)
data class ActaTestigoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long, // Relación con la cabecera

    // 🛑 Datos cargados por el inspector en la calle
    val dniOriginal: String,
    val nombreOriginal: String,
    val domicilioOriginal: String,
    val provinciaOriginal: String,
    val localidadOriginal: String,
    val cpOriginal: String,

    // ⚖️ Espacio reservado para que el Juzgado verifique en la Web de Laravel
    val dniVerificado: String? = null,
    val nombreVerificado: String? = null,
    val domicilioVerificado: String? = null,
    val estadoVerificacion: String = "PENDIENTE" // PENDIENTE, VALIDO, ERRONEO
)