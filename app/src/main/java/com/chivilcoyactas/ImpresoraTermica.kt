package com.chivilcoyactas

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.annotation.RequiresPermission
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.OutputStream
import java.util.*

class ImpresoraTermica {
    private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var outputStream: OutputStream? = null
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun conectarYProbar(callback: (String) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            callback("El dispositivo no soporta Bluetooth")
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val printerDevice = pairedDevices?.find {
            val name = it.name ?: ""
            name.contains("Printer", true) || name.contains("POS", true) || name.contains("InnerPrinter", true)
        }

        if (printerDevice == null) {
            callback("No se encontró la impresora vinculada.")
            return
        }

        try {
            socket = printerDevice.createRfcommSocketToServiceRecord(PRINTER_UUID)
            socket?.connect()
            outputStream = socket?.outputStream

            imprimirTicketDePrueba()
            callback("Impresión enviada correctamente")

        } catch (e: Exception) {
            callback("Error: ${e.message}")
        }
    }

    private fun imprimirTicketDePrueba() {
        val os = outputStream ?: return
        try {
            val reset = byteArrayOf(0x1B, 0x40)
            val negritaOn = byteArrayOf(0x1B, 0x45, 0x01)
            val centro = byteArrayOf(0x1B, 0x61, 0x01)

            os.write(reset)
            os.write(centro)
            os.write(negritaOn)
            os.write("MUNICIPALIDAD DE CHIVILCOY\n".toByteArray())
            os.write("--- PRUEBA DE SISTEMA ---\n\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) 
            os.write("Terminal: 3nStar PTA0130\n".toByteArray())
            os.write("Estado: Conectado OK\n".toByteArray())
            os.write("Fecha: ${Date()}\n\n".toByteArray())
            os.write("##########################\n".toByteArray())
            os.write("\n\n\n\n".toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun imprimirActaReal(acta: ActaViewModel, callback: (String) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            callback("Por favor, activá el Bluetooth")
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val printerDevice = pairedDevices?.find {
            val name = it.name ?: ""
            name.contains("Printer", true) || name.contains("POS", true) || name.contains("InnerPrinter", true)
        }

        if (printerDevice == null) {
            callback("No se encontró la impresora vinculada.")
            return
        }

        try {
            socket = printerDevice.createRfcommSocketToServiceRecord(PRINTER_UUID)
            socket?.connect()
            Thread.sleep(200) 
            outputStream = socket?.outputStream
            val os = outputStream ?: throw Exception("No se pudo abrir el canal de datos")

            os.write(byteArrayOf(0x1B, 0x40)) // Reset

            // --- ENCABEZADO INSTITUCIONAL ---
            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrado
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita ON
            os.write("MUNICIPALIDAD DE CHIVILCOY\n".toByteArray())
            os.write("SECRETARIA DE SEGURIDAD\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Negrita OFF
            val reparticion = if (acta.reparticionActual.isNotBlank()) acta.reparticionActual else "Direccion de Transito"
            os.write("$reparticion\n".toByteArray())
            os.write("--------------------------------\n".toByteArray())

            // --- DATOS DEL ACTA ---
            os.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita ON
            os.write("NOTIFICACION DE INFRACCION\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Negrita OFF
            
            os.write("ACTA NRO: ".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
            os.write("${acta.nroActa}\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // OFF

            os.write("FECHA: ${acta.fecha} HORA: ${acta.hora}\n".toByteArray())
            
            // Dirección Híbrida
            val direccion = "${acta.calle} ${acta.altura ?: ""}".trim()
            os.write("LUGAR: $direccion\n".toByteArray())
            if (acta.ubReferencia.isNotBlank()) {
                os.write("REF: ${acta.ubReferencia}\n".toByteArray())
            }
            
            // Datos de Inspección (Comercio/Catastro)
            if (acta.tipoFormulario == TipoFormulario.INSPECCION) {
                if (acta.comNombreFantasia.isNotBlank()) {
                    os.write("COMERCIO: ${acta.comNombreFantasia}\n".toByteArray())
                }
                if (acta.catPartidaMunicipal.isNotBlank()) {
                    os.write("PARTIDA: ${acta.catPartidaMunicipal}\n".toByteArray())
                }
            }

            if (acta.ApellidoNombreInfractor.isNotBlank()) {
                val tituloSujeto = if (acta.tipoFormulario == TipoFormulario.TRANSITO) "INFRACTOR" else "RESPONSABLE"
                os.write("$tituloSujeto: ${acta.ApellidoNombreInfractor}\n".toByteArray())
                os.write("DNI/CUIL: ${acta.dniInfractor}\n".toByteArray())
            }
            
            if (acta.tipoFormulario == TipoFormulario.TRANSITO && acta.dominio.isNotBlank()) {
                os.write("VEHICULO: ${acta.tipoVehiculo} ${acta.marca} ${acta.modelo}\n".toByteArray())
                os.write("DOMINIO: ".toByteArray())
                os.write(byteArrayOf(0x1B, 0x21, 0x30)) // Texto doble alto y ancho
                os.write("${acta.dominio}\n".toByteArray())
                os.write(byteArrayOf(0x1B, 0x21, 0x00)) // Reset fuente
            }
            
            // Alcoholemia si existe
            if (acta.hacerTestAlcoholemia && acta.ftResultado > 0) {
                os.write("ALCOHOLEMIA: ${acta.ftResultado} g/l\n".toByteArray())
            }

            os.write("--------------------------------\n".toByteArray())

            // --- INFRACCIONES DETECTADAS ---
            os.write(byteArrayOf(0x1B, 0x45, 0x01))
            os.write("INFRACCIONES DETECTADAS:\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00))

            
            if (acta.listaFaltasSeleccionadas.isEmpty()) {
                os.write("- Sin especificar\n".toByteArray())
            } else {
                acta.listaFaltasSeleccionadas.forEach { infraccion ->
                    val texto = "${infraccion.codigo}: ${infraccion.nombre}"
                    val textoCorta = if (texto.length > 30) {
                        texto.substring(0, 27) + "..."
                    } else {
                        texto
                    }
                    os.write("- $textoCorta\n".toByteArray())
                }
            }
            os.write("--------------------------------\n".toByteArray())

            // --- TEXTO LEGAL ---
            os.write("Se notifica la infraccion a las\n".toByteArray())
            os.write("normas de transito vigentes en\n".toByteArray())
            os.write("el Partido de Chivilcoy.\n\n".toByteArray())

            // --- BLOQUE DE SEGURIDAD DIGITAL ---
            os.write("================================\n".toByteArray())
            os.write("     VALIDACION DE ACTA     \n".toByteArray())
            os.write("TOKEN: ${acta.codigoValidacion}\n".toByteArray())
            os.write("================================\n".toByteArray())
            os.write(" Esta notificacion constituye \n".toByteArray())
            os.write(" documento publico oficial. \n".toByteArray())
            os.write("--------------------------------\n".toByteArray())
            os.write(" CONSULTA Y PAGO VOLUNTARIO: \n".toByteArray())
            os.write(" chivilcoy.gob.ar/actas \n\n".toByteArray())


            // --- QR ---
            val urlConsulta = "https://chivilcoy.gob.ar/actas?cod=${acta.codigoValidacion}"
            val qrBitmap = generarQrBitmap(urlConsulta, 250)
            if (qrBitmap != null) {
                os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrado
                os.write(obtenerBytesDeBitmap(qrBitmap))
                os.write("\n".toByteArray())
            }

            // --- SELLO DIGITAL DEL INSPECTOR ---
            os.write("\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrado
            
            // 1. Prioridad: Firma capturada en el acta actual
            var imprimioFirma = false
            if (!acta.firmaInspectorPath.isNullOrEmpty()) {
                try {
                    val bitmapFirma = BitmapFactory.decodeFile(acta.firmaInspectorPath)
                    if (bitmapFirma != null) {
                        os.write(obtenerBytesDeBitmap(bitmapFirma))
                        os.write("\n".toByteArray())
                        imprimioFirma = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // 2. Si no hay firma capturada, intentar con la del perfil (Base64)
            if (!imprimioFirma) {
                val prefs = acta.getApplication<android.app.Application>().getSharedPreferences("SesionInspector", android.content.Context.MODE_PRIVATE)
                val firmaBase64 = prefs.getString("INSPECTOR_FIRMA_BASE64", null)
                if (!firmaBase64.isNullOrEmpty()) {
                    try {
                        val decodedBytes = android.util.Base64.decode(firmaBase64, android.util.Base64.DEFAULT)
                        val bitmapFirma = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmapFirma != null) {
                            os.write(obtenerBytesDeBitmap(bitmapFirma))
                            os.write("\n".toByteArray())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita ON
            os.write("FIRMADO DIGITALMENTE POR:\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Negrita OFF
            
            // Intentamos obtener el nombre del inspector desde la sesión
            val prefs = acta.getApplication<android.app.Application>().getSharedPreferences("SesionInspector", android.content.Context.MODE_PRIVATE)
            val nombreInspector = prefs.getString("INSPECTOR_NOMBRE", "Inspector Autorizado")
            val legajoInspector = prefs.getString("INSPECTOR_LEGAJO", "0")

            os.write("${nombreInspector?.uppercase()}\n".toByteArray())
            os.write("Legajo / DNI: $legajoInspector\n".toByteArray())
            os.write("Hash: ${acta.codigoValidacion}\n".toByteArray())


            // --- AVANCE FINAL ---
            os.write("\n\n\n\n\n".toByteArray())

            callback("Acta impresa con éxito")
        } catch (e: Exception) {
            callback("Error: ${e.message}")
        }
    }

    fun generarQrBitmap(texto: String, size: Int = 200): Bitmap? {
        return try {
            val bitMatrix = MultiFormatWriter().encode(texto, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun obtenerBytesDeBitmap(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        
        val bytesImagen = mutableListOf<Byte>()
        
        // Comando ESC/POS: GS v 0 m xL xH yL yH d1...dk
        bytesImagen.add(0x1D)
        bytesImagen.add(0x76)
        bytesImagen.add(0x30)
        bytesImagen.add(0x00)
        
        bytesImagen.add((widthBytes % 256).toByte())
        bytesImagen.add((widthBytes / 256).toByte())
        bytesImagen.add((height % 256).toByte())
        bytesImagen.add((height / 256).toByte())

        for (y in 0 until height) {
            for (x in 0 until widthBytes) {
                var b = 0
                for (bit in 0..7) {
                    val pixelX = x * 8 + bit
                    if (pixelX < width) {
                        val pixel = bitmap.getPixel(pixelX, y)
                        if (Color.red(pixel) < 128) { // Umbral de negro
                            b = b or (1 shl (7 - bit))
                        }
                    }
                }
                bytesImagen.add(b.toByte())
            }
        }
        return bytesImagen.toByteArray()
    }

    fun cerrar() {
        try {
            socket?.close()
        } catch (e: Exception) {}
    }
}
