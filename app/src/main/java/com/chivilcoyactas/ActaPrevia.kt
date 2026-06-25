package com.chivilcoyactas

data class ActaPrevia(
    val id: Int,
    val ordenNumero: String,
    val calle: String,
    val altura: String,
    val motivo: String,
    val infractorNombre: String = "",
    val estado: String = "PENDIENTE"
)