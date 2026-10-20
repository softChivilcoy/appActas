package com.chivilcoyactas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chivilcoyactas.databinding.FragmentHojaRutaBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HojaRutaFragment : Fragment() {

    private var _binding: FragmentHojaRutaBinding? = null
    private val binding get() = _binding!!
    private val actaViewModel: ActaViewModel by activityViewModels()

    private var adapter: HojaRutaAdapter? = null
    private val listaBorradores = mutableListOf<ItemHojaRuta>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHojaRutaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvHojaRuta.layoutManager = LinearLayoutManager(requireContext())

        // Configurar el gesto de deslizar para borrar (Swipe to Delete)
        configurarSwipeParaEliminar()

        cargarItemsHojaRuta()

        binding.btnNuevaActa.setOnClickListener {
            actaViewModel.resetearActa()
            actaViewModel.esNuevaActa = true
            findNavController().navigate(R.id.action_hojaRuta_to_step1_ubicacion)
        }
    }

    private fun cargarItemsHojaRuta() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val borradores = db.actaDao().obtenerActasPorEstado("BORRADOR")

            listaBorradores.clear()
            listaBorradores.addAll(borradores.map { acta ->
                ItemHojaRuta(
                    id = acta.idLocal,
                    titulo = "BORRADOR #${acta.idLocal}",
                    calle = acta.nombreCalle ?: "SIN CALLE",
                    altura = acta.alturaCalle?.toString() ?: "",
                    motivo = "Acta pendiente de finalización",
                    infractorNombre = "",
                    fecha = "${acta.fecha} ${acta.hora}",
                    estadoTexto = "BORRADOR",
                    origen = TipoOrigenItem.BORRADOR_LOCAL,
                    actaEntity = acta
                )
            })

            withContext(Dispatchers.Main) {
                _binding?.let { binding ->
                    actualizarVisibilidadEstado(listaBorradores.isEmpty())

                    adapter = HojaRutaAdapter(listaBorradores) { itemSeleccionado ->
                        procesarSeleccion(itemSeleccionado)
                    }
                    binding.rvHojaRuta.adapter = adapter
                }
            }
        }
    }

    private fun configurarSwipeParaEliminar() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val posicion = viewHolder.adapterPosition
                val itemABorrar = listaBorradores[posicion]

                // Diálogo de confirmación antes de borrar de Room
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar Borrador")
                    .setMessage("¿Estás seguro de que querés descartar el borrador #${itemABorrar.id}? Se perderán los datos locales.")
                    .setCancelable(false)
                    .setPositiveButton("Eliminar") { _, _ ->
                        eliminarBorradorDeRoom(itemABorrar, posicion)
                    }
                    .setNegativeButton("Cancelar") { dialog, _ ->
                        // Si cancela, restauramos la tarjeta en la UI
                        adapter?.notifyItemChanged(posicion)
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.rvHojaRuta)
    }

    private fun eliminarBorradorDeRoom(item: ItemHojaRuta, posicion: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.actaDao().eliminarActaLocalCompleta(item.id)

            withContext(Dispatchers.Main) {
                listaBorradores.removeAt(posicion)
                adapter?.notifyItemRemoved(posicion)

                if (listaBorradores.isEmpty()) {
                    actualizarVisibilidadEstado(true)
                }
            }
        }
    }

    private fun actualizarVisibilidadEstado(estaVacio: Boolean) {
        if (estaVacio) {
            binding.rvHojaRuta.visibility = View.GONE
            binding.layoutEmptyState?.visibility = View.VISIBLE
        } else {
            binding.rvHojaRuta.visibility = View.VISIBLE
            binding.layoutEmptyState?.visibility = View.GONE
        }
    }

    private fun procesarSeleccion(item: ItemHojaRuta) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val actaCompleta = db.actaDao().obtenerActaCompleta(item.id)

            withContext(Dispatchers.Main) {
                actaViewModel.resetearActa()

                if (actaCompleta != null) {
                    val cabecera = actaCompleta.cabecera

                    // --- 1. CABECERA Y UBICACIÓN ---
                    actaViewModel.idActaLocal = cabecera.idLocal
                    actaViewModel.esNuevaActa = false
                    actaViewModel.secuenciaActual = cabecera.secuencia
                    actaViewModel.serie = cabecera.serie
                    actaViewModel.anio = cabecera.anio
                    actaViewModel.puntoEmisionId = cabecera.puntoEmisionId

                    actaViewModel.tipoFormulario = if (actaCompleta.vehiculo != null) TipoFormulario.TRANSITO else TipoFormulario.INSPECCION
                    actaViewModel.calle = cabecera.nombreCalle
                    actaViewModel.altura = cabecera.alturaCalle ?:0
                    actaViewModel.latitud = cabecera.latitud
                    actaViewModel.longitud = cabecera.longitud
                    actaViewModel.ejidoUrbano = cabecera.ejidoUrbano
                    actaViewModel.ubDepto = cabecera.detallePiso ?: ""
                    actaViewModel.ubReferencia = cabecera.detalleReferencia ?: ""
                    actaViewModel.ubTieneDetalleAdicional = !cabecera.detallePiso.isNullOrBlank() || !cabecera.detalleReferencia.isNullOrBlank()

                    // --- 2. INFRACTORES / PERSONAS VINCULADAS ---
                    actaViewModel.datosPersonasVinculadas.clear()
                    actaViewModel.vinculoLugar = ""
                    actaViewModel.niegaDatos = -1
                    actaViewModel.motivoDatos = ""

                    if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                        // En Inspección, restauramos el mapa de personas vinculadas
                        val vinculos = mutableListOf<String>()
                        actaCompleta.infractores.forEach { inf ->
                            actaViewModel.niegaDatos = inf.niegaDatos
                            
                            if (inf.niegaDatos == 0) {
                                val persona = PersonaVinculada(
                                    nombre = inf.nombreCompleto,
                                    dni = inf.dni,
                                    provincia = inf.provincia,
                                    localidad = inf.localidad,
                                    cp = inf.cp,
                                    calle = inf.calle,
                                    altura = inf.altura,
                                    tieneLicencia = inf.tieneLicencia == 1,
                                    nroLicencia = inf.nroLicencia ?: "",
                                    tipoVinculo = inf.vinculoLugar,
                                    firmaPath = inf.firmaPath
                                )
                                inf.vinculoLugar?.let { rol ->
                                    actaViewModel.datosPersonasVinculadas[rol] = persona
                                    vinculos.add(rol)
                                    // Mantenemos firmaInfractorPath para compatibilidad con el primer infractor si es necesario
                                    if (inf.firmaPath != null && actaViewModel.firmaInfractorPath == null) {
                                        actaViewModel.firmaInfractorPath = inf.firmaPath
                                    }
                                }
                            } else {
                                // Mapeo de motivoDatos para Inspección si fuera necesario
                                if (inf.motivoNiega != null && actaViewModel.motivoDatos.isEmpty()) {
                                    actaViewModel.motivoDatos = inf.motivoNiega
                                }
                            }
                        }
                        actaViewModel.vinculoLugar = vinculos.joinToString(", ")
                    } else {
                        // En Tránsito, restauramos el infractor único
                        actaCompleta.infractores.firstOrNull()?.let { inf ->
                            actaViewModel.ApellidoNombreInfractor = inf.nombreCompleto
                            actaViewModel.dniInfractor = inf.dni
                            actaViewModel.provinciaInfractor = inf.provincia
                            actaViewModel.localidadInfractor = inf.localidad
                            actaViewModel.cpInfractor = inf.cp
                            actaViewModel.calleInfractor = inf.calle
                            actaViewModel.alturaInfractor = inf.altura
                            actaViewModel.niegaDatos = inf.niegaDatos
                            actaViewModel.tieneLicencia = inf.tieneLicencia == 1
                            actaViewModel.nroLicencia = inf.nroLicencia ?: ""
                            actaViewModel.firmaInfractorPath = inf.firmaPath
                            actaViewModel.motivoDatos = inf.motivoNiega ?: ""
                        }
                    }

                    // --- 3. PROCEDIMIENTO / TIPO DE INSPECCIÓN ---
                    actaViewModel.categoriasSeleccionadas.clear()

                    // 🚀 LEER LA CATEGORÍA REAL DESDE LA CABECERA (procTipoInmueble)
                    cabecera.procTipoInmueble?.let { categorias ->
                        if (categorias.isNotBlank()) {
                            // Soporta una sola categoría o varias separadas por coma
                            val listaCategoriasBD = categorias.split(",").map { it.trim() }
                            actaViewModel.categoriasSeleccionadas.addAll(listaCategoriasBD)
                        }
                    }

                    actaCompleta.procedimiento?.let { proc ->
                        actaViewModel.procAccion = proc.tipoInspeccion
                        actaViewModel.procRefActa = proc.nroReferenciaActa ?: ""
                    }

                    // --- 4. COMERCIO ---
                    actaCompleta.comercio?.let { com ->
                        actaViewModel.comNombreFantasia = com.nombreComercio
                        actaViewModel.comHabNumero = com.nroHabilitacionMunicipal ?: ""
                        actaViewModel.comRubro = com.rubroComercio
                    }

                    // --- 5. CATASTRO ---
                    actaCompleta.catastro?.let { cat ->
                        actaViewModel.catCirc = cat.ctCirc ?: ""
                        actaViewModel.catSeccion = cat.ctSecc ?: ""
                        actaViewModel.catChacraNro = cat.ctChaqNro ?: ""
                        actaViewModel.catChacraLet = cat.ctChaqLet ?: ""
                        actaViewModel.catQuintaNro = cat.ctQuinNro ?: ""
                        actaViewModel.catQuintaLet = cat.ctQuinLet ?: ""
                        actaViewModel.catFraccionNro = cat.ctFracNro ?: ""
                        actaViewModel.catFraccionLet = cat.ctFracLetra ?: ""
                        actaViewModel.catManzanaNro = cat.ctMzNro ?: ""
                        actaViewModel.catManzanaLet = cat.ctMzLet ?: ""
                        actaViewModel.catParcelaNro = cat.ctParcNro ?: ""
                        actaViewModel.catParcelaLet = cat.ctParcLet ?: ""
                        actaViewModel.catSubparcela = cat.ctSubParc ?: ""
                        actaViewModel.catUF = cat.ctUf ?: ""
                        actaViewModel.catPartidaMunicipal = cat.partidaMunicipal ?: ""
                    }

                    // --- RESTAURAR CATEGORÍAS SELECCIONADAS ---

                    // 2. Fallback inteligente: si tipoInmueble vino vacío en la BD, inferimos según las entidades
                    if (actaViewModel.categoriasSeleccionadas.isEmpty()) {
                        val tieneComercio = actaCompleta.comercio != null ||
                                !actaViewModel.comNombreFantasia.isNullOrBlank() ||
                                !actaViewModel.comHabNumero.isNullOrBlank()

                        if (tieneComercio) {
                            actaViewModel.categoriasSeleccionadas.add("Comercio / Local")
                        }

                        val tieneCatastro = actaCompleta.catastro != null ||
                                !actaViewModel.catCirc.isNullOrBlank() ||
                                !actaViewModel.catSeccion.isNullOrBlank() ||
                                !actaViewModel.catManzanaNro.isNullOrBlank() ||
                                !actaViewModel.catParcelaNro.isNullOrBlank()

                        if (tieneCatastro && !tieneComercio) {
                            actaViewModel.categoriasSeleccionadas.add("Vereda / Terreno")
                        }
                    }

                    // --- 6. FALTAS ---
                    actaViewModel.listaFaltasSeleccionadas.clear()
                    actaCompleta.faltas.forEach { faltaEntity ->
                        val infraccion = Infraccion(
                            id = faltaEntity.id.toInt(),
                            codigo = faltaEntity.codigoFalta,
                            nombre = faltaEntity.descripcion
                        )
                        actaViewModel.listaFaltasSeleccionadas.add(infraccion)
                    }
                    // Recuperar observaciones de la cabecera (detalleFalta)
                    actaViewModel.ftObservaciones = cabecera.detalleFalta ?: ""

                    // --- 7. TESTIGOS ---
                    // Limpiamos los datos previos en el ViewModel para evitar arrastrar basura
                    actaViewModel.Testigo1Dni = ""
                    actaViewModel.Testigo1Nombre = ""
                    actaViewModel.Testigo1Provincia = "BUENOS AIRES"
                    actaViewModel.Testigo1Calle = ""
                    actaViewModel.Testigo1Localidad = "CHIVILCOY"
                    actaViewModel.Testigo1Cp = ""
                    actaViewModel.Testigo1FirmaPath = null

                    actaViewModel.Testigo2Dni = ""
                    actaViewModel.Testigo2Nombre = ""
                    actaViewModel.Testigo2Provincia = "BUENOS AIRES"
                    actaViewModel.Testigo2Calle = ""
                    actaViewModel.Testigo2Localidad = "CHIVILCOY"
                    actaViewModel.Testigo2Cp = ""
                    actaViewModel.Testigo2FirmaPath = null

                    // Asignamos el primer testigo si existe en la BD
                    actaCompleta.testigos.getOrNull(0)?.let { t1 ->
                        actaViewModel.Testigo1Dni = t1.dniOriginal
                        actaViewModel.Testigo1Nombre = t1.nombreOriginal
                        actaViewModel.Testigo1Provincia = t1.provinciaOriginal
                        actaViewModel.Testigo1Calle = t1.domicilioOriginal
                        actaViewModel.Testigo1Localidad = t1.localidadOriginal
                        actaViewModel.Testigo1Cp = t1.cpOriginal
                        actaViewModel.Testigo1FirmaPath = t1.firmaPath
                    }

                    // Asignamos el segundo testigo si existe en la BD
                    actaCompleta.testigos.getOrNull(1)?.let { t2 ->
                        actaViewModel.Testigo2Dni = t2.dniOriginal
                        actaViewModel.Testigo2Nombre = t2.nombreOriginal
                        actaViewModel.Testigo2Provincia = t2.provinciaOriginal
                        actaViewModel.Testigo2Calle = t2.domicilioOriginal
                        actaViewModel.Testigo2Localidad = t2.localidadOriginal
                        actaViewModel.Testigo2Cp = t2.cpOriginal
                        actaViewModel.Testigo2FirmaPath = t2.firmaPath
                    }

                    // --- 8. VEHÍCULO / ALCOHOLEMIA / BIENES (Solo Tránsito) ---
                    if (actaViewModel.tipoFormulario == TipoFormulario.TRANSITO) {
                        actaCompleta.vehiculo?.let { v ->
                            actaViewModel.dominio = v.dominio
                            actaViewModel.tipoVehiculo = v.tipoVehiculo
                            actaViewModel.idTipoVehiculoSeleccionado = v.idTipoVehiculo
                            actaViewModel.marca = v.marca
                            actaViewModel.idMarcaSeleccionada = v.idMarca
                            actaViewModel.modelo = v.modelo
                            actaViewModel.idModeloSeleccionado = v.idModelo
                        }

                        actaCompleta.alcoholemia?.let { a ->
                            actaViewModel.hacerTestAlcoholemia = true
                            actaViewModel.ftResultado = a.resultadoAlcoholemia
                            actaViewModel.alcoMarca = a.marcaAlcoholimetro
                            actaViewModel.alcoModelo = a.modeloAlcoholimetro
                            actaViewModel.alcoSerie = a.nroSerieAlcoholimetro
                            actaViewModel.alcoAprobacion = a.codAprobacionAlcoholimetro
                            actaViewModel.alcoholimetroId = a.alcoholimetroId
                        }

                        actaViewModel.bienesSeleccionadosIds.clear()
                        actaViewModel.bienesDescripciones.clear()
                        actaCompleta.bienesRetenidos.forEach { bien ->
                            actaViewModel.bienesSeleccionadosIds.add(bien.tipoBienId)
                            bien.observacion?.let { obs ->
                                actaViewModel.bienesDescripciones[bien.tipoBienId] = obs
                            }
                        }
                    }

                    // --- 9. FOTOS / MEDIA / OBSERVACIONES ---
                    actaViewModel.observaciones = cabecera.observaciones ?: ""

                    val fotosRecuperadas = mutableListOf<Bitmap>()

                    actaCompleta.media.forEach { mediaEntity ->
                        val archivo = java.io.File(mediaEntity.rutaArchivo)
                        if (archivo.exists()) {
                            try {
                                val bitmap = BitmapFactory.decodeFile(archivo.absolutePath)
                                if (bitmap != null) {
                                    fotosRecuperadas.add(bitmap)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("HOJA_RUTA", "Error al cargar foto de la BD: ${e.message}")
                            }
                        }
                    }

                    // 🚀 Notificamos al ViewModel y FotosFragment se actualizará solo
                    actaViewModel.setListaFotos(fotosRecuperadas)
                }

                // Navegar a la primera pantalla del flujo
                findNavController().navigate(R.id.action_hojaRuta_to_step1_ubicacion)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}