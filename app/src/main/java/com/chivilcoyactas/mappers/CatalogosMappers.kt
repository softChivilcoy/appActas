package com.chivilcoyactas.mappers


import com.chivilcoyactas.Coordenada
import com.chivilcoyactas.EjidoUrbanoDto
import com.chivilcoyactas.EjidoUrbanoEntity

fun EjidoUrbanoDto.toEntity(): EjidoUrbanoEntity {
    return EjidoUrbanoEntity(
        id = 1,
        updatedAt = this.updatedAt,
        coordenadas = this.coordenadas?.map {
            Coordenada(lat = it.lat, lng = it.lng)
        } ?: emptyList()
    )
}