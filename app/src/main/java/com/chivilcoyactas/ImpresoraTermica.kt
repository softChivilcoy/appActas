package com.chivilcoyactas

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import java.io.OutputStream
import java.util.*
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

class ImpresoraTermica {
    private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var outputStream: OutputStream? = null
    private var socket: BluetoothSocket? = null

    fun conectarYProbar(callback: (String) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Buscamos en los dispositivos vinculados
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val printerDevice = pairedDevices?.find {
            it.name.contains("Printer", true) || it.name.contains("POS", true)
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
        } finally {
            // No cerramos el socket de inmediato para dar tiempo a que termine de imprimir
        }
    }

    private fun imprimirTicketDePrueba() {
        val os = outputStream ?: return

        // Comandos ESC/POS básicos
        val reset = byteArrayOf(0x1B, 0x40)
        val negritaOn = byteArrayOf(0x1B, 0x45, 0x01)
        val centro = byteArrayOf(0x1B, 0x61, 0x01)

        os.write(reset)
        os.write(centro)
        os.write(negritaOn)
        os.write("MUNICIPALIDAD DE CHIVILCOY\n".toByteArray())
        os.write("--- PRUEBA DE SISTEMA ---\n\n".toByteArray())
        os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Negrita off
        os.write("Terminal: 3nStar PTA0130\n".toByteArray())
        os.write("Estado: Conectado OK\n".toByteArray())
        os.write("Fecha: 27/04/2026\n\n".toByteArray())
        os.write("##########################\n".toByteArray())
        os.write("\n\n\n".toByteArray()) // Espacio para cortar
    }

    // Dentro de la clase ImpresoraTermica
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun imprimirActaReal(acta: ActaViewModel, callback: (String) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            callback("Por favor, activá el Bluetooth")
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val printerDevice = pairedDevices?.find {
            it.name.contains("Printer", true) || it.name.contains("POS", true)
        }

        if (printerDevice == null) {
            callback("No se encontró la impresora vinculada.")
            return
        }



        try {
            socket = printerDevice.createRfcommSocketToServiceRecord(PRINTER_UUID)
            socket?.connect()
            Thread.sleep(100) // Un respiro de 100ms para la placa del 3nStar
            outputStream = socket?.outputStream
            val os = outputStream ?: throw Exception("No se pudo abrir el canal de datos")

            os.write(byteArrayOf(0x1B, 0x40)) // Reset

            // --- ENCABEZADO INSTITUCIONAL ---
            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrado
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita ON
            os.write("MUNICIPALIDAD DE CHIVILCOY\n".toByteArray())
            os.write("SECRETARIA DE SEGURIDAD\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Negrita OFF
            os.write("Direccion de Transito\n".toByteArray())
            os.write("--------------------------------\n".toByteArray())

            // --- DATOS DEL ACTA (RESALTADOS) ---
            os.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda
            os.write("ACTA NRO: ".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
            os.write("${acta.nroActa}\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // OFF

            os.write("FECHA: ${acta.fecha} HORA: ${acta.hora}\n".toByteArray())
            os.write("DOMINIO: ${acta.dominio}\n".toByteArray())
            os.write("VEHICULO: ${acta.tipoVehiculo}\n".toByteArray())
            os.write("--------------------------------\n".toByteArray())

            // Resumen de Faltas
            /*os.write("INFRACCIONES DETECTADAS:\n".toByteArray())

            // Usamos ?.let para asegurarnos de que la lista no sea nula antes de recorrerla
            acta.listaFaltasSeleccionadas?.let { faltas ->
                if (faltas.isEmpty()) {
                    os.write("- Sin especificar\n".toByteArray())
                } else {
                    faltas.forEach { falta ->
                        // Verificamos que 'falta' no sea null y limpiamos espacios
                        val textoLimpio = falta?.trim() ?: "Falta s/n"
                        val textoCorta = if(textoLimpio.length > 30) {
                            textoLimpio.substring(0, 27) + "..."
                        } else {
                            textoLimpio
                        }
                        os.write("- $textoCorta\n".toByteArray())
                    }
                }
            } ?: os.write("- Sin faltas cargadas\n".toByteArray())*/

            // --- TEXTO LEGAL ---
            os.write("Se notifica la infraccion a las\n".toByteArray())
            os.write("normas de transito vigentes en\n".toByteArray())
            os.write("el Partido de Chivilcoy.\n\n".toByteArray())


            // --- BLOQUE DE SEGURIDAD DIGITAL ---
            os.write("================================\n".toByteArray())
            os.write("     CODIGO DE VERIFICACION     \n".toByteArray())
            os.write("===========  ${acta.codigoValidacion}  ===========\n".toByteArray())
            os.write("--------------------------------\n".toByteArray())
            os.write(" CONSULTA Y PAGO VOLUNTARIO: \n".toByteArray())
            os.write(" chivilcoy.gob.ar/actas \n\n".toByteArray())

            // --- 🚀 COMANDOS NATIVOS SIMPLIFICADOS PARA QR (COMPATIBLE CON 3nSTAR) ---
            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centramos en el papel

            val urlConsulta = "https://chivilcoy.gob.ar/actas?cod=${acta.codigoValidacion}"

            // 1. Creamos el QR en un bitmap chiquito y cuadrado (200x200 píxeles es ideal para 58mm)
            val qrBitmap = generarQrBitmap(urlConsulta, 200)

            if (qrBitmap != null) {
                // 2. Lo transformamos a los bytes gráficos que entiende Telpo
                val bytesQr = obtenerBytesDeBitmap(qrBitmap)
                // 3. Lo escupimos directo al stream
                os.write(bytesQr)
            } else {
                os.write("[Error al generar QR]\n".toByteArray())
            }

            os.write("\n".toByteArray()) // Un enter para despegar el QR de la firma

            // --- ESPACIO DE FIRMA MANUAL (Ahora sí va a salir) ---
            os.write("\n\n\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrado
            os.write("............................\n".toByteArray())
            os.write("    FIRMA DEL INSPECTOR     \n".toByteArray())

            // --- AVANCE FINAL DE PAPEL ---
            os.write("\n\n\n\n".toByteArray())

            callback("Acta impresa con éxito")
        } catch (e: Exception) {
            callback("Error: ${e.message}")
        }
    }

    private fun imprimirQR(datos: String) {
        val os = outputStream ?: return
        try {
            // En lugar de intentar dibujar píxeles que la impresora no entiende...
            // Creamos un recuadro de texto que sea muy fácil de leer

            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrado
            os.write("================================\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita ON
            os.write("      VERIFICACION ONLINE       \n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Negrita OFF
            os.write("================================\n".toByteArray())

            os.write("\nPara ver fotos y descargar el\n".toByteArray())
            os.write("comprobante oficial, ingrese a:\n\n".toByteArray())

            os.write(byteArrayOf(0x1B, 0x21, 0x01)) // Fuente B (más chiquita para que entre la URL)
            os.write("https://chivilcoy.gob.ar/actas\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x21, 0x00)) // Fuente normal

            os.write("\nE ingrese el Numero de Acta.\n".toByteArray())
            os.write("--------------------------------\n".toByteArray())

        } catch (e: Exception) {
            os.write("\nURL: $datos\n".toByteArray())
        }
    }

    private fun generarQrBitmap(texto: String): android.graphics.Bitmap {
        val size = 192 // 192 es múltiplo de 8 y de 32 bits. Ideal para impresoras de 58mm.
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            texto, com.google.zxing.BarcodeFormat.QR_CODE, size, size
        )
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    fun cerrar() {
        socket?.close()
    }

    fun generarQrBitmap(texto: String, tamaño: Int = 200): Bitmap? {
        try {
            val bitMatrix = MultiFormatWriter().encode(texto, BarcodeFormat.QR_CODE, tamaño, tamaño)
            val ancho = bitMatrix.width
            val alto = bitMatrix.height
            val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
            for (x in 0 until ancho) {
                for (y in 0 until alto) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun obtenerBytesDeBitmap(bitmap: Bitmap): ByteArray {
        val ancho = bitmap.width
        val alto = bitmap.height
        val anchoBytes = (ancho + 7) / 8
        val reresultado = ByteArray(alto * anchoBytes + 4)

        // Comando universal ESC/POS para imprimir gráficos de mapa de bits
        reresultado[0] = 0x1D
        reresultado[1] = 0x76
        reresultado[2] = 0x30
        reresultado[3] = 0x00

        // Dimensiones de la imagen en bytes
        val pL = (anchoBytes % 256).toByte()
        val pH = (anchoBytes / 256).toByte()
        val qL = (alto % 256).toByte()
        val qH = (alto / 256).toByte()

        val bytesImagen = ArrayList<Byte>()
        bytesImagen.add(0x1D)
        bytesImagen.add(0x76)
        bytesImagen.add(0x30)
        bytesImagen.add(0x00)
        bytesImagen.add(pL)
        bytesImagen.add(pH)
        bytesImagen.add(qL)
        bytesImagen.add(qH)

        for (y in 0 until alto) {
            for (x in 0 until anchoBytes) {
                var b = 0
                for (bit in 0..7) {
                    val pixelX = x * 8 + bit
                    if (pixelX < ancho) {
                        val pixel = bitmap.getPixel(pixelX, y)
                        if (Color.red(pixel) < 128) { // Si tiende a negro
                            b = b or (1 weights (7 - bit))
                        }
                    }
                }
                bytesImagen.add(b.toByte())
            }
        }
        return bytesImagen.toByteArray()
    }
    // Función de extensión simple para el desplazamiento de bits
    private infix fun Int.weights(bit: Int): Int = this shl bit
}