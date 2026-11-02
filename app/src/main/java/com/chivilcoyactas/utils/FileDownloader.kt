package com.chivilcoyactas.utils

import android.content.Context
import android.util.Log
import com.chivilcoyactas.net.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileDownloader {

    private const val TAG = "FileDownloader"

    /**
     * Descarga un archivo desde una URL relativa o absoluta y lo guarda en el almacenamiento interno.
     * @param context Contexto de la aplicación.
     * @param urlRuta La ruta que viene del servidor (ej: "storage/actas/foto.png").
     * @param subDir Subdirectorio interno (ej: "actas", "firmas").
     * @return La ruta absoluta del archivo local guardado, o null si falló.
     */
    suspend fun descargarYGuardar(
        context: Context,
        urlRuta: String,
        subDir: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Limpiar la URL. Si empieza con storage/, nos aseguramos que Retrofit la maneje bien
            // Si RetrofitClient tiene una BASE_URL, podemos pasar solo la ruta relativa si descargarArchivo usa @Url
            val responseBody = RetrofitClient.apiService.descargarArchivo(urlRuta)

            // 2. Crear el directorio si no existe
            val directory = File(context.filesDir, subDir)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            // 3. Generar un nombre de archivo basado en la ruta remota para evitar duplicados locales
            val fileName = urlRuta.substringAfterLast("/")
            val localFile = File(directory, fileName)

            // 4. Escribir el stream al archivo
            val inputStream: InputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(localFile)

            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.d(TAG, "Archivo descargado exitosamente: ${localFile.absolutePath}")
            return@withContext localFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error descargando archivo $urlRuta: ${e.message}")
            return@withContext null
        }
    }
}
