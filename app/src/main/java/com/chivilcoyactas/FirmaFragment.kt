package com.chivilcoyactas

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
import androidx.activity.OnBackPressedCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chivilcoyactas.net.SincronizacionWorker
import java.util.Calendar

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
                if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
                    mostrarDialogoEstadoInspeccion()
                } else {
                    guardarYEnviarAlServidor("FINALIZADA")
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
        // Si el testigo ya firmó
        if (actaViewModel.firmaTestigo != null) {
            binding.btnFirmaTestigo.isEnabled = false
            binding.btnFirmaTestigo.text = "✅ TESTIGO FIRMÓ"
            binding.btnFirmaTestigo.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")) // Verde clarito
        }

        // Si el infractor ya firmó (o se negó)
        if (actaViewModel.firmaInfractor != null || binding.checkSeNiegaAFirmar.isChecked) {
            binding.btnFirmaInfractor.text = if (binding.checkSeNiegaAFirmar.isChecked) "NEGATIVA REGISTRADA" else "✅ INFRACTOR FIRMÓ"
            // Aquí podrías decidir si deshabilitarlo o dejarlo por si quiere corregir la firma
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


        // Guardar en el ViewModel
        if (!binding.signaturePad.isEmpty) {
            val bitmapFirma = binding.signaturePad.signatureBitmap
            if (esFirmaTestigo) {
                actaViewModel.firmaTestigo = bitmapFirma
                // Usamos el nombre que ya viene del actaViewModel para ser consistentes
                actaViewModel.nombreTestigo = actaViewModel.Testigo1Nombre
            } else {
                actaViewModel.firmaInfractor = bitmapFirma
            }
        }

        if (esFirmaTestigo) {
            // VOLVER A PREPARACIÓN
            binding.layoutModoFirma.visibility = View.GONE
            binding.layoutPreparacionFirma.visibility = View.VISIBLE

            actualizarEstadoBotonesSegunFirma()

            // Cambiamos al infractor
            binding.toggleGrupoFirmas.check(R.id.btnFirmaInfractor)
            // IMPORTANTE: Al hacer el check arriba, el listener del Toggle
            // se va a disparar y va a poner esFirmaTestigo = false automáticamente.

            Toast.makeText(context, "Firma de testigo guardada. Turno del infractor.", Toast.LENGTH_SHORT).show()
        } else {
            // CIERRE DEFINITIVO
            if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
                mostrarDialogoEstadoInspeccion()
            } else {
                guardarYEnviarAlServidor("FINALIZADA")
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
                        guardarYEnviarAlServidor("PENDIENTE")
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
                guardarYEnviarAlServidor("FINALIZADA")
            }
            .setNegativeButton("VOLVER", null)
            .show()
    }

    private fun guardarYEnviarAlServidor(estado: String) {
        val loadingDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setMessage("Guardando acta localmente...")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val firmaBase64 = null //SessionManager.obtenerFirmaInspector(requireContext())
                val db = AppDatabase.getDatabase(requireContext())

                // 1. Obtenemos la fecha y hora del momento exacto
                val ahora = Date()
                actaViewModel.fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(ahora)
                actaViewModel.hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora)

                // 🚀 EXTRAEMOS EL DÍA DEL MES (Ejemplo: si es 24/06/2026, nos da el Int 24)
                val calendar = Calendar.getInstance().apply { time = ahora }
                val diaDelMes = calendar.get(Calendar.DAY_OF_MONTH)

                // 🕵️‍♂️ CONSULTA OFFLINE: Buscamos el juzgado que corresponde en la tabla local
                val juzgadoAsignado = db.catalogoDao().obtenerJuzgadoPorDia(diaDelMes) ?: 1 // Plan B: Juzgado 1

                // 1. Guardamos la Cabecera General
                val nuevaActa = ActaEntity(
                    tipoActa = actaViewModel.tipoActa.name,
                    idInspector = 1,
                    fecha = actaViewModel.fecha,
                    hora = actaViewModel.hora,
                    latitud = actaViewModel.latitud,
                    longitud = actaViewModel.longitud,
                    estadoEnvio = "PENDIENTE",
                    firmaInspectorBase64 = firmaBase64,
                    esOperativo = actaViewModel.esOperativo,
                    ejidoUrbano = actaViewModel.ejidoUrbano,//.toString(), // Guardamos el valor (0 o 1)
                    nombreCalle = actaViewModel.calle,
                    alturaCalle = actaViewModel.altura,
                    detallePiso = actaViewModel.ubDepto,
                    detalleReferencia = actaViewModel.ubReferencia,
                    idJuzgado = juzgadoAsignado
                )

                // Insertamos cabecera y obtenemos el ID generado por Room
                val idLocal = db.actaDao().insertarCabecera(nuevaActa)

                // 2. Guardamos los Datos del Infractor
                val infractor = ActaInfractorEntity(
                    actaId = idLocal,
                    nombreCompleto = actaViewModel.ApellidoNombreInfractor,
                    dni = actaViewModel.dniInfractor,
                    provincia = actaViewModel.provinciaInfractor, // 🚀 Mapeado
                    localidad = actaViewModel.localidadInfractor, // 🚀 Mapeado
                    cp = actaViewModel.cpInfractor,               // 🚀 Mapeado
                    calle = actaViewModel.calleInfractor,
                    altura = actaViewModel.alturaInfractor,
                    niegaDatos = actaViewModel.niegaDatos,
                    nroLicencia = actaViewModel.nroLicencia,
                    vinculoLugar = actaViewModel.vinculoLugar,
                    nombreResponsable = actaViewModel.nombreResponsable,
                    dniResponsable = actaViewModel.dniResponsable,
                    calleResponsable = actaViewModel.calleResponsable,
                    alturaResponsable = actaViewModel.alturaResponsable
                )
                db.actaDao().insertarInfractor(infractor)

                // 3. Guardamos las Faltas Seleccionadas
                val listaFaltas = actaViewModel.listaFaltasSeleccionadas.map {
                    ActaFaltasEntity(actaId = idLocal, codigoFalta = it.codigo, descripcion = it.nombre)
                }
                db.actaDao().insertarFaltas(listaFaltas)

                // 4. 🚀 CONDICIONAL: Si es acta de TRANSITO, guardamos vehículo
                if (actaViewModel.tipoActa.name == "TRANSITO") {
                    val datosVehiculo = ActaVehiculoEntity(
                        actaId = idLocal,
                        dominio = actaViewModel.dominio ?: "",
                        marca = actaViewModel.marca ?: "",
                        modelo = actaViewModel.modelo ?: "",
                        tipoVehiculo = actaViewModel.tipoVehiculo ?: "Moto"
                        //color = actaViewModel.vehiculoColor
                    )
                    db.actaDao().insertarVehiculo(datosVehiculo)

                    // 🧪 Si se realizó el test y tenemos resultado, guardamos la alcoholemia técnica
                    val resultado = actaViewModel.ftResultado
                    if (resultado > 0.0) {
                        val alcoholemia = ActaAlcoholemiaEntity(
                            actaId = idLocal,
                            resultadoAlcoholemia = resultado,
                            // Estos datos del equipo los podés tener en variables globales,
                            // o recuperarlos del ViewModel si el inspector los selecciona/vienen por config
                            marcaAlcoholimetro = actaViewModel.alcoMarca ?: "Dräger",
                            modeloAlcoholimetro = actaViewModel.alcoModelo ?: "Alcotest 7510",
                            nroSerieAlcoholimetro = actaViewModel.alcoSerie ?: "S/N",
                            codAprobacionAlcoholimetro = actaViewModel.alcoAprobacion ?: "S/C"
                        )
                        db.actaDao().insertarAlcoholemia(alcoholemia)
                    }

                    // 5. NUEVO: Guardamos el bloque unificado de Medidas Preventivas
                    val medidasPreventivas = ActaMedidaPreventivaEntity(
                        actaId = idLocal,
                        realizoAlcoholemia = actaViewModel.hacerTestAlcoholemia ?: false,
                        retencionVehiculo = actaViewModel.retencionVehiculo ?: false,
                        retencionLicencia = actaViewModel.retencionLicencia ?: false,
                        retencionAnimal = actaViewModel.retencionAnimal ?: false
                        //observacionesMedida = actaViewModel.medidaObservaciones // Opcional si tenés un campo de texto
                    )
                    db.actaDao().insertarMedidasPreventivas(medidasPreventivas)


                    // 6. Guardamos el Detalle del Secuestro si corresponde
                    // Podés verificarlo con el tilde de la medida preventiva o si el mapa no está vacío
                    if (actaViewModel.retencionVehiculo == true || actaViewModel.inventarioSecuestro.isNotEmpty()) {

                        val stringInventario = serializarInventario(actaViewModel.inventarioSecuestro)

                        val detalleSecuestro = ActaSecuestroEntity(
                            actaId = idLocal,
                            // Recuperamos los EditText que completó el inspector
                            numeroMotor = actaViewModel.numeroMotor,
                            numeroChasis = actaViewModel.numeroChasis,
                            estadoCentralObservaciones = actaViewModel.estadoCentral,
                            incluyoInterior = actaViewModel.incluyoInterior,
                            inventarioSerializado = stringInventario // 👈 Todo el mapa metido acá adentro
                        )

                        db.actaDao().insertarSecuestro(detalleSecuestro)
                    }
                }


                // 🏛️ 🚀 NUEVO: Bloque exclusivo para Inspección General / Notificaciones
                // Normalizamos el texto: cambia espacios por guiones bajos y asegura mayúsculas
                val tipoActaNormalizado = actaViewModel.tipoActa.name.replace(" ", "_").uppercase()

                if (tipoActaNormalizado == "INSPECCION" || tipoActaNormalizado == "INSPECCION_GENERAL" || tipoActaNormalizado == "INSPECCION_GRAL") {

                    // Convertimos la lista de categorías ["Comercio", "Obra"] en un String "Comercio, Obra"
                    val inmueblesSerializados = if (actaViewModel.categoriasSeleccionadas.isNotEmpty()) {
                        actaViewModel.categoriasSeleccionadas.joinToString(separator = ", ")
                    } else {
                        "No especificado"
                    }

                    // 1. Guardamos el Procedimiento (Obligatorio para este tipo de acta)
                    val procedimiento = ActaProcedimientoEntity(
                        actaId = idLocal,
                        tipoInspeccion = actaViewModel.procAccion ?: "Inspección Gral",
                        tipoInmueble = inmueblesSerializados, // 👈 Guardamos el String unido por comas
                        nroReferenciaActa = actaViewModel.procRefActa,
                        seProcedeA = actaViewModel.procSeProcedeA ?: "" // El campo de texto libre
                    )
                    db.actaDao().insertarProcedimiento(procedimiento)

                    // 2. Guardamos los Datos del Comercio (Solo si completó el nombre o rubro)
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

                    // 3. Guardamos los Datos Catastrales (Solo si cargó al menos la Circunscripción o Sección)
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



                // 7. Guardamos los datos del Testigo si el inspector cargó alguno
                val listaTestigos = mutableListOf<ActaTestigoEntity>()

                val dniT1 = actaViewModel.Testigo1Dni?.trim() ?: ""
                val nombreT1 = actaViewModel.Testigo1Nombre?.trim() ?: ""

                // Si cargó al menos el DNI o el Nombre, procesamos el Testigo 1
                if (dniT1.isNotEmpty() || nombreT1.isNotEmpty()) {
                    listaTestigos.add(
                        ActaTestigoEntity(
                            actaId = idLocal,
                            dniOriginal = dniT1,
                            nombreOriginal = nombreT1,
                            provinciaOriginal = actaViewModel.Testigo1Provincia.trim(), // 🚀 AGREGADO
                            domicilioOriginal = actaViewModel.Testigo1Domicilio?.trim() ?: "",
                            localidadOriginal = actaViewModel.Testigo1Localidad?.trim() ?: "",
                            cpOriginal = actaViewModel.Testigo1Cp?.trim() ?: ""
                        )
                    )
                }

                val dniT2 = actaViewModel.Testigo2Dni?.trim() ?: ""
                val nombreT2 = actaViewModel.Testigo2Nombre?.trim() ?: ""

                // 💡 Acordate de hacer lo mismo si tenés el bloque del Testigo 2 abajo:
                if (dniT2.isNotEmpty() || nombreT2.isNotEmpty()) {
                    listaTestigos.add(
                        ActaTestigoEntity(
                            actaId = idLocal,
                            dniOriginal = dniT2,
                            nombreOriginal = nombreT2,
                            provinciaOriginal = actaViewModel.Testigo2Provincia.trim(), // 🚀 AGREGADO
                            domicilioOriginal = actaViewModel.Testigo2Domicilio?.trim() ?: "",
                            localidadOriginal = actaViewModel.Testigo2Localidad?.trim() ?: "",
                            cpOriginal = actaViewModel.Testigo2Cp?.trim() ?: ""
                        )
                    )
                }
                // (Opcional) Si en el futuro tenés Testigo 2, clonás el "if" acá abajo para Testigo 2

                // Si la lista tiene elementos, los mandamos a Room de un viaje
                if (listaTestigos.isNotEmpty()) {
                    db.actaDao().insertarTestigos(listaTestigos)
                }

                // 8. 📸 CONDICIONAL: Guardamos las fotos tomadas si existen en el ViewModel
                val fotosActuales = actaViewModel.listaFotos.value
                if (!fotosActuales.isNullOrEmpty()) {

                    // Convertimos cada Bitmap de la lista en una entidad de Room guardando el archivo físico
                    val listaMedia = fotosActuales.mapIndexed { indice, bitmap ->

                        // Creamos un nombre único usando el ID local y el índice
                        val nombreFoto = "acta_${idLocal}_foto_${indice + 1}"
                        val rutaFisica = guardarBitmapEnAlmacenamiento(bitmap, nombreFoto)

                        ActaMediaEntity(
                            actaId = idLocal,
                            tipoMedia = "FOTO_${indice + 1}",
                            rutaArchivo = rutaFisica // 👈 Ahora sí viaja el String con la ruta del archivo
                        )
                    }
                    db.actaDao().insertarMedia(listaMedia)
                }

                // 🧪 --- BLOQUE DE PRUEBA: VER EL JSON COMPLETO EN EL LOGCAT ---
                /*try {
                    // 1. Buscamos el acta completa que acabamos de meter en Room
                    val actaRecuperadaDb = db.actaDao().obtenerActaCompleta(idLocal)

                    if (actaRecuperadaDb != null) {
                        // 2. La pasamos por nuestro mapeador para convertirla a DTO
                        val dtoEnviar = com.chivilcoyactas.net.ActaMapeador.transformarAEnviarDto(actaRecuperadaDb)

                        // 3. Inicializamos GSON con formato lindo (Pretty Printing)
                        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                        val jsonString = gson.toJson(dtoEnviar)

                        // 4. Lo mandamos al Logcat con una etiqueta bien llamativa
                        android.util.Log.d("JSON_ACTA_CHIVILCOY", "\n================ OBJETO JSON A ENVIAR ================\n$jsonString\n=======================================================")


                        // 2. Disparás a la red en una corrutina
                        val respuesta = RetrofitClient.apiService.enviarActa(dtoEnviar)

                        if (respuesta.isSuccessful && respuesta.body()?.success == true) {
                            // 3. ¡Éxito total! Actualizás tu Room local: estadoEnvio = "ENVIADO" e idServer = respuesta.body()?.idServer
                            db.actaDao().actualizarEstadoSincro(idLocal, "ENVIADO", respuesta.body()?.idServer)
                        } else {
                            // Hubo un error de red o de validación en el servidor: estadoEnvio = "ERROR"
                            db.actaDao().actualizarEstadoSincro(idLocal, "ERROR", null)
                        }
                    }
                } catch (jsonException: Exception) {
                    android.util.Log.e("JSON_ACTA_CHIVILCOY", "Error generando el JSON de prueba: ${jsonException.message}")
                }*/
                // ---------------------------------------------------------------

                // =================================================================
                // 🧪 OPTATIVO: PRUEBA DE LOGCAT (Para ver el JSON lindo en consola)
                // =================================================================
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
                // =================================================================

                // =================================================================
                // 🚀 DISPARO AUTOMÁTICO CON WORKMANAGER (Sustituye al envío manual)
                // =================================================================
                // 1. Reglas: Solo arrancar si hay internet (Wi-Fi o Datos)
                val restricciones = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                // 2. Creamos la petición de trabajo único
                val solicitudSincro = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                    .setConstraints(restricciones)
                    .build()

                // 3. Encolamos en el sistema operativo Android
                WorkManager.getInstance(requireContext()).enqueue(solicitudSincro)
                // =================================================================


                //----------------------------------------------------------------
                // 1. Definimos las REGLAS: Solo arrancar si el teléfono tiene internet (cualquiera: Wi-Fi o Datos móviles)
                //val restricciones = Constraints.Builder()
                //    .setRequiredNetworkType(NetworkType.CONNECTED)
               //     .build()

                // 2. Creamos la petición de trabajo único para nuestro Worker
                //val solicitudSincro = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                //    .setConstraints(restricciones)
                //    .build()

                // 3. Encolamos la tarea en el sistema operativo
               // WorkManager.getInstance(requireContext()).enqueue(solicitudSincro)
                //----------------------------------------------------------------

                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(requireContext(), "Acta guardada localmente de forma completa", Toast.LENGTH_SHORT).show()
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
        // Guardamos en el directorio de archivos privados de la app (no ensucia la galería del inspector)
        val directorio = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val archivo = java.io.File(directorio, "$nombreArchivo.jpg")

        java.io.FileOutputStream(archivo).use { out ->
            // Comprimimos la foto al 80% para que no pese una locura y suba rápido a Laravel
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        return archivo.absolutePath
    }

    private fun serializarInventario(inventario: Map<String, String>): String {
        if (inventario.isEmpty()) return ""
        // Une cada par Clave=Valor usando un punto y coma como separador
        return inventario.entries.joinToString(separator = ";") { "${it.key}:${it.value}" }
    }
}