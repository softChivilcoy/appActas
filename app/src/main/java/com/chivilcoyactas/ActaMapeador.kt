package com.chivilcoyactas.net

import com.chivilcoyactas.db.ActaCompletaDb

object ActaMapeador {

    fun transformarAEnviarDto(dbData: ActaCompletaDb): ActaEnviarDto {
        val acta = dbData.cabecera

        // 1. Mapeamos la Cabecera
        val cabeceraDto = CabeceraDto(
            idLocal = acta.idLocal,
            tipoActa = acta.tipoActa,
            idInspector = acta.idInspector,
            fecha = acta.fecha,
            hora = acta.hora,
            latitud = acta.latitud,
            longitud = acta.longitud,
            idHojaRuta = acta.idHojaRuta,
            firmaInspectorBase64 = acta.firmaInspectorBase64,
            esOperativo = acta.esOperativo,
            ejidoUrbano = acta.ejidoUrbano,
            nombreCalle = acta.nombreCalle,
            alturaCalle = acta.alturaCalle,
            detallePiso = acta.detallePiso,
            detalleReferencia = acta.detalleReferencia,
            idJuzgado = acta.idJuzgado
        )

        // 2. Mapeamos el Infractor (Agregados: provincia, localidad, cp)
        val infractorDto = dbData.infractor?.let {
            InfractorDto(
                nombreCompleto = it.nombreCompleto,
                dni = it.dni,
                provincia = it.provincia,   // 🚀 AGREGADO
                localidad = it.localidad,   // 🚀 AGREGADO
                cp = it.cp,                 // 🚀 AGREGADO
                calle = it.calle,
                altura = it.altura,
                niegaDatos = it.niegaDatos,
                vinculoLugar = it.vinculoLugar
            )
        }

        // 3. Mapeamos las Faltas
        val faltasDto = dbData.faltas.map {
            FaltaDto(codigoArticulo = it.codigoFalta, descripcion = it.descripcion)
        }

        // 4. Mapeamos los Testigos (Agregado: provinciaOriginal)
        val testigosDto = dbData.testigos.map {
            TestigoDto(
                dniOriginal = it.dniOriginal,
                nombreOriginal = it.nombreOriginal,
                domicilioOriginal = it.domicilioOriginal,
                provinciaOriginal = it.provinciaOriginal, // 🚀 AGREGADO
                localidadOriginal = it.localidadOriginal,
                cpOriginal = it.cpOriginal
            )
        }

        // 5. Rama Condicional: TRANSITO
        val datosTransitoDto = if (acta.tipoActa == "TRANSITO") {
            val vehiculoDto = dbData.vehiculo?.let {
                VehiculoDto(
                    dominioPatente = it.dominio,
                    marca = it.marca,
                    modelo = it.modelo,
                    tipoVehiculo = it.tipoVehiculo
                )
            }

            val alcoholemiaDto = dbData.alcoholemia?.let {
                AlcoholemiaDto(
                    resultadoAlcoholemia = it.resultadoAlcoholemia,
                    marcaAlcoholimetro = it.marcaAlcoholimetro,
                    modeloAlcoholimetro = it.modeloAlcoholimetro,
                    nroSerieAlcoholimetro = it.nroSerieAlcoholimetro,
                    codAprobacionAlcoholimetro = it.codAprobacionAlcoholimetro
                )
            }

            DatosTransitoDto(
                vehiculo = vehiculoDto,
                retencionLicencia = dbData.medidasPreventivas?.retencionLicencia ?: false,
                retencionVehiculo = dbData.medidasPreventivas?.retencionVehiculo ?: false,
                secuestroInventario = dbData.secuestro?.inventarioSerializado,
                alcoholemia = alcoholemiaDto
            )
        } else null

        // 6. Rama Condicional: INSPECCION
        val datosInspeccionDto = if (acta.tipoActa != "TRANSITO") {
            dbData.procedimiento?.let { proc ->
                val comercioDto = dbData.comercio?.let { com ->
                    ComercioDto(
                        nombreComercio = com.nombreComercio,
                        nroHabilitacion = com.nroHabilitacionMunicipal,
                        rubro = com.rubroComercio
                    )
                }

                val catastroDto = dbData.catastro?.let { cat ->
                    CatastroDto(
                        circ = cat.ctCirc, secc = cat.ctSecc, chNro = cat.ctChaqNro, chLet = cat.ctChaqLet,
                        quinNro = cat.ctQuinNro, quinLet = cat.ctQuinLet, fracNro = cat.ctFracNro, fracLetra = cat.ctFracLetra,
                        mzNro = cat.ctMzNro, mzLet = cat.ctMzLet, parcNro = cat.ctParcNro, parcLet = cat.ctParcLet,
                        subParc = cat.ctSubParc, uf = cat.ctUf
                    )
                }

                DatosInspeccionDto(
                    tipoInspeccion = proc.tipoInspeccion,
                    tipoInmueble = proc.tipoInmueble,
                    nroReferenciaActa = proc.nroReferenciaActa,
                    seProcedeA = proc.seProcedeA,
                    comercio = comercioDto,
                    catastro = catastroDto
                )
            }
        } else null

        // 7. 📸 Mapeamos las Fotos leyendo el archivo físico y pasándolo a Base64
        /*val fotosDto = dbData.media.map { mediaEntity ->
            val stringBase64 = convertirArchivoABase64(mediaEntity.rutaArchivo)
            FotoDto(
                tipoMedia = mediaEntity.tipoMedia,
                fotoBase64 = stringBase64
            )
        }*/
        val fotosDto = emptyList<FotoDto>()


        return ActaEnviarDto(
            cabecera = cabeceraDto,
            infractor = infractorDto,
            faltas = faltasDto,
            testigos = testigosDto,
            datosTransito = datosTransitoDto,
            datosInspeccion = datosInspeccionDto,
            fotos = fotosDto // 👈 📸 Pasamos la lista al DTO
        )
    }

    private fun convertirArchivoABase64(rutaArchivo: String): String {
        return try {
            val archivo = java.io.File(rutaArchivo)
            if (archivo.exists()) {
                val bytes = archivo.readBytes()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else {
                ""
            }
        } catch (e: Exception) {
            android.util.Log.e("MAPEADOR_MEDIA", "Error al convertir foto a Base64: ${e.message}")
            ""
        }
    }
}