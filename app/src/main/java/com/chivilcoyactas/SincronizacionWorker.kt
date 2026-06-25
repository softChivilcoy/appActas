package com.chivilcoyactas.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.chivilcoyactas.AppDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream


class SincronizacionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)

            // 1. Buscamos todas las actas que quedaron colgadas en PENDIENTE
            val actasPendientes = db.actaDao().obtenerActasPorEstado("PENDIENTE")

            if (actasPendientes.isEmpty()) {
                return@withContext Result.success()
            }

            Log.d("WORKER_SINCRO", "Se encontraron ${actasPendientes.size} actas pendientes. Iniciando subida...")

            for (acta in actasPendientes) {
                try {
                    val actaCompletaDb = db.actaDao().obtenerActaCompleta(acta.idLocal)

                    if (actaCompletaDb != null) {
                        // 2. Transformamos el árbol de Room al DTO (ya sin strings base64 pesados adentro)
                        val dtoEnviar = ActaMapeador.transformarAEnviarDto(actaCompletaDb)

                        // 3. Convertimos el objeto completo a un String JSON único
                        val jsonString = Gson().toJson(dtoEnviar)
                        val datosPart = jsonString.toRequestBody("text/plain".toMediaTypeOrNull())

                        // 4. Preparamos el array de archivos binarios optimizados para el Multipart
                        val listaMultipartFotos = mutableListOf<MultipartBody.Part>()

                        // 🕵️‍♂️ Usamos 'media' que es el nombre real de tu lista en la relación de Room
                        actaCompletaDb.media.forEachIndexed { index, mediaEntity ->
                            val archivoOriginal = java.io.File(mediaEntity.rutaArchivo)

                            if (archivoOriginal.exists()) {
                                // 📸 OPTIMIZACIÓN INTELIGENTE (~500KB) para dejar contento al jefe
                                val archivoOptimizado = optimizarImagen(applicationContext, archivoOriginal)

                                val requestFile = archivoOptimizado.asRequestBody("image/jpeg".toMediaTypeOrNull())

                                // "fotos[]" es la clave que tu Laravel va a recibir como array de archivos
                                val partFoto = MultipartBody.Part.createFormData("fotos[]", archivoOptimizado.name, requestFile)
                                listaMultipartFotos.add(partFoto)
                            } else {
                                Log.e("WORKER_SINCRO", "Archivo físico no encontrado en el teléfono: ${mediaEntity.rutaArchivo}")
                            }
                        }

                        // 5. Enviamos todo junto a Laravel mediante el nuevo método Multipart
                        val response = RetrofitClient.apiService.enviarActaMultipart(datosPart, listaMultipartFotos)

                        if (response.isSuccessful && response.body()?.success == true) {
                            Log.d("WORKER_SINCRO", "Acta local #${acta.idLocal} y sus imágenes binarias sincronizadas con éxito.")

                            // Eliminamos de Room la cabecera (y por CASCADE limpia las 11 tablas hijas automáticamente)
                            db.actaDao().eliminarActaLocalCompleta(acta.idLocal)
                            Log.d("WORKER_SINCRO", "Acta local #${acta.idLocal} y dependencias borradas de Room.")
                        } else {
                            Log.e("WORKER_SINCRO", "Servidor rechazó el acta #${acta.idLocal}. Código: ${response.code()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WORKER_SINCRO", "Fallo temporal al enviar acta #${acta.idLocal}: ${e.message}")
                }
            }

            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e("WORKER_SINCRO", "Error crítico en la sincronización: ${e.message}")
            return@withContext Result.retry()
        }
    }

    /**
     * 📸 MÉTODO DE OPTIMIZACIÓN DE IMAGEN
     * Toma la foto original capturada por la cámara, si es muy gigante la reescala un poco
     * a un tamaño óptimo para pantallas (Full HD) y la guarda con una compresión del 85% JPEG.
     * Retorna un archivo temporal liviano (~400KB - 500KB) de nitidez impecable.
     */
    private fun optimizarImagen(context: Context, archivoOriginal: File): File {
        val bitmapOriginal = BitmapFactory.decodeFile(archivoOriginal.absolutePath) ?: return archivoOriginal

        // 1. 🚀 SUBIMOS EL TOPE: Pasamos a 2560px (Resolución 2K). Retiene muchísima definición para hacer zoom.
        val maxDimension = 2560
        val width = bitmapOriginal.width
        val height = bitmapOriginal.height

        val (nuevoAncho, nuevoAlto) = if (width > height) {
            if (width > maxDimension) Pair(maxDimension, (height * maxDimension) / width) else Pair(width, height)
        } else {
            if (height > maxDimension) Pair((width * maxDimension) / height, maxDimension) else Pair(width, height)
        }

        val bitmapEscalado = Bitmap.createScaledBitmap(bitmapOriginal, nuevoAncho, nuevoAlto, true)

        // Guardamos la foto optimizada en la memoria caché temporal de la app
        val archivoTemporal = File(context.cacheDir, "opt_" + archivoOriginal.name)
        val outStream = FileOutputStream(archivoTemporal)

        // 🚀 CALIDAD PREMIUN (92%): Forzamos a que el archivo pese más (mínimo 350KB - 600KB)
        // eliminando toda compresión visible en los textos o patentes.
        bitmapEscalado.compress(Bitmap.CompressFormat.JPEG, 92, outStream)
        outStream.flush()
        outStream.close()

        // Limpieza estricta de memoria RAM
        if (bitmapOriginal != bitmapEscalado) bitmapOriginal.recycle()
        bitmapEscalado.recycle()

        return archivoTemporal
    }
}