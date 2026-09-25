package com.chivilcoyactas

enum class TipoOrigenItem {
    ORDEN_SERVIDOR,
    BORRADOR_LOCAL
}

data class ItemHojaRuta(
    val id: Long,
    val titulo: String,         // "ORDEN #1024" o "BORRADOR #12"
    val calle: String,
    val altura: String,
    val motivo: String,
    val infractorNombre: String,
    val fecha: String,
    val estadoTexto: String,    // "PENDIENTE", "BORRADOR"
    val origen: TipoOrigenItem,
    val actaEntity: ActaEntity? = null // Conserva la entidad de Room si es borrador
)