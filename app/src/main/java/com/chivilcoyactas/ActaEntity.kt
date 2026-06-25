package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "actas")
data class ActaEntity(
    @PrimaryKey(autoGenerate = true) val idLocal: Long = 0,
    val idServer: Long? = null,              // ID que devuelve Laravel al sincronizar
    val tipoActa: String,                    // "TRANSITO" o "INSPECCION"
    val idInspector: Int,
    val fecha: String,                       // yyyy-MM-dd
    val hora: String,                        // HH:mm
    val latitud: Double,
    val longitud: Double,
    val estadoEnvio: String = "PENDIENTE",   // PENDIENTE, ENVIADO, ERROR
    val idHojaRuta: Int? = null,             // Solo para Inspección General
    val firmaInspectorBase64: String? = null,

    // 🚀 NUEVOS CAMPOS DE UBICACIÓN Y CONTEXTO MUNICIPAL
    val esOperativo: Boolean = false,        // True si pertenece a un operativo especial
    val ejidoUrbano: Int,                 // "Chivilcoy", "Moquehuá", "Ruta 5", etc.
    val nombreCalle: String,                 // Guardamos la calle limpia del autocomplete
    val alturaCalle: Int?,                   // Altura numérica (puede ser null si es una esquina/ruta)
    val detallePiso: String? = null,         // "Piso 2 Depto B" (más usado en inspección de comercios/obras)
    val detalleReferencia: String? = null,   // "Entre Pellegrini y Mitre" o "Frente a la escuela 1"
    val idJuzgado: Int? = null               // Juzgado de Faltas asignado (si se define en la app)
)