package com.chivilcoyactas

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentFirmaBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.gcacace.signaturepad.views.SignaturePad
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chivilcoyactas.net.SincronizacionWorker
import java.io.File
import java.util.Calendar
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.io.FileOutputStream

class FirmaFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentFirmaBinding? = null
    private val binding get() = _binding!!

    private val TIEMPO_PRESION = 2000L

    private var esFirmaTestigo = false
    private var personaActual: PersonaVinculada? = null
    private var testigoIndexActual: Int = 0
    private var claveVinculoActual: String? = null

    private val handlerLongPress = android.os.Handler(android.os.Looper.getMainLooper())
    private val runnableFinalizar = Runnable { finalizarProceso() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFirmaBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configuración inicial
        generarBotonesDinamicos()

        // 2. Botón volver de firma (desde el pad)
        binding.btnVolverDeFirma.setOnClickListener {
            binding.layoutModoFirma.visibility = View.GONE
            binding.layoutPreparacionFirma.visibility = View.VISIBLE
            generarBotonesDinamicos()
        }

        // 3. Botón Finalizar Acta (El que guarda todo el acta y sale)
        binding.btnIrAFirmar.setOnClickListener {
            if (binding.checkSeNiegaAFirmar.isChecked) {
                if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                    mostrarDialogoEstadoInspeccion()
                } else {
                    guardarYEnviarAlServidor("PENDIENTE")
                }
            } else {
                // Si no se niega, verificamos si hay al menos una firma o si es obligatorio
                val hayAlgunaFirma = !actaViewModel.Testigo1FirmaPath.isNullOrEmpty() || 
                                     !actaViewModel.Testigo2FirmaPath.isNullOrEmpty() || 
                                     !actaViewModel.firmaInfractorPath.isNullOrEmpty() ||
                                     actaViewModel.datosPersonasVinculadas.values.any { !it.firmaPath.isNullOrEmpty() }

                if (!hayAlgunaFirma) {
                    Toast.makeText(requireContext(), "Debe registrar al menos una firma o marcar que se niega", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                    mostrarDialogoEstadoInspeccion()
                } else {
                    guardarYEnviarAlServidor("PENDIENTE")
                }
            }
        }

        binding.checkSeNiegaAFirmar.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.btnIrAFirmar.text = "FINALIZAR ACTA (SE NIEGA)"
            } else {
                binding.btnIrAFirmar.text = "SIGUIENTE"
            }
        }

        // 4. Lógica del SignaturePad (Asegúrate que el ID coincida en el XML)
        binding.signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {}

            override fun onSigned() {
                // Cuando hay firma, mostramos el botón y lo habilitamos
                binding.btnFinalizarActa.visibility = View.VISIBLE
                habilitarBotonFinalizar(true)
            }

            override fun onClear() {
                // Si borran la firma, ocultamos el botón y cancelamos cualquier proceso de presión
                binding.btnFinalizarActa.visibility = View.GONE
                habilitarBotonFinalizar(false)
                handlerLongPress.removeCallbacks(runnableFinalizar)
            }
        })

        // Listener para el botón BORRAR
        binding.btnLimpiarFirma.setOnClickListener {
            binding.signaturePad.clear() // Limpia el trazo
            habilitarBotonFinalizar(false) // Deshabilita el botón de finalizar hasta que firme de nuevo
        }

        // 5. Botón Finalizar (Presión larga)
        // REVISIÓN: Asegúrate de que el ID en el XML sea @+id/btnFinalizarActa
        binding.btnFinalizarActa.setOnTouchListener { v, event ->
            if (binding.btnFinalizarActa.isEnabled) {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        handlerLongPress.postDelayed(runnableFinalizar, TIEMPO_PRESION)

                        // Feedback visual y táctil inicial
                        Toast.makeText(requireContext(), "Mantenga presionado para finalizar", Toast.LENGTH_SHORT).show()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        handlerLongPress.removeCallbacks(runnableFinalizar)
                    }
                }
            }
            true
        }

        // Bloqueo del botón físico "Atrás" para que el infractor no salga
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutModoFirma.visibility == View.VISIBLE) {
                    Toast.makeText(context, "El inspector debe retomar el equipo", Toast.LENGTH_SHORT).show()
                } else {
                    // CRUCIAL: Deshabilitamos este callback antes de navegar
                    // para que no cause el bucle infinito que rompe el SignaturePad
                    this.isEnabled = false
                    findNavController().navigateUp()
                }
            }
        })
    }

    private fun generarBotonesDinamicos() {
        binding.containerBotonesFirmas.removeAllViews()
        val context = requireContext()

        // 1. Botones para Testigos
        if (!actaViewModel.Testigo1Nombre.isNullOrBlank()) {
            agregarFilaFirma("Testigo: ${actaViewModel.Testigo1Nombre}", "TESTIGO_1", !actaViewModel.Testigo1FirmaPath.isNullOrEmpty())
        }
        if (!actaViewModel.Testigo2Nombre.isNullOrBlank()) {
            agregarFilaFirma("Testigo: ${actaViewModel.Testigo2Nombre}", "TESTIGO_2", !actaViewModel.Testigo2FirmaPath.isNullOrEmpty())
        }

        // 2. Botones para Vinculados / Infractores
        actaViewModel.listaInfractores.forEach { p ->
            val rol = p.tipoVinculo ?: "Infractor"
            val texto = "$rol: ${p.nombre}"
            val clave = if (p.dni.isNotBlank()) p.dni.replace(".", "").replace("-", "").trim() else p.nombre.trim().lowercase()
            agregarFilaFirma(texto, "VINCULO_$clave", !p.firmaPath.isNullOrEmpty())
        }
    }

    private fun agregarFilaFirma(texto: String, tag: String, yaFirmado: Boolean) {
        val layoutFila = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val btn = com.google.android.material.button.MaterialButton(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton).apply {
            this.text = if (yaFirmado) "✅ $texto" else texto
            this.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            this.textSize = 14f // Reducir un poco el tamaño para que quepa mejor con el check
            this.setPadding(32, 40, 32, 40)
            
            if (yaFirmado) {
                this.strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33BB66"))
                this.strokeWidth = 3
                setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")) // Verde clarito si ya firmó
                this.setTextColor(android.graphics.Color.parseColor("#33BB66"))
            } else {
                this.strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.LTGRAY)
                this.strokeWidth = 2
                setBackgroundColor(android.graphics.Color.WHITE)
                this.setTextColor(android.graphics.Color.BLACK)
            }
            this.isAllCaps = false
            
            this.setOnClickListener {
                procesarSeleccionFirma(tag)
            }
        }

        layoutFila.addView(btn)

        // Si ya está firmado, agregamos el botón de borrar
        if (yaFirmado) {
            val btnBorrar = android.widget.ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setColorFilter(android.graphics.Color.RED)
                setPadding(24, 24, 24, 24)
                setOnClickListener {
                    confirmarEliminarFirma(tag)
                }
            }
            layoutFila.addView(btnBorrar)
        }

        binding.containerBotonesFirmas.addView(layoutFila)
    }

    private fun confirmarEliminarFirma(tag: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Firma")
            .setMessage("¿Desea borrar la firma registrada?")
            .setPositiveButton("SÍ, BORRAR") { _, _ ->
                eliminarFirmaDeViewModel(tag)
                generarBotonesDinamicos()
                Toast.makeText(context, "Firma eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun eliminarFirmaDeViewModel(tag: String) {
        when {
            tag == "TESTIGO_1" -> {
                borrarArchivoFisico(actaViewModel.Testigo1FirmaPath)
                actaViewModel.Testigo1FirmaPath = null
            }
            tag == "TESTIGO_2" -> {
                borrarArchivoFisico(actaViewModel.Testigo2FirmaPath)
                actaViewModel.Testigo2FirmaPath = null
            }
            tag.startsWith("VINCULO_") -> {
                val identificador = tag.replace("VINCULO_", "")
                
                // 1. Limpiar en el principal si coincide
                val clavePrincipal = if (actaViewModel.dniInfractor.isNotBlank()) 
                    actaViewModel.dniInfractor.replace(".", "").replace("-", "").trim() 
                    else actaViewModel.ApellidoNombreInfractor.trim().lowercase()
                
                if (identificador == clavePrincipal) {
                    borrarArchivoFisico(actaViewModel.firmaInfractorPath)
                    actaViewModel.firmaInfractorPath = null
                }
                
                // 2. Limpiar en el mapa de vinculados
                actaViewModel.datosPersonasVinculadas.forEach { (_, p) ->
                    val clavePv = if (p.dni.isNotBlank()) p.dni.replace(".", "").replace("-", "").trim() else p.nombre.trim().lowercase()
                    if (clavePv == identificador) {
                        borrarArchivoFisico(p.firmaPath)
                        p.firmaPath = null
                    }
                }
            }
        }
    }

    private fun borrarArchivoFisico(path: String?) {
        if (path.isNullOrEmpty()) return
        try {
            val file = java.io.File(path)
            if (file.exists()) {
                val eliminado = file.delete()
                android.util.Log.d("FIRMA_DELETE", "Archivo físico eliminado: $path -> $eliminado")
            }
        } catch (e: Exception) {
            android.util.Log.e("FIRMA_DELETE", "Error al borrar archivo físico: ${e.message}")
        }
    }

    private fun procesarSeleccionFirma(tag: String) {
        when {
            tag.startsWith("TESTIGO_") -> {
                esFirmaTestigo = true
                testigoIndexActual = tag.replace("TESTIGO_", "").toInt()
                personaActual = null
                claveVinculoActual = tag

                val nombre = if (testigoIndexActual == 1) actaViewModel.Testigo1Nombre else actaViewModel.Testigo2Nombre

                if (!nombre.isNullOrBlank()) {
                    prepararModoFirma(true)
                }
            }
            tag.startsWith("VINCULO_") -> {
                esFirmaTestigo = false
                val identificador = tag.replace("VINCULO_", "")
                claveVinculoActual = identificador
                
                // Buscamos en la lista procesada para tener el objeto visual
                personaActual = actaViewModel.listaInfractores.find {
                    val clave = if (it.dni.isNotBlank()) it.dni.replace(".", "").replace("-", "").trim() else it.nombre.trim().lowercase()
                    clave == identificador
                }
                
                testigoIndexActual = 0
                prepararModoFirma(false)
            }
        }
    }

    private fun prepararModoFirma(esTestigo: Boolean) {
        val titulo = if (esTestigo) "FIRMA DE TESTIGO" else "FIRMA DE VÍNCULO"
        val nombre = if (esTestigo) {
            if (testigoIndexActual == 1) actaViewModel.Testigo1Nombre else actaViewModel.Testigo2Nombre
        } else {
            personaActual?.nombre ?: "Desconocido"
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val fechaHoy = sdf.format(Date())

        val resumen = """
        $titulo
        NOMBRE: $nombre
        FECHA: $fechaHoy
    """.trimIndent()

        binding.tvResumenActa.text = resumen
        binding.layoutPreparacionFirma.visibility = View.GONE
        binding.layoutModoFirma.visibility = View.VISIBLE
        binding.signaturePad.clear()
        habilitarBotonFinalizar(false)
    }

    private fun finalizarProceso() {
        if (binding.signaturePad.isEmpty) {
            Toast.makeText(context, "Falta la firma obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Guardar firma física
        val bitmapFirma = binding.signaturePad.transparentSignatureBitmap
        val identificadorFirma = if (esFirmaTestigo) "testigo_$testigoIndexActual" else "vinculo_$claveVinculoActual"
        val ruta = guardarFirmaEnDisco(bitmapFirma, identificadorFirma)

        // 2. Persistir en ViewModel (Borrando la anterior si existía para evitar huérfanos)
        if (esFirmaTestigo) {
            if (testigoIndexActual == 1) {
                borrarArchivoFisico(actaViewModel.Testigo1FirmaPath)
                actaViewModel.Testigo1FirmaPath = ruta
            } else {
                borrarArchivoFisico(actaViewModel.Testigo2FirmaPath)
                actaViewModel.Testigo2FirmaPath = ruta
            }
        } else {
            val claveAComparar = claveVinculoActual ?: ""

            // Actualizar Infractor Principal si coincide
            val clavePrincipal = if (actaViewModel.dniInfractor.isNotBlank()) 
                actaViewModel.dniInfractor.replace(".", "").replace("-", "").trim() 
                else actaViewModel.ApellidoNombreInfractor.trim().lowercase()

            if (claveAComparar == clavePrincipal) {
                borrarArchivoFisico(actaViewModel.firmaInfractorPath)
                actaViewModel.firmaInfractorPath = ruta
            }

            // Actualizar en el mapa de vinculados (IMPORTANTE: Recorrer para actualizar el objeto real)
            actaViewModel.datosPersonasVinculadas.forEach { (rol, p) ->
                val clavePv = if (p.dni.isNotBlank()) p.dni.replace(".", "").replace("-", "").trim() else p.nombre.trim().lowercase()
                if (clavePv == claveAComparar) {
                    borrarArchivoFisico(p.firmaPath)
                    p.firmaPath = ruta
                }
            }
        }

        // 2. Volver a la pantalla de selección
        binding.layoutModoFirma.visibility = View.GONE
        binding.layoutPreparacionFirma.visibility = View.VISIBLE
        
        generarBotonesDinamicos() // Refrescar estados de botones
        Toast.makeText(context, "Firma guardada correctamente", Toast.LENGTH_SHORT).show()
    }

    private fun habilitarBotonFinalizar(estado: Boolean) {
        binding.btnFinalizarActa.isEnabled = estado
        binding.btnFinalizarActa.alpha = if (estado) 1.0f else 0.5f
    }

    private fun mostrarDialogoEstadoInspeccion() {
        val opciones = arrayOf("Finalizar y enviar al Juzgado", "Guardar como Pendiente (Borrador)")

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Estado del Acta")
            .setCancelable(false) // Obligamos a elegir una opción
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> { // Finalizar
                        confirmarFinalizacionTotal()
                    }
                    1 -> { // Pendiente
                        guardarYEnviarAlServidor("BORRADOR")
                    }
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun confirmarFinalizacionTotal() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Confirmar envío al Juzgado?")
            .setMessage("Una vez finalizada, no podrá editar el acta desde la hoja de ruta.")
            .setPositiveButton("SÍ, FINALIZAR") { _, _ ->
                guardarYEnviarAlServidor("PENDIENTE")
            }
            .setNegativeButton("VOLVER", null)
            .show()
    }

    suspend fun obtenerYSumaSecuencia(dao: PuntoSecuenciaDao, androidId: String, puntoCodigo: Int): PuntoSecuenciaEntity {
        val anioActual = java.time.Year.now().value
        var estado = dao.obtenerSecuenciaActual(androidId, anioActual)

        if (estado == null) {
            // Si no existe secuencia local para este año/dispositivo, inicializamos en 0
            estado = PuntoSecuenciaEntity(
                androidId = androidId,
                serie = "E",
                puntoCodigo = puntoCodigo,
                anio = anioActual,
                ultimaSecuencia = 0
            )
            dao.insertarOActualizar(estado)
        }

        if (estado.anio != anioActual) {
            // Cambio de año -> reinicia secuencia a 0
            estado = estado.copy(anio = anioActual, ultimaSecuencia = 0)
            dao.insertarOActualizar(estado)
        }

        // Incrementar en 1 la secuencia
        val nuevaSecuencia = estado.ultimaSecuencia + 1
        dao.actualizarSecuencia(
            androidId = androidId,
            anio = anioActual,
            nuevaSecuencia = nuevaSecuencia
        )

        return estado.copy(ultimaSecuencia = nuevaSecuencia)
    }


    object CalculadorActaHelper {

        /**
         * Replica la función PostgreSQL public.fn_calcular_dv_acta
         */
        fun calcularDigitoVerificador(puntoCodigo: Int, anio: Int, secuencia: Int): String {
            // 1. Unificar a 16 caracteres con ceros a la izquierda
            val puntoStr = puntoCodigo.toString().padStart(4, '0')
            val anioStr = anio.toString().padStart(4, '0')
            val secuenciaStr = secuencia.toString().padStart(8, '0')

            val cadena = "$puntoStr$anioStr$secuenciaStr"

            var factor = 2
            var suma = 0

            // 2. Multiplicación ponderada de derecha a izquierda (ciclo 2..7)
            for (i in cadena.length - 1 downTo 0) {
                val digito = cadena[i].digitToInt()
                suma += digito * factor
                factor++
                if (factor > 7) {
                    factor = 2
                }
            }

            // 3. Aplicar Módulo 11
            val resto = suma % 11
            val dv = 11 - resto

            // 4. Casos límite
            return when (dv) {
                11, 10 -> "0"
                else -> dv.toString()
            }
        }

        /**
         * Conformar el identificador visible de 22 caracteres:
         * [SERIE]-[PUNTO_4]-[ANIO_4]-[SECUENCIA_8]-[DV]
         */
        fun generarNroActa(serie: String, puntoCodigo: Int, anio: Int, secuencia: Int): String {
            val dv = calcularDigitoVerificador(puntoCodigo, anio, secuencia)

            val serieFormatted = serie.trim().uppercase()
            val puntoFormatted = puntoCodigo.toString().padStart(4, '0')
            val anioFormatted = anio.toString().padStart(4, '0')
            val secuenciaFormatted = secuencia.toString().padStart(8, '0')

            return "$serieFormatted-$puntoFormatted-$anioFormatted-$secuenciaFormatted-$dv"
        }
    }

    private fun guardarYEnviarAlServidor(estadoActa: String) {
        val loadingDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setMessage(if (estadoActa == "BORRADOR") "Guardando borrador..." else "Guardando acta...")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val firmaBase64 = null
                val db = AppDatabase.getDatabase(requireContext())

                // 1. Obtenemos fecha y hora
                val ahora = Date()
                actaViewModel.fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(ahora)
                actaViewModel.hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora)

                val calendar = Calendar.getInstance().apply { time = ahora }
                val diaDelMes = calendar.get(Calendar.DAY_OF_MONTH)

                // Consulta offline del juzgado
                val juzgadoAsignado = db.catalogoDao().obtenerJuzgadoPorDia(diaDelMes) ?: 1

                // Inspector y Punto de Emisión desde SharedPreferences
                val prefs = requireContext().getSharedPreferences("SesionInspector", Context.MODE_PRIVATE)
                val idInspectorLogueado = prefs.getInt("INSPECTOR_ID", -1)
                val puntoEmisionIdLogueado = prefs.getInt("PUNTO_EMISION_ID", 0)
                val puntoCodigoLogueado = prefs.getInt("PUNTO_EMISION_CODIGO", 0)
                val androidIdLogueado = prefs.getString("DEVICE_ID", "") ?: ""

                // 🚀 OBTENER REPARTICIÓN SELECCIONADA
                val idReparticionSeleccionada = prefs.getInt("REPARTICION_ID", -1)

                if (idReparticionSeleccionada == -1) {
                    throw IllegalStateException("No hay una repartición seleccionada en la sesión")
                }

                val idTipoActa = actaViewModel.idTipoActaSeleccionada
                    ?: throw IllegalStateException("No se seleccionó un tipo de acta válido")


                val observacionesFalta= actaViewModel.ftObservaciones.trim().ifEmpty { null }

                // GESTIÓN DE SECUENCIA Y SERIE
                var serieActa = "E"
                var anioActa = calendar.get(Calendar.YEAR)
                var secuenciaActa = 0

                // Si ya tenemos una secuencia positiva en el ViewModel, la usamos (es un borrador con secuencia)
                if (actaViewModel.secuenciaActual > 0) {
                    serieActa = actaViewModel.serie.ifEmpty { "E" }
                    anioActa = if (actaViewModel.anio > 0) actaViewModel.anio else calendar.get(Calendar.YEAR)
                    secuenciaActa = actaViewModel.secuenciaActual
                } else {
                    // Si es 0 o menor, necesitamos generar una nueva sí o sí para evitar el error 23514 en el servidor
                    val secuenciaActualizada = obtenerYSumaSecuencia(
                        dao = db.puntoSecuenciaDao(),
                        androidId = androidIdLogueado,
                        puntoCodigo = puntoCodigoLogueado
                    )
                    serieActa = secuenciaActualizada.serie
                    anioActa = secuenciaActualizada.anio
                    secuenciaActa = secuenciaActualizada.ultimaSecuencia

                    // Actualizamos el ViewModel para que los DTOs que se generen después tengan la secuencia correcta
                    actaViewModel.secuenciaActual = secuenciaActa
                    actaViewModel.serie = serieActa
                    actaViewModel.anio = anioActa
                }

                // 🚀 Generar el número de acta con la secuencia actualizada y punto de emisión dinámico
                val nroActaGenerado = CalculadorActaHelper.generarNroActa(
                    serie = serieActa,
                    puntoCodigo = puntoCodigoLogueado,
                    anio = anioActa,
                    secuencia = secuenciaActa
                )

                actaViewModel.nroActa = nroActaGenerado
                actaViewModel.puntoEmisionId = puntoEmisionIdLogueado

                // 1. Guardamos Cabecera General
                val nuevaActa = ActaEntity(
                    // 🚀 AHORA SÍ: Si ya veníamos editando un borrador (idActaLocal > 0), mantenemos ese ID.
                    // Si idActaLocal es 0 (acta totalmente nueva), SQLite le generará un ID nuevo.
                    idLocal = if (actaViewModel.idActaLocal > 0) actaViewModel.idActaLocal else 0,
                    nroActa = nroActaGenerado,
                    tipoActa = idTipoActa,
                    idInspector = idInspectorLogueado,
                    fecha = actaViewModel.fecha,
                    hora = actaViewModel.hora,
                    latitud = actaViewModel.latitud,
                    longitud = actaViewModel.longitud,
                    estadoEnvio = estadoActa,
                    firmaInspectorBase64 = firmaBase64,
                    esOperativo = actaViewModel.esOperativo,
                    ejidoUrbano = actaViewModel.ejidoUrbano,
                    nombreCalle = actaViewModel.calle,
                    alturaCalle = actaViewModel.altura,
                    detallePiso = actaViewModel.ubDepto,
                    detalleReferencia = actaViewModel.ubReferencia,
                    idJuzgado = juzgadoAsignado,
                    idReparticion = idReparticionSeleccionada,
                    detalleFalta = observacionesFalta,
                    observaciones = actaViewModel.observaciones.trim().ifEmpty { null }, // 🚀 Persistir observaciones
                    procTipoInmueble = actaViewModel.procTipoInmueble.trim().ifEmpty { null }, // 🚀 Persistir tipo inmueble
                    serie = serieActa,
                    puntoEmisionId = puntoEmisionIdLogueado,
                    anio = anioActa,
                    secuencia = secuenciaActa
                )

                val idLocal = db.actaDao().insertarCabecera(nuevaActa)
                actaViewModel.idActaLocal = idLocal

                // 🚀 LIMPIEZA PREVIA: Si estamos re-guardando un borrador, eliminamos datos viejos de tablas relacionadas
                // para evitar duplicados por IDs autogenerados distintos.
                db.actaDao().eliminarInfractoresPorActa(idLocal)
                db.actaDao().eliminarFaltasPorActa(idLocal)
                db.actaDao().eliminarVehiculoPorActa(idLocal)
                db.actaDao().eliminarAlcoholemiaPorActa(idLocal)
                db.actaDao().eliminarMedidasPreventivasPorActa(idLocal)
                db.actaDao().eliminarBienesRetenidosPorActa(idLocal)
                db.actaDao().eliminarSecuestrosPorActa(idLocal)
                db.actaDao().eliminarTestigosPorActa(idLocal)
                db.actaDao().eliminarProcedimientoPorActa(idLocal)
                db.actaDao().eliminarComercioPorActa(idLocal)
                db.actaDao().eliminarCatastroPorActa(idLocal)

                // 2. Datos de los Infractores / Personas Vinculadas (UNIFICADO)
                val listaInfractoresParaGuardar = mutableListOf<ActaInfractorEntity>()

                if (actaViewModel.niegaDatos == 0) {
                    // Usamos la lista priorizada y deduplicada del ViewModel
                    actaViewModel.listaInfractores.forEach { p ->
                        listaInfractoresParaGuardar.add(
                            ActaInfractorEntity(
                                actaId = idLocal,
                                nombreCompleto = p.nombre,
                                dni = p.dni,
                                provincia = p.provincia,
                                localidad = p.localidad,
                                cp = p.cp,
                                calle = p.calle,
                                altura = p.altura,
                                niegaDatos = 0,
                                tieneLicencia = if (p.tieneLicencia) 1 else 0,
                                nroLicencia = p.nroLicencia,
                                vinculoLugar = p.tipoVinculo ?: "Infractor",
                                firmaPath = p.firmaPath
                            )
                        )
                    }
                } else {
                    // Caso: Niega datos (1), No se encuentra (2)
                    listaInfractoresParaGuardar.add(
                        ActaInfractorEntity(
                            actaId = idLocal,
                            nombreCompleto = "",
                            dni = "",
                            provincia = "BUENOS AIRES",
                            localidad = "CHIVILCOY",
                            cp = "6620",
                            calle = "",
                            altura = "",
                            niegaDatos = if (actaViewModel.niegaDatos != -1) actaViewModel.niegaDatos else 1,
                            vinculoLugar = if (actaViewModel.tipoFormulario == TipoFormulario.TRANSITO) "INFRACTOR" else "DESCONOCIDO",
                            motivoNiega = actaViewModel.motivoDatos.trim().ifEmpty { null }
                        )
                    )
                }

                db.actaDao().insertarInfractores(listaInfractoresParaGuardar)

                // 3. Faltas Seleccionadas


                val listaFaltas = actaViewModel.listaFaltasSeleccionadas.map {
                    ActaFaltasEntity(
                        actaId = idLocal,
                        codigoFalta = it.codigo,
                        descripcion = it.nombre
                        //observaciones = observacionesTexto
                    )
                }
                db.actaDao().insertarFaltas(listaFaltas)

                // 🚀 BIFURCACIÓN DE CIRCUITOS POR ENUM
                when (actaViewModel.tipoFormulario) {

                    TipoFormulario.TRANSITO -> {
                        // -------------------------------------------------------------
                        // CIRCUITO TRÁNSITO: Vehículo, Alcoholemia, Secuestro
                        // -------------------------------------------------------------
                        val datosVehiculo = ActaVehiculoEntity(
                            actaId = idLocal,
                            dominio = actaViewModel.dominio,

                            // Nombres en texto siempre presentes
                            tipoVehiculo = actaViewModel.tipoVehiculo,
                            marca = actaViewModel.marca,
                            modelo = actaViewModel.modelo,

                            // IDs opcionales (serán null si el usuario escribió texto libre)
                            idTipoVehiculo = actaViewModel.idTipoVehiculoSeleccionado,
                            idMarca = actaViewModel.idMarcaSeleccionada,
                            idModelo = actaViewModel.idModeloSeleccionado
                        )

                        db.actaDao().insertarVehiculo(datosVehiculo)

                        if (actaViewModel.hacerTestAlcoholemia) {
                            val alcoholemia = ActaAlcoholemiaEntity(
                                actaId = idLocal,
                                resultadoAlcoholemia = actaViewModel.ftResultado, // Puede ser 0.0 sin problema
                                marcaAlcoholimetro = actaViewModel.alcoMarca.takeIf { !it.isNullOrBlank() } ?: "Sin Marca",
                                modeloAlcoholimetro = actaViewModel.alcoModelo.takeIf { !it.isNullOrBlank() } ?: "S/M",
                                nroSerieAlcoholimetro = actaViewModel.alcoSerie.takeIf { !it.isNullOrBlank() } ?: "S/N",
                                codAprobacionAlcoholimetro = actaViewModel.alcoAprobacion.takeIf { !it.isNullOrBlank() } ?: "S/C",
                                alcoholimetroId = actaViewModel.alcoholimetroId
                            )
                            db.actaDao().insertarAlcoholemia(alcoholemia)
                        }

                        // 2. Guardar Medida Preventiva base
                        val medidasPreventivas = ActaMedidaPreventivaEntity(
                            actaId = idLocal,
                            realizoAlcoholemia = actaViewModel.hacerTestAlcoholemia ?: false,
                            retencionVehiculo = actaViewModel.retencionVehiculo,
                            retencionLicencia = actaViewModel.retencionLicencia
                        )
                        db.actaDao().insertarMedidasPreventivas(medidasPreventivas)

                        // 3. Guardar dinámicamente los Bienes Retenidos seleccionados
                        val bienesAInsertar = actaViewModel.bienesSeleccionadosIds.map { bienId ->
                            ActaBienRetenidoEntity(
                                actaId = idLocal,
                                tipoBienId = bienId,
                                observacion = actaViewModel.bienesDescripciones[bienId] // 🚀 Asigna la observación cargada
                            )
                        }

                        if (bienesAInsertar.isNotEmpty()) {
                            db.actaDao().insertarBienesRetenidos(bienesAInsertar)
                        }

                        // 4. Guardar inventario de secuestro dinámico
                        val listaSecuestro = actaViewModel.inventarioDinamico.map { (clave, valor) ->
                            ActaSecuestroEntity(
                                actaId = idLocal,
                                codigoClave = clave,
                                valor = valor
                            )
                        }
                        if (listaSecuestro.isNotEmpty()) {
                            db.actaDao().insertarSecuestro(listaSecuestro)
                        }
                        
                    }

                    TipoFormulario.INSPECCION -> {
                        // -------------------------------------------------------------
                        // CIRCUITO INSPECCIÓN: Procedimiento, Comercio, Catastro
                        // -------------------------------------------------------------

                        // 1. Procedimiento
                        val procedimiento = ActaProcedimientoEntity(
                            actaId = idLocal,
                            tipoInspeccion = actaViewModel.procAccion ?: "Inspección Gral",
                            nroReferenciaActa = actaViewModel.procRefActa,
                            seProcedeA = ""
                        )
                        db.actaDao().insertarProcedimiento(procedimiento)

                        // 2. Datos de Comercio (si aplica)
                        val nombreComer = actaViewModel.comNombreFantasia?.trim() ?: ""
                        val rubroComer = actaViewModel.comRubro?.trim() ?: ""

                        if (nombreComer.isNotEmpty() || rubroComer.isNotEmpty()) {
                            val datosComercio = ActaComercioEntity(
                                actaId = idLocal,
                                nombreComercio = nombreComer,
                                nroHabilitacionMunicipal = actaViewModel.comHabNumero,
                                rubroComercio = rubroComer
                            )
                            db.actaDao().insertarComercio(datosComercio)
                        }

                        // 3. Datos Catastrales (si aplica)
                        val ctCirc = actaViewModel.catCirc?.trim() ?: ""
                        val ctSecc = actaViewModel.catSeccion?.trim() ?: ""

                        if (ctCirc.isNotEmpty() || ctSecc.isNotEmpty() || actaViewModel.catPartidaMunicipal.isNotBlank()) {
                            val datosCatastro = ActaCatastroEntity(
                                actaId = idLocal,
                                ctCirc = actaViewModel.catCirc,
                                ctSecc = actaViewModel.catSeccion,
                                ctChaqNro = actaViewModel.catChacraNro,
                                ctChaqLet = actaViewModel.catChacraLet,
                                ctQuinNro = actaViewModel.catQuintaNro,
                                ctQuinLet = actaViewModel.catQuintaLet,
                                ctFracNro = actaViewModel.catFraccionNro,
                                ctFracLetra = actaViewModel.catFraccionLet,
                                ctMzNro = actaViewModel.catManzanaNro,
                                ctMzLet = actaViewModel.catManzanaLet,
                                ctParcNro = actaViewModel.catParcelaNro,
                                ctParcLet = actaViewModel.catParcelaLet,
                                ctSubParc = actaViewModel.catSubparcela,
                                ctUf = actaViewModel.catUF,
                                partidaMunicipal = actaViewModel.catPartidaMunicipal
                            )
                            db.actaDao().insertarCatastro(datosCatastro)
                        }
                    }

                    null -> throw IllegalStateException("El tipo de circuito (TRANSITO/INSPECCION) no está definido en el ViewModel")
                }

                // 7. Testigos (Compartido para ambos circuitos)
                val listaTestigos = mutableListOf<ActaTestigoEntity>()

                val dniT1 = actaViewModel.Testigo1Dni?.trim() ?: ""
                val nombreT1 = actaViewModel.Testigo1Nombre?.trim() ?: ""

                if (dniT1.isNotEmpty() || nombreT1.isNotEmpty()) {
                    listaTestigos.add(
                        ActaTestigoEntity(
                            actaId = idLocal,
                            dniOriginal = dniT1,
                            nombreOriginal = nombreT1,
                            provinciaOriginal = actaViewModel.Testigo1Provincia.trim(),
                            domicilioOriginal = "${actaViewModel.Testigo1Calle} ${actaViewModel.Testigo1CalleNro}".trim(),
                            localidadOriginal = actaViewModel.Testigo1Localidad.trim(),
                            cpOriginal = actaViewModel.Testigo1Cp.trim(),
                            firmaPath = actaViewModel.Testigo1FirmaPath
                        )
                    )
                }

                val dniT2 = actaViewModel.Testigo2Dni?.trim() ?: ""
                val nombreT2 = actaViewModel.Testigo2Nombre?.trim() ?: ""

                if (dniT2.isNotEmpty() || nombreT2.isNotEmpty()) {
                    listaTestigos.add(
                        ActaTestigoEntity(
                            actaId = idLocal,
                            dniOriginal = dniT2,
                            nombreOriginal = nombreT2,
                            provinciaOriginal = actaViewModel.Testigo2Provincia.trim(),
                            domicilioOriginal = "${actaViewModel.Testigo2Calle} ${actaViewModel.Testigo2CalleNro}".trim(),
                            localidadOriginal = actaViewModel.Testigo2Localidad.trim(),
                            cpOriginal = actaViewModel.Testigo2Cp.trim(),
                            firmaPath = actaViewModel.Testigo2FirmaPath
                        )
                    )
                }

                if (listaTestigos.isNotEmpty()) {
                    db.actaDao().insertarTestigos(listaTestigos)
                }

                // 8. Fotos / Media (Compartido para ambos circuitos)
                val fotosActuales = actaViewModel.listaFotos.value

                // Limpiamos los registros de fotos previos para este idLocal si es una actualización
                db.actaDao().eliminarMediaPorActaId(idLocal)

                if (!fotosActuales.isNullOrEmpty()) {
                    val listaMedia = fotosActuales.mapIndexed { indice, bitmap ->
                        val nombreFoto = "acta_${idLocal}_foto_${indice + 1}.jpg"
                        val rutaFisica = guardarBitmapEnAlmacenamiento(bitmap, nombreFoto)

                        ActaMediaEntity(
                            actaId = idLocal,
                            tipoMedia = "FOTO_${indice + 1}",
                            rutaArchivo = rutaFisica
                        )
                    }
                    db.actaDao().insertarMedia(listaMedia)
                }

                // Logcat de verificación en desarrollo
                try {
                    val actaRecuperadaDb = db.actaDao().obtenerActaCompleta(idLocal)
                    if (actaRecuperadaDb != null) {
                        val dtoEnviar = com.chivilcoyactas.net.ActaMapeador.transformarAEnviarDto(actaRecuperadaDb)
                        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                        val jsonString = gson.toJson(dtoEnviar)
                        android.util.Log.d("JSON_ACTA_CHIVILCOY", "\n================ OBJETO JSON A ENVIAR ================\n$jsonString\n=======================================================")
                    }
                } catch (jsonException: Exception) {
                    android.util.Log.e("JSON_ACTA_CHIVILCOY", "Error en Logcat de prueba: ${jsonException.message}")
                }


                // Solo encolamos la sincronización si el acta NO es un borrador pendiente
                if (estadoActa == "PENDIENTE") {
                   /* val restricciones = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val solicitudSincro = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                        .setConstraints(restricciones)
                        .build()

                    WorkManager.getInstance(requireContext()).enqueue(solicitudSincro)*/
                    val restricciones = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    // 1. Pasamos el idLocal como dato de entrada al Worker
                    val inputData = workDataOf("ACTA_ID_LOCAL" to idLocal)

                    // 2. Armamos la solicitud con reintento exponencial
                    val solicitudSincro = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                        .setConstraints(restricciones)
                        .setInputData(inputData)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            WorkRequest.MIN_BACKOFF_MILLIS,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        .build()

                    // 3. Encolamos como trabajo único usando el idLocal
                    WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                        "sincro_acta_$idLocal",
                        ExistingWorkPolicy.KEEP,
                        solicitudSincro
                    )
                }

                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    val mensaje = if (estadoActa == "BORRADOR") {
                        "Borrador guardado localmente"
                    } else {
                        "Acta guardada y en cola de envío"
                    }
                    Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_firma_to_exito)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(requireContext(), "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun guardarBitmapEnAlmacenamiento(bitmap: Bitmap, nombreArchivo: String): String {
        val carpetaFotos = File(requireContext().filesDir, "fotos_actas")
        if (!carpetaFotos.exists()) {
            carpetaFotos.mkdirs()
        }

        // Evita duplicar ".jpg" si nombreArchivo ya lo trae
        val nombreLimpio = if (nombreArchivo.endsWith(".jpg", ignoreCase = true)) {
            nombreArchivo
        } else {
            "$nombreArchivo.jpg"
        }

        val archivoDestino = File(carpetaFotos, nombreLimpio)

        try {
            FileOutputStream(archivoDestino).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e("GUARDAR_FOTO", "Error al guardar foto en disco: ${e.message}")
        }

        return archivoDestino.absolutePath
    }

    private fun guardarFirmaEnDisco(bitmap: Bitmap, nombreTipo: String): String {
        val context = requireContext()
        // Generar un nombre único basado en el timestamp o el número de acta
        val nombreArchivo = "firma_${nombreTipo}_${System.currentTimeMillis()}.png"
        val archivo = File(context.filesDir, nombreArchivo)

        FileOutputStream(archivo).use { out ->
            // Comprimir en PNG para mantener la transparencia/calidad sin pérdida
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return archivo.absolutePath // Retorna la ruta absoluta del archivo local
    }

    private fun serializarInventario(inventario: Map<String, String>): String {
        if (inventario.isEmpty()) return ""
        // Une cada par Clave=Valor usando un punto y coma como separador
        return inventario.entries.joinToString(separator = ";") { "${it.key}:${it.value}" }
    }
}