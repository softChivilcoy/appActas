package com.chivilcoyactas

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chivilcoyactas.net.RetrofitClient

class CatalogoUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("WORKER_CATALOGO", "Iniciando actualización diaria de catálogos maestros...")

        return try {
            // 1. Le pegamos a la API de Laravel
            val response = RetrofitClient.apiService.obtenerCatalogosMaestros()

            if (response.isSuccessful && response.body()?.success == true) {
                val catalogos = response.body()!!
                Log.d("WORKER_CATALOGO", "Catálogos descargados con éxito. Mapeando a Room...")

                // 2. Instanciamos la base de datos local
                val db = AppDatabase.getDatabase(applicationContext)

                // 3. Mapeamos los DTOs de red a las Entities de Room
                val listaFaltas = catalogos.tiposFalta.map {
                    TipoFaltaEntity(id = it.id, codigo = it.codigoArticulo, descripcion = it.descripcion)
                }
                val listaVehiculos = catalogos.tiposVehiculo.map {
                    TipoVehiculoEntity(id = it.id, nombre = it.nombre)
                }
                val listaMarcas = catalogos.tipoMarcas.map {
                    TipoMarcaEntity(id = it.id, nombre = it.nombre)
                }
                val listaModelos = catalogos.tipoModelos.map {
                    TipoModeloEntity(id = it.id, nombre = it.nombre, marcaId = it.idMarca)
                }
                val listaProvincias = catalogos.tipoProvincias.map {
                    TipoProvinciaEntity(id = it.id, nombre = it.nombre)
                }
                val listaLocalidades = catalogos.tipoLocalidades.map {
                    TipoLocalidadEntity(id = it.id, nombre = it.nombre, provinciaId = it.idProvincia)
                }
                val listaActas = catalogos.tiposActa.map {
                    TipoActaEntity(id = it.id, nombre = it.nombre)
                }

                val listaQuincenas = catalogos.quincenas.map {
                    QuincenaEntity(
                        id = it.id,
                        diaInicio = it.diaInicio,
                        diaFin = it.diaFin,
                        idJuzgado = it.idJuzgado
                    )
                }

                // 4. Mandamos todo a Room bajo una transacción segura
                db.catalogoDao().actualizarCatalogoCompleto(
                    faltas = listaFaltas,
                    vehiculos = listaVehiculos,
                    marcas = listaMarcas,
                    modelos = listaModelos,
                    provincias = listaProvincias,
                    localidades = listaLocalidades,
                    actas = listaActas,
                    quincenas = listaQuincenas
                )

                Log.d("WORKER_CATALOGO", "¡Catálogos actualizados en Room de forma limpia y transparente!")
                Result.success()
            } else {
                Log.e("WORKER_CATALOGO", "Error en el servidor al traer catálogos: ${response.code()}")
                Result.retry() // Reintenta más tarde si es un problema del servidor
            }
        } catch (e: Exception) {
            Log.e("WORKER_CATALOGO", "Fallo de red o excepción al actualizar catálogos", e)
            Result.retry() // Si se cortó el internet, WorkManager lo vuelve a correr al recuperar señal
        }
    }
}