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
        val db = AppDatabase.getDatabase(applicationContext)

        // 1. Identificamos si se pasó un ID de acta específico, o buscamos la primera pendiente
        var actaIdLocal = inputData.getLong("ACTA_ID_LOCAL", -1L)

        if (actaIdLocal == -1L) {
            val primeraPendiente = db.actaDao().obtenerActasPorEstado("PENDIENTE").firstOrNull()
            if (primeraPendiente == null) {
                Log.d("WORKER_SINCRO", "No hay actas pendientes de sincronizar.")
                return@withContext Result.success()
            }
            actaIdLocal = primeraPendiente.idLocal
        }

        Log.d("WORKER_SINCRO", "Iniciando sincronización atómica para el acta local #$actaIdLocal...")

        val archivosTemporales = mutableListOf<File>()

        try {
            val actaCompletaDb = db.actaDao().obtenerActaCompleta(actaIdLocal)

            if (actaCompletaDb == null) {
                Log.e("WORKER_SINCRO", "Acta #$actaIdLocal no encontrada en Room. Se omite.")
                return@withContext Result.failure()
            }

            // 2. Mapeo a DTO y serialización a JSON String
            val dtoEnviar = ActaMapeador.transformarAEnviarDto(actaCompletaDb)
            val jsonString = Gson().toJson(dtoEnviar)
            val datosPart = jsonString.toRequestBody("text/plain".toMediaTypeOrNull())

            // 3. Compresión y armado de Partes Multipart para Fotos de inspección/tránsito
            val listaMultipartFotos = mutableListOf<MultipartBody.Part>()

            actaCompletaDb.media.forEach { mediaEntity ->
                val archivoOriginal = File(mediaEntity.rutaArchivo)

                if (archivoOriginal.exists()) {
                    val archivoOptimizado = optimizarImagen(applicationContext, archivoOriginal)
                    archivosTemporales.add(archivoOptimizado)

                    val requestFile = archivoOptimizado.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val partFoto = MultipartBody.Part.createFormData("fotos[]", archivoOptimizado.name, requestFile)
                    listaMultipartFotos.add(partFoto)
                } else {
                    Log.w("WORKER_SINCRO", "Foto no hallada en almacenamiento local: ${mediaEntity.rutaArchivo}")
                }
            }

            // 🚀 3.1 Armado de las partes Multipart para las FIRMAS (PNG)
            val parteFirmaInfractor = crearPartFirma("firma_infractor", actaCompletaDb.infractor?.firmaPath)
            val parteFirmaTestigo = crearPartFirma("firma_testigo", actaCompletaDb.testigos.firstOrNull()?.firmaPath)

            // 4. Envío HTTP a Laravel mediante Retrofit
            val response = RetrofitClient.apiService.enviarActaMultipart(
                datosPart,
                listaMultipartFotos,
                parteFirmaInfractor,
                parteFirmaTestigo
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("WORKER_SINCRO", "✅ Acta #$actaIdLocal enviada con éxito a Laravel.")

                // Borramos de Room
                db.actaDao().eliminarActaLocalCompleta(actaIdLocal)
                Log.d("WORKER_SINCRO", "Acta local #$actaIdLocal eliminada de Room.")

                return@withContext Result.success()
            } else if (response.code() == 401) {
                // 🔒 Token vencido / Sesión inválida: No borramos nada de Room y abortamos el reintento
                Log.e("WORKER_SINCRO", "🔒 HTTP 401 Unauthorized. Token vencido o sesión cerrada.")
                return@withContext Result.failure()
            } else {
                Log.e("WORKER_SINCRO", "❌ Servidor rechazó el acta #$actaIdLocal. HTTP Status: ${response.code()}")
                // Reintentamos atómicamente esta acta mediante Exponential Backoff
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            Log.e("WORKER_SINCRO", "⚠️ Fallo de red/conexión al enviar acta #$actaIdLocal: ${e.message}")
            // Si hubo microcorte de 3G/4G o timeout, WorkManager reintentará automáticamente
            return@withContext Result.retry()

        } finally {
            // 5. Limpieza garantizada de archivos *.opt de la memoria Caché
            archivosTemporales.forEach { archivoTemp ->
                if (archivoTemp.exists()) {
                    archivoTemp.delete()
                }
            }
        }
    }

    /**
     * ✍️ Convierte la ruta de un archivo de firma PNG a MultipartBody.Part?
     */
    private fun crearPartFirma(nombreParametro: String, rutaLocal: String?): MultipartBody.Part? {
        if (rutaLocal.isNullOrEmpty()) return null

        val archivo = File(rutaLocal)
        if (!archivo.exists()) {
            Log.w("WORKER_SINCRO", "Firma no hallada en almacenamiento local: $rutaLocal")
            return null
        }

        val requestFile = archivo.asRequestBody("image/png".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(nombreParametro, archivo.name, requestFile)
    }

    /**
     * 📸 OPTIMIZACIÓN DE IMAGEN PREMIUM (2560px / Quality 92%)
     */
    private fun optimizarImagen(context: Context, archivoOriginal: File): File {
        val bitmapOriginal = BitmapFactory.decodeFile(archivoOriginal.absolutePath) ?: return archivoOriginal

        val maxDimension = 2560
        val width = bitmapOriginal.width
        val height = bitmapOriginal.height

        val (nuevoAncho, nuevoAlto) = if (width > height) {
            if (width > maxDimension) Pair(maxDimension, (height * maxDimension) / width) else Pair(width, height)
        } else {
            if (height > maxDimension) Pair((width * maxDimension) / height, maxDimension) else Pair(width, height)
        }

        val bitmapEscalado = Bitmap.createScaledBitmap(bitmapOriginal, nuevoAncho, nuevoAlto, true)

        val archivoTemporal = File(context.cacheDir, "opt_" + archivoOriginal.name)
        val outStream = FileOutputStream(archivoTemporal)

        bitmapEscalado.compress(Bitmap.CompressFormat.JPEG, 92, outStream)
        outStream.flush()
        outStream.close()

        if (bitmapOriginal != bitmapEscalado) bitmapOriginal.recycle()
        bitmapEscalado.recycle()

        return archivoTemporal
    }
}