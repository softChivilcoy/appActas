package com.chivilcoyactas.utils

import com.chivilcoyactas.Coordenada

object GeoUtils {

    /**
     * Evalúa si una coordenada GPS (latitud, longitud) se encuentra dentro
     * del polígono definido por la lista de coordenadas del Ejido Urbano.
     * Utiliza el algoritmo Ray-Casting.
     */
    fun estaDentroDelEjido(latitud: Double?, longitud: Double?, poligono: List<Coordenada>?): Boolean {
        // Validaciones de casos borde
        if (latitud == null || longitud == null) return false
        if (poligono.isNullOrEmpty() || poligono.size < 3) return false // Un polígono válido necesita al menos 3 vértices

        var dentro = false
        var j = poligono.size - 1

        for (i in poligono.indices) {
            val xi = poligono[i].lng
            val yi = poligono[i].lat
            val xj = poligono[j].lng
            val yj = poligono[j].lat

            // Algoritmo Ray-Casting
            val intersecta = ((yi > latitud) != (yj > latitud)) &&
                    (longitud < (xj - xi) * (latitud - yi) / (yj - yi) + xi)

            if (intersecta) dentro = !dentro
            j = i
        }

        return dentro
    }
}