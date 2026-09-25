package com.chivilcoyactas

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

    private val handlerLongPress = android.os.Handler(android.os.Looper.getMainLooper())
    private val runnableFinalizar = Runnable { finalizarProceso() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFirmaBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // 1. Configuración inicial de visibilidad y variables
        esFirmaTestigo = false
        binding.inputNombreTestigo.visibility = View.GONE

        val tieneTestigo = !actaViewModel.Testigo1Nombre.isNullOrEmpty()

        if (!tieneTestigo) {
            binding.btnFirmaTestigo.visibility = View.GONE
            binding.toggleGrupoFirmas.check(R.id.btnFirmaInfractor)
            esFirmaTestigo = false // Asegurar
        } else {
            binding.btnFirmaTestigo.visibility = View.VISIBLE
            binding.toggleGrupoFirmas.check(R.id.btnFirmaTestigo)
            esFirmaTestigo = true // DEBÍA SER TRUE SI HAY TESTIGO

            binding.etNombreTestigo.setText(actaViewModel.Testigo1Nombre)
            binding.inputNombreTestigo.visibility = View.VISIBLE
            binding.inputNombreTestigo.isEnabled = false
        }

        // 2. Listener del Toggle (Quién firma)
        // Listener para actualizar colores/estados si el inspector cambia manualmente
        binding.toggleGrupoFirmas.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                // ESTA LÍNEA FALTABA:
                esFirmaTestigo = (checkedId == R.id.btnFirmaTestigo)

                actualizarEstadoBotonesSegunFirma()

                // También actualizamos la visibilidad del nombre por si cambia manualmente
                if (esFirmaTestigo) {
                    binding.inputNombreTestigo.visibility = View.VISIBLE
                } else {
                    binding.inputNombreTestigo.visibility = View.GONE
                }
            }
        }


        // 3. Botón para pasar al modo firma
        binding.btnIrAFirmar.setOnClickListener {
            if (binding.checkSeNiegaAFirmar.isChecked) {
                // Si se niega, cerramos de una
                if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                    mostrarDialogoEstadoInspeccion()
                } else {
                    guardarYEnviarAlServidor("PENDIENTE")
                }
            } else {
                // Si no se niega, procedemos a que firme (testigo o infractor)
                if (esFirmaTestigo && binding.etNombreTestigo.text.isNullOrEmpty()) {
                    binding.etNombreTestigo.error = "Ingrese nombre"
                    return@setOnClickListener
                }
                prepararModoFirma(esFirmaTestigo)
            }
        }

        binding.checkSeNiegaAFirmar.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.btnIrAFirmar.text = "FINALIZAR ACTA (SE NIEGA)"
                binding.btnIrAFirmar.setBackgroundColor(android.graphics.Color.parseColor("#B71C1C")) // Rojo
            } else {
                binding.btnIrAFirmar.text = "ENTREGAR PARA FIRMAR"
                binding.btnIrAFirmar.setBackgroundColor(android.graphics.Color.parseColor("#007BFF")) // Azul
            }
        }

        // 4. Lógica del SignaturePad (Asegúrate que el ID coincida en el XML)
        binding.signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {}

            override fun onSigned() {
                // Cuando hay firma, mostramos el botón y lo habilitamos
                binding.btnFinalizarActa.visibility = View.VISIBLE
                binding.btnFinalizarActa.isEnabled = true
            }

            override fun onClear() {
                // Si borran la firma, ocultamos el botón y cancelamos cualquier proceso de presión
                binding.btnFinalizarActa.visibility = View.GONE
                binding.btnFinalizarActa.isEnabled = false
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

    private fun actualizarEstadoBotonesSegunFirma() {
        // Si el testigo ya firmó (comprobamos por ruta de archivo)
        if (!actaViewModel.firmaTestigoPath.isNullOrEmpty()) {
            binding.btnFirmaTestigo.isEnabled = false
            binding.btnFirmaTestigo.text = "✅ TESTIGO FIRMÓ"
            binding.btnFirmaTestigo.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")) // Verde clarito
        }

        // Si el infractor ya firmó (o se negó)
        if (!actaViewModel.firmaInfractorPath.isNullOrEmpty() || binding.checkSeNiegaAFirmar.isChecked) {
            binding.btnFirmaInfractor.text = if (binding.checkSeNiegaAFirmar.isChecked) "NEGATIVA REGISTRADA" else "✅ INFRACTOR FIRMÓ"
        }
    }

    private fun prepararModoFirma(esTestigo: Boolean) {
        // Si NO es testigo y marcó "Se niega", saltamos directo al guardado
        if (!esTestigo && binding.checkSeNiegaAFirmar.isChecked) {
            finalizarProceso()
            return
        }

        val titulo = if (esTestigo) "FIRMA DE TESTIGO" else "FIRMA DE INFRACTOR"
        val nombre = if (esTestigo) binding.etNombreTestigo.text.toString() else (actaViewModel.ApellidoNombreInfractor ?: "Infractor")

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
        habilitarBotonFinalizar(false) // Resetear el botón para la nueva firma
    }



    private fun habilitarBotonFinalizar(estado: Boolean) {
        binding.btnFinalizarActa.isEnabled = estado
        binding.btnFinalizarActa.alpha = if (estado) 1.0f else 0.5f
    }

    private fun finalizarProceso() {
        val seNiega = binding.checkSeNiegaAFirmar.isChecked && !esFirmaTestigo

        if (!seNiega && binding.signaturePad.isEmpty) {
            Toast.makeText(context, "Falta la firma obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Si firmó, guardamos el Bitmap como archivo PNG y obtenemos la ruta
        if (!binding.signaturePad.isEmpty) {
            val bitmapFirma = binding.signaturePad.transparentSignatureBitmap
            val tipoFirma = if (esFirmaTestigo) "testigo" else "infractor"
            val rutaGuardada = guardarFirmaEnDisco(bitmapFirma, tipoFirma)

            if (esFirmaTestigo) {
                actaViewModel.firmaTestigoPath = rutaGuardada
                actaViewModel.nombreTestigo = actaViewModel.Testigo1Nombre
            } else {
                actaViewModel.firmaInfractorPath = rutaGuardada
            }
        } else if (seNiega) {
            actaViewModel.infractorSeNiegaAFirmar = true
            actaViewModel.firmaInfractorPath = null
        }

        // 2. Lógica de transición de pantallas
        if (esFirmaTestigo) {
            // Limpiamos el Pad para que quede en blanco para el infractor
            binding.signaturePad.clear()

            // Volver a la pantalla de preparación
            binding.layoutModoFirma.visibility = View.GONE
            binding.layoutPreparacionFirma.visibility = View.VISIBLE

            // Actualizar la interfaz (botón testigo en verde)
            actualizarEstadoBotonesSegunFirma()

            // Cambiar el selector al Infractor (esto dispara el listener y pone esFirmaTestigo = false)
            binding.toggleGrupoFirmas.check(R.id.btnFirmaInfractor)

            Toast.makeText(context, "Firma de testigo guardada. Turno del infractor.", Toast.LENGTH_SHORT).show()
        } else {
            // Cierre definitivo
            if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                mostrarDialogoEstadoInspeccion()
            } else {
                guardarYEnviarAlServidor("PENDIENTE")
            }
        }
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

                if (actaViewModel.idActaLocal == 0L) {
                    // 🚀 Si es acta nueva, incrementamos la secuencia en Room
                    val secuenciaActualizada = obtenerYSumaSecuencia(
                        dao = db.puntoSecuenciaDao(),
                        androidId = androidIdLogueado,
                        puntoCodigo = puntoCodigoLogueado
                    )
                    serieActa = secuenciaActualizada.serie
                    anioActa = secuenciaActualizada.anio
                    secuenciaActa = secuenciaActualizada.ultimaSecuencia // 👈 Secuencia corregida (ej: 1, 2, 3...)

                    actaViewModel.secuenciaActual = secuenciaActa
                    actaViewModel.serie = serieActa
                    actaViewModel.anio = anioActa
                } else {
                    // Si se está editando un borrador local existente, mantenemos la secuencia ya asignada
                    serieActa = actaViewModel.serie.ifEmpty { "E" }
                    anioActa = if (actaViewModel.anio > 0) actaViewModel.anio else calendar.get(Calendar.YEAR)
                    secuenciaActa = actaViewModel.secuenciaActual
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
                    serie = serieActa,
                    puntoEmisionId = puntoEmisionIdLogueado,
                    anio = anioActa,
                    secuencia = secuenciaActa
                )

                val idLocal = db.actaDao().insertarCabecera(nuevaActa)
                actaViewModel.idActaLocal = idLocal

                // 2. Datos del Infractor
                val infractor = ActaInfractorEntity(
                    actaId = idLocal,
                    nombreCompleto = actaViewModel.ApellidoNombreInfractor,
                    dni = actaViewModel.dniInfractor,
                    provincia = actaViewModel.provinciaInfractor,
                    localidad = actaViewModel.localidadInfractor,
                    cp = actaViewModel.cpInfractor,
                    calle = actaViewModel.calleInfractor,
                    altura = actaViewModel.alturaInfractor,
                    niegaDatos = actaViewModel.niegaDatos,
                    nroLicencia = actaViewModel.nroLicencia,
                    vinculoLugar = actaViewModel.vinculoLugar,
                    nombreResponsable = actaViewModel.nombreResponsable,
                    dniResponsable = actaViewModel.dniResponsable,
                    calleResponsable = actaViewModel.calleResponsable,
                    alturaResponsable = actaViewModel.alturaResponsable,
                    firmaPath = actaViewModel.firmaInfractorPath
                )
                db.actaDao().insertarInfractor(infractor)

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
                                resultadoAlcoholemia = actaViewModel.ftResultado,
                                marcaAlcoholimetro = actaViewModel.alcoMarca ?: "Dräger",
                                modeloAlcoholimetro = actaViewModel.alcoModelo ?: "Alcotest 7510",
                                nroSerieAlcoholimetro = actaViewModel.alcoSerie ?: "S/N",
                                codAprobacionAlcoholimetro = actaViewModel.alcoAprobacion ?: "S/C",
                                alcoholimetroId = actaViewModel.alcoholimetroId ?: 0
                            )
                            db.actaDao().insertarAlcoholemia(alcoholemia)
                        }

                        val medidasPreventivas = ActaMedidaPreventivaEntity(
                            actaId = idLocal,
                            realizoAlcoholemia = actaViewModel.hacerTestAlcoholemia ?: false,
                            retencionVehiculo = actaViewModel.retencionVehiculo ?: false,
                            retencionLicencia = actaViewModel.retencionLicencia ?: false,
                            retencionAnimal = actaViewModel.retencionAnimal ?: false
                        )
                        db.actaDao().insertarMedidasPreventivas(medidasPreventivas)

                        if (actaViewModel.retencionVehiculo == true || actaViewModel.inventarioDinamico.isNotEmpty()) {
                            /*val stringInventario = serializarInventario(actaViewModel.inventarioDinamico)
                            val detalleSecuestro = ActaSecuestroEntity(
                                actaId = idLocal,
                                opcionesLista = stringInventario
                            )
                            db.actaDao().insertarSecuestro(detalleSecuestro)*/
                            val listaChecklist = actaViewModel.inventarioDinamico.map { (codigo, valor) ->
                                ActaSecuestroEntity(
                                    actaId = idLocal,
                                    codigoClave = codigo,
                                    valor = valor
                                )
                            }
                            db.actaDao().insertarSecuestro(listaChecklist)
                        }
                    }

                    TipoFormulario.INSPECCION -> {
                        // -------------------------------------------------------------
                        // CIRCUITO INSPECCIÓN: Procedimiento, Comercio, Catastro
                        // -------------------------------------------------------------
                        val inmueblesSerializados = if (actaViewModel.categoriasSeleccionadas.isNotEmpty()) {
                            actaViewModel.categoriasSeleccionadas.joinToString(separator = ", ")
                        } else {
                            "No especificado"
                        }

                        // 1. Procedimiento
                        val procedimiento = ActaProcedimientoEntity(
                            actaId = idLocal,
                            tipoInspeccion = actaViewModel.procAccion ?: "Inspección Gral",
                            tipoInmueble = inmueblesSerializados,
                            nroReferenciaActa = actaViewModel.procRefActa,
                            seProcedeA = actaViewModel.procSeProcedeA ?: ""
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

                        if (ctCirc.isNotEmpty() || ctSecc.isNotEmpty()) {
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
                                ctUf = actaViewModel.catUF
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
                            domicilioOriginal = actaViewModel.Testigo1Domicilio?.trim() ?: "",
                            localidadOriginal = actaViewModel.Testigo1Localidad?.trim() ?: "",
                            cpOriginal = actaViewModel.Testigo1Cp?.trim() ?: "",
                            firmaPath = actaViewModel.firmaTestigoPath
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
                            domicilioOriginal = actaViewModel.Testigo2Domicilio?.trim() ?: "",
                            localidadOriginal = actaViewModel.Testigo2Localidad?.trim() ?: "",
                            cpOriginal = actaViewModel.Testigo2Cp?.trim() ?: "",
                            firmaPath = actaViewModel.firmaTestigoPath
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