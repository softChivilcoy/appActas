package com.chivilcoyactas.net

import com.chivilcoyactas.db.ActaCompletaDb
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Función auxiliar para recuperar el Map<Int, String>
private fun deserializarInventario(jsonString: String?): Map<String, String> {
    if (jsonString.isNullOrEmpty()) return emptyMap()
    return try {
        val type = object : TypeToken<Map<String, String>>() {}.type
        Gson().fromJson(jsonString, type) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

object ActaMapeador {

    fun transformarAEnviarDto(dbData: ActaCompletaDb): ActaEnviarDto {
        val acta = dbData.cabecera

        // Si la entidad vehículo no es null en el objeto relacional de Room, es TRANSITO
        val tipoFormularioCalculado = if (dbData.vehiculo != null) "TRANSITO" else "INSPECCION"

        // 1. Mapeamos la Cabecera
        val cabeceraDto = CabeceraDto(
            idLocal = acta.idLocal,
            nroActa = acta.nroActa,
            tipoActa = acta.tipoActa,
            tipoFormulario = tipoFormularioCalculado, //
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
            idJuzgado = acta.idJuzgado,
            idReparticion = acta.idReparticion,
            detalleFalta = acta.detalleFalta,
            observaciones = acta.observaciones, // 🚀 Mapeado
            tipoInmueble = acta.procTipoInmueble, // 🚀 Mapeado (Categorías unidas por coma)
            serie = acta.serie,
            puntoEmisionId = acta.puntoEmisionId,
            anio = acta.anio,
            secuencia = acta.secuencia,
        )

        // 2. Mapeamos los Infractores / Personas Vinculadas
        val todosLosInfractores = dbData.infractores.map {
            InfractorDto(
                nombreCompleto = it.nombreCompleto,
                dni = it.dni,
                provincia = it.provincia,
                localidad = it.localidad,
                cp = it.cp,
                calle = it.calle,
                altura = it.altura,
                niegaDatos = it.niegaDatos,
                vinculoLugar = it.vinculoLugar,
                nroLicencia = it.nroLicencia, // 🚀 AGREGADO
                firmaPath = it.firmaPath,
                motivoNiega = it.motivoNiega
            )
        }

        // El primer infractor (o el único en Tránsito) se envía en el campo 'infractor' para compatibilidad
        val infractorPrincipal = todosLosInfractores.firstOrNull()

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
                cpOriginal = it.cpOriginal,
                firmaPath = it.firmaPath
            )
        }

        // 5. Rama Condicional: TRANSITO
        // Comparamos el enum de la UI o consultamos si existen datos de vehículo
        val esTransito = dbData.vehiculo != null || dbData.alcoholemia != null || dbData.bienesRetenidos.isNotEmpty()

        val datosTransitoDto = if (esTransito) {

            val vehiculoDto = dbData.vehiculo?.let {
                VehiculoDto(
                    dominioPatente = it.dominio,
                    // 🚀 Enviamos el ID como string si existe, para que Laravel lo resuelva con is_numeric
                    marca = it.idMarca?.toString() ?: it.marca,
                    modelo = it.idModelo?.toString() ?: it.modelo,
                    tipoVehiculo = it.idTipoVehiculo?.toString() ?: it.tipoVehiculo
                )
            }

            val alcoholemiaDto = dbData.alcoholemia?.let {
                AlcoholemiaDto(
                    resultadoAlcoholemia = it.resultadoAlcoholemia,
                    marcaAlcoholimetro = it.marcaAlcoholimetro,
                    modeloAlcoholimetro = it.modeloAlcoholimetro,
                    nroSerieAlcoholimetro = it.nroSerieAlcoholimetro,
                    codAprobacionAlcoholimetro = it.codAprobacionAlcoholimetro,
                    alcoholimetroId = it.alcoholimetroId
                )
            }

            // 1. Mapeamos la lista de Room directamente al DTO
            val itemsChecklist = dbData.secuestro.map { item ->
                SecuestroChecklistDto(
                    codigoClave = item.codigoClave,
                    valor = item.valor
                )
            }

            // 2. Mapeamos la lista de bienes retenidos a sus IDs correspondientes
            val listaBienesDto = dbData.bienesRetenidos.map { bien ->
                BienRetenidoDto(
                    tipoBienId = bien.tipoBienId,
                    observacion = bien.observacion
                )
            }.toMutableList()

            // 🚀 INYECCIÓN AUTOMÁTICA: Si hay retención de vehículo y no está en la lista, 
            // lo agregamos con ID 1 para que Laravel active la lógica de secuestro vehicular.
            if (dbData.medidasPreventivas?.retencionVehiculo == true && listaBienesDto.none { it.tipoBienId == 1 }) {
                listaBienesDto.add(BienRetenidoDto(tipoBienId = 1, observacion = "Vehículo retenido por acta"))
            }

            // 3. Si hay ítems o hay retención de vehículo, creamos el DTO de secuestro
            val secuestroDto = if (itemsChecklist.isNotEmpty() || dbData.medidasPreventivas?.retencionVehiculo == true) {
                SecuestroDto(items = itemsChecklist)
            } else null

            DatosTransitoDto(
                vehiculo = vehiculoDto,
                retencionLicencia = dbData.medidasPreventivas?.retencionLicencia ?: false,
                retencionVehiculo = dbData.medidasPreventivas?.retencionVehiculo ?: false,
                alcoholemia = alcoholemiaDto,
                secuestro = secuestroDto,
                bienesRetenidos = listaBienesDto
            )

            //secuestroInventario = dbData.secuestro?.inventarioSerializado,
        } else null

        // 6. Rama Condicional: INSPECCION
        val datosInspeccionDto = if (!esTransito) {
            dbData.procedimiento?.let { proc ->

                // 🔹 Si com.esVacio() es true, devuelve null y NO genera ComercioDto
                val comercioDto = dbData.comercio?.takeIf { !it.esVacio() }?.let { com ->
                    ComercioDto(
                        nombreComercio = com.nombreComercio,
                        nroHabilitacion = com.nroHabilitacionMunicipal,
                        rubro = com.rubroComercio
                    )
                }

                // 🔹 Si cat.esVacio() es true, devuelve null y NO genera CatastroDto
                val catastroDto = dbData.catastro?.takeIf { !it.esVacio() }?.let { cat ->
                    CatastroDto(
                        circ = cat.ctCirc, secc = cat.ctSecc, chNro = cat.ctChaqNro, chLet = cat.ctChaqLet,
                        quinNro = cat.ctQuinNro, quinLet = cat.ctQuinLet, fracNro = cat.ctFracNro, fracLetra = cat.ctFracLetra,
                        mzNro = cat.ctMzNro, mzLet = cat.ctMzLet, parcNro = cat.ctParcNro, parcLet = cat.ctParcLet,
                        subParc = cat.ctSubParc, uf = cat.ctUf,
                        partidaMunicipal = cat.partidaMunicipal
                    )
                }

                DatosInspeccionDto(
                    tipoInspeccion = proc.tipoInspeccion,
                    nroReferenciaActa = proc.nroReferenciaActa?.takeIf { it.isNotBlank() },
                    seProcedeA = proc.seProcedeA,
                    comercio = comercioDto,
                    catastro = catastroDto
                )
            }
        } else null

        // 7. 📸 Mapeamos las Fotos leyendo el archivo físico y pasándolo a Base64
        val fotosDto = dbData.media.map { mediaEntity ->
            val stringBase64 = convertirArchivoABase64(mediaEntity.rutaArchivo)
            FotoDto(
                tipoMedia = mediaEntity.tipoMedia,
                fotoBase64 = stringBase64
            )
        }


        return ActaEnviarDto(
            cabecera = cabeceraDto,
            infractor = infractorPrincipal,
            personasVinculadas = todosLosInfractores,
            faltas = faltasDto,
            testigos = testigosDto,
            datosTransito = datosTransitoDto,
            datosInspeccion = datosInspeccionDto,
            fotos = fotosDto
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