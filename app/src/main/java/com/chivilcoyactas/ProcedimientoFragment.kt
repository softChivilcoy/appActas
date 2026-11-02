package com.chivilcoyactas

import android.graphics.Rect
import com.google.android.material.textfield.TextInputLayout
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.Filter
import com.google.android.material.button.MaterialButton
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentProcedimientoBinding
import kotlinx.coroutines.launch

class ProcedimientoFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentProcedimientoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProcedimientoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Estándar 3nStar: Foco inicial en el root para evitar teclado automático y validaciones prematuras
        binding.rootProcedimiento.requestFocus()

        // Forzar scroll al inicio al entrar o volver
        binding.scrollProcedimiento.post {
            binding.scrollProcedimiento.scrollTo(0, 0)
        }

        (activity as? MainActivity)?.actualizarProgreso(2)

        // 🚀 Ajuste dinámico del padding según el teclado (Evita espacio vacío gigante al inicio)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollProcedimiento) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val extraPadding = if (imeHeight > 0) imeHeight + (32 * resources.displayMetrics.density).toInt() else 0
            
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                extraPadding
            )
            insets
        }

        configurarAutoScrollEnCampos()

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            // 0. Sincronizar categoriasSeleccionadas desde el String procTipoInmueble (Importante para Borradores)
            if (actaViewModel.categoriasSeleccionadas.isEmpty() && actaViewModel.procTipoInmueble.isNotEmpty()) {
                val categoriasGuardadas = actaViewModel.procTipoInmueble
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                actaViewModel.categoriasSeleccionadas.addAll(categoriasGuardadas)
            }

            // 1. Cargar las categorías de la base de datos
            if (actaViewModel.catalogoCategorias.isEmpty()) {
                var categoriasDb = db.catalogoDao().obtenerTodasLasCategorias()

                if (categoriasDb.isEmpty()) {
                    val categoriasSemilla = listOf(
                        CategoriaEntity(id = 1, nombre = "Baldío"),
                        CategoriaEntity(id = 2, nombre = "Comercio / Local"),
                        CategoriaEntity(id = 3, nombre = "Higiene Urbana"),
                        CategoriaEntity(id = 4, nombre = "Obra en Construcción"),
                        CategoriaEntity(id = 5, nombre = "Vereda / Terreno"),
                        CategoriaEntity(id = 6, nombre = "Vía Pública / Ruidos")
                    )
                    db.catalogoDao().insertarCategorias(categoriasSemilla)
                    categoriasDb = db.catalogoDao().obtenerTodasLasCategorias()
                }
                actaViewModel.catalogoCategorias = categoriasDb
            }

            // 2. Sincronizar IDs a Nombres si es necesario (para compatibilidad con el servidor/borradores)
            val seleccionadasActuales = actaViewModel.categoriasSeleccionadas.toList()
            actaViewModel.categoriasSeleccionadas.clear()
            seleccionadasActuales.forEach { sel ->
                val selLimpia = sel.replace(" ", "")
                // Buscamos si el String guardado es un número que coincida con un ID
                val catEncontradaPorId = actaViewModel.catalogoCategorias.find { it.id.toString() == selLimpia }
                // O si el nombre normalizado coincide
                val catEncontradaPorNombre = actaViewModel.catalogoCategorias.find { normalizarTexto(it.nombre) == normalizarTexto(sel) }
                
                val nombreFinal = catEncontradaPorId?.nombre ?: catEncontradaPorNombre?.nombre ?: sel
                if (!actaViewModel.categoriasSeleccionadas.contains(nombreFinal)) {
                    actaViewModel.categoriasSeleccionadas.add(nombreFinal)
                }
            }

            // 3. Renderizar dinámicamente los Chips de Categoría
            renderizarCategoriasChips(actaViewModel.catalogoCategorias)

            // 3. Configurar el Spinner de Acciones (Catálogo completo de la DB)
            val actasDb = db.catalogoDao().obtenerTodosLosTiposActa()

            if (actasDb.isNotEmpty()) {
                actaViewModel.listaTiposActaDisponibles = actasDb.map {
                    TipoActaDto(
                        id = it.id,
                        nombre = it.nombre,
                        requiereFaltas = it.requiereFaltas
                    )
                }
            }
            setupSpinnerAcciones()

            // 4. Volcar y recuperar los datos en los controles de la vista
            recuperarDatos()
        }

        binding.btnVolverProcedimiento.setOnClickListener {
            findNavController().navigate(R.id.action_procedimiento_to_ubicacion_VUELTA)
        }

        binding.btnSiguienteProcedimiento.setOnClickListener {
            // Validar categorías tildadas
            if (actaViewModel.categoriasSeleccionadas.isEmpty()) {
                Toast.makeText(context, "Debe seleccionar al menos una categoría", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validar que se haya seleccionado un Tipo de Acta o escrito una acción válida de la LISTA
            val accionTexto = binding.spinnerAccion.text.toString().trim()
            val tipoValidado = actaViewModel.listaTiposActaDisponibles.find {
                it.nombre.trim().equals(accionTexto, ignoreCase = true)
            }

            if (tipoValidado == null) {
                binding.spinnerAccion.error = "Seleccioná una acción válida de la lista"
                return@setOnClickListener
            } else {
                binding.spinnerAccion.error = null
                // Aseguramos sincronización aunque no haya hecho click en el dropdown
                actaViewModel.idTipoActaSeleccionada = tipoValidado.id
                actaViewModel.procAccion = tipoValidado.nombre
            }

            guardarDatos()
            findNavController().navigate(R.id.action_procedimiento_to_infractor)
        }

        // 2. Scroll suavizado SOLO cuando el campo GANA foco (hasFocus == true) Referencia Acta
        /*binding.etRefActa.setOnFocusChangeListener { viewFoco, hasFocus ->
            if (hasFocus) {
                binding.scrollProcedimiento.post {
                    binding.scrollProcedimiento.smoothScrollTo(0, binding.etRefActa.top)
                }
            }
        }*/

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollProcedimiento.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val currentFocusView = activity?.currentFocus
                if (currentFocusView is EditText) {
                    (activity as? MainActivity)?.hideKeyboard()
                    currentFocusView.clearFocus()
                }
            }
            false // Permite que el ScrollView siga manejando el evento de scroll nativo
        }
    }

    private fun setupSpinnerAcciones() {
        val tiposActaInspeccion = actaViewModel.listaTiposActaDisponibles

        if (tiposActaInspeccion.isNotEmpty()) {
            val nombresAcciones = tiposActaInspeccion.map { it.nombre }
            
            // 🚀 ADAPTER PERSONALIZADO CON FILTRO COMPLETO (El límite se da por la altura del scroll)
            val adapter = crearAdapterLimitado(nombresAcciones)
            binding.spinnerAccion.setAdapter(adapter)

            // Limitar la altura del dropdown para mostrar ~4.5 items y habilitar scroll
            binding.spinnerAccion.dropDownHeight = (200 * resources.displayMetrics.density).toInt()

            binding.spinnerAccion.setOnItemClickListener { parent, _, position, _ ->
                val nombreSeleccionado = parent.getItemAtPosition(position).toString()
                val tipoSeleccionado = tiposActaInspeccion.find { it.nombre == nombreSeleccionado }
                
                tipoSeleccionado?.let {
                    actaViewModel.idTipoActaSeleccionada = it.id
                    actaViewModel.procAccion = it.nombre
                    binding.spinnerAccion.error = null
                }
                actualizarVisibilidadCatastro()
                (activity as? MainActivity)?.hideKeyboard()
            }

            // 🚀 Mostrar el listado al tocar el campo (comportamiento de Spinner)
            binding.spinnerAccion.setOnClickListener {
                binding.spinnerAccion.showDropDown()
            }

            // Validar al perder el foco si lo que escribió es válido y seleccionar todo al entrar
            binding.spinnerAccion.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    binding.spinnerAccion.post {
                        binding.spinnerAccion.selectAll()
                    }
                    binding.spinnerAccion.showDropDown()
                    ejecutarAutoScroll(binding.spinnerAccion)
                    v.setBackgroundResource(R.drawable.bg_edittext_focused)
                } else {
                    v.setBackgroundResource(0)
                    val textoIngresado = binding.spinnerAccion.text.toString().trim()
                    if (textoIngresado.isNotEmpty()) {
                        val tipoEncontrado = tiposActaInspeccion.find {
                            it.nombre.trim().equals(textoIngresado, ignoreCase = true)
                        }

                        if (tipoEncontrado != null) {
                            actaViewModel.idTipoActaSeleccionada = tipoEncontrado.id
                            actaViewModel.procAccion = tipoEncontrado.nombre
                            binding.spinnerAccion.error = null
                            // Si existe pero lo escribió con diferencias de mayúsculas, se corrige
                            binding.spinnerAccion.setText(tipoEncontrado.nombre, false)
                        } else {
                            binding.spinnerAccion.setText("")
                            binding.spinnerAccion.error = "Seleccioná una acción válida de la lista"
                            Toast.makeText(
                                requireContext(),
                                "Acción no oficial. Seleccioná una de la lista sugerida.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    actualizarVisibilidadCatastro()
                }
            }
        } else {
            Toast.makeText(requireContext(), "No hay tipos de acta configurados para esta repartición", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderizarCategoriasChips(lista: List<CategoriaEntity>) {
        binding.containerCategorias.removeAllViews()

        val density = resources.displayMetrics.density
        val marginHorizontal = (6 * density).toInt()
        val marginVertical = (4 * density).toInt()

        lista.forEach { categoria ->
            val nombreLimpio = categoria.nombre.trim()
            
            // Usamos MaterialButton con estilo Outlined en lugar de Chip para permitir multilínea
            val button = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = nombreLimpio
                isCheckable = true
                isAllCaps = false
                textSize = 13f
                cornerRadius = (20 * density).toInt()
                
                // Ajustes de altura e insets para imitar la apariencia de un Chip
                minHeight = 0
                minWidth = 0
                val verticalInset = (4 * density).toInt()
                insetTop = verticalInset
                insetBottom = verticalInset
                setPaddingRelative((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())

                // Configuración de multilínea (MaterialButton lo soporta sin errores)
                isSingleLine = false
                maxLines = 2
                gravity = android.view.Gravity.CENTER
                
                addOnCheckedChangeListener { view, isChecked ->
                    if (isChecked) {
                        // 1. Desmarcar visualmente los otros (Selección única)
                        for (i in 0 until binding.containerCategorias.childCount) {
                            val other = binding.containerCategorias.getChildAt(i) as? MaterialButton
                            if (other != null && other != view && other.isChecked) {
                                other.isChecked = false
                            }
                        }
                        // 2. Actualizar el ViewModel
                        actaViewModel.categoriasSeleccionadas.clear()
                        actaViewModel.categoriasSeleccionadas.add(nombreLimpio)
                    } else {
                        actaViewModel.categoriasSeleccionadas.remove(nombreLimpio)
                    }
                    actualizarVisibilidadCatastro()
                }

                // 🔹 Verificación flexible para marcar el chip recuperado (Por ID o por Nombre)
                // Se setea DESPUÉS del listener para que se dispare la lógica de visibilidad inicial
                isChecked = actaViewModel.categoriasSeleccionadas.any { seleccionada ->
                    val esMismoNombre = normalizarTexto(seleccionada) == normalizarTexto(nombreLimpio)
                    val esMismoId = seleccionada == categoria.id.toString()
                    esMismoNombre || esMismoId
                }

                // 🚀 LayoutParams para GridLayout: 2 columnas de igual ancho
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL), // row spec
                    GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f) // column spec con peso 1
                )
                params.width = 0
                params.height = GridLayout.LayoutParams.WRAP_CONTENT
                params.setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical)
                layoutParams = params
            }
            binding.containerCategorias.addView(button)
        }

        actualizarVisibilidadCatastro()
    }

    // Función auxiliar para comparar nombres de categorías de forma ultra-segura (quita acentos, espacios y símbolos)
    private fun normalizarTexto(texto: String): String {
        return texto.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
            .replace('ü', 'u').replace('ñ', 'n')
            .replace("[^a-z0-9]".toRegex(), "")
    }

    private fun actualizarVisibilidadCatastro() {
        // 1. Visibilidad de Catastro y Comercio (Categorías)
        val categoriasCatastro = listOf("Baldío", "Comercio / Local", "Higiene Urbana", "Obra en Construcción", "Vereda / Terreno", "Vía Pública / Ruidos")

        val mostrarCatastro = actaViewModel.categoriasSeleccionadas.any { seleccionada ->
            val selNorm = normalizarTexto(seleccionada)
            categoriasCatastro.any { normalizarTexto(it) == selNorm }
        }
        binding.cardCatastro.visibility = if (mostrarCatastro) View.VISIBLE else View.GONE

        val mostrarComercio = actaViewModel.categoriasSeleccionadas.any { seleccionada ->
            normalizarTexto(seleccionada) == normalizarTexto("Comercio / Local")
        }

        val estabaOcultoComercio = binding.cardComercio.visibility == View.GONE
        binding.cardComercio.visibility = if (mostrarComercio) View.VISIBLE else View.GONE

        // 🚀 2. Visibilidad de Referencia Acta (Tipo de Acta -> requiereFaltas)
        val accionActual = binding.spinnerAccion.text.toString().trim()
        val tipoEncontrado = actaViewModel.listaTiposActaDisponibles.find { it.id == actaViewModel.idTipoActaSeleccionada }
            ?: actaViewModel.listaTiposActaDisponibles.find { it.nombre.equals(accionActual, ignoreCase = true) }

        val requiereFaltas = tipoEncontrado?.requiereFaltas ?: false
        binding.layoutRefActa.visibility = if (requiereFaltas) View.VISIBLE else View.GONE

        android.util.Log.d("VISIBILIDAD_REF", "Accion: $accionActual | ID_Sel: ${actaViewModel.idTipoActaSeleccionada} | Encontrado_ID: ${tipoEncontrado?.id} | Requiere: $requiereFaltas")

        actualizarFlujoDeFocos()

        // Si la Card de Comercio pasa de estar oculta a visible, hacemos foco y scroll automático
        if (mostrarComercio && estabaOcultoComercio) {
            binding.cardComercio.postDelayed({
                binding.etNombreFantasia.requestFocus()
            }, 150)
        }
    }

    private fun actualizarFlujoDeFocos() {
        val esCatastroVisible = binding.cardCatastro.visibility == View.VISIBLE
        val esRefActaVisible = binding.layoutRefActa.visibility == View.VISIBLE

        if (esCatastroVisible) {
            binding.etRubro.nextFocusForwardId = binding.etPartidaMunicipal.id
        } else {
            if (esRefActaVisible) {
                binding.etRubro.nextFocusForwardId = binding.etRefActa.id
            }
        }
    }

    private fun configurarAutoScrollEnCampos() {
        val inputs = listOf(
            // Datos de Comercio
            binding.etNombreFantasia, binding.etHabNumero, binding.etRubro,
            // Datos Catastrales
            binding.etPartidaMunicipal, binding.etCirc, binding.etSeccion, 
            binding.etChacraNro, binding.etChacraLet,
            binding.etQuintaNro, binding.etQuintaLet, binding.etFraccionNro, binding.etFraccionLet,
            binding.etManzanaNro, binding.etManzanaLet, binding.etParcelaNro, binding.etParcelaLet,
            binding.etSubparcelaNro, binding.etSubparcelaLet, binding.etUf,
            // Referencia Acta
            binding.etRefActa
        )

        inputs.forEach { editText ->
            editText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    (view as? EditText)?.selectAll()
                    ejecutarAutoScroll(view)
                    view.setBackgroundResource(R.drawable.bg_edittext_focused)
                } else {
                    view.setBackgroundResource(0)
                }
            }
        }
    }

    private fun ejecutarAutoScroll(view: View) {
        view.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            // Intentar encontrar el TextInputLayout para incluir el label en el scroll
            val parentInput = (view.parent?.parent as? TextInputLayout) ?: (view.parent as? TextInputLayout) ?: view
            val rect = Rect()
            parentInput.getDrawingRect(rect)

            try {
                binding.scrollProcedimiento.offsetDescendantRectToMyCoords(parentInput, rect)
                // Scrolleamos para que el campo quede en la parte superior con un margen generoso (90dp)
                val margin = (90 * resources.displayMetrics.density).toInt()
                val scrollY = (rect.top - margin).coerceAtLeast(0)
                binding.scrollProcedimiento.smoothScrollTo(0, scrollY)
            } catch (e: Exception) {
                // Fallback si falla la conversión de coordenadas
            }
        }, 500) // Delay estandarizado de 500ms
    }
    private fun recuperarDatos() {
        // 1. Sincronizar ID si solo tenemos el texto o viceversa
        val idActual = actaViewModel.idTipoActaSeleccionada
        val nombreActual = actaViewModel.procAccion

        if (idActual != null) {
            val tipoPorId = actaViewModel.listaTiposActaDisponibles.find { it.id == idActual }
            if (tipoPorId != null) {
                actaViewModel.procAccion = tipoPorId.nombre
                binding.spinnerAccion.setText(tipoPorId.nombre, false)
            }
        } else if (nombreActual.isNotEmpty()) {
            val tipoPorNombre = actaViewModel.listaTiposActaDisponibles.find { it.nombre.equals(nombreActual, ignoreCase = true) }
            if (tipoPorNombre != null) {
                actaViewModel.idTipoActaSeleccionada = tipoPorNombre.id
                binding.spinnerAccion.setText(tipoPorNombre.nombre, false)
            }
        }

        // 4. Forzamos la sincronización de los botones tildados físicamente en las vistas
        for (i in 0 until binding.containerCategorias.childCount) {
            val child = binding.containerCategorias.getChildAt(i)
            if (child is MaterialButton) {
                val nombreBoton = child.text.toString()
                val estaMarcado = actaViewModel.categoriasSeleccionadas.any { seleccionada ->
                    normalizarTexto(seleccionada) == normalizarTexto(nombreBoton) ||
                    seleccionada == (actaViewModel.catalogoCategorias.find { it.nombre == nombreBoton }?.id?.toString() ?: "")
                }
                child.isChecked = estaMarcado
            }
        }

        // Resto del recuperarDatos...
        binding.etRefActa.setText(actaViewModel.procRefActa)

        binding.etNombreFantasia.setText(actaViewModel.comNombreFantasia)
        binding.etHabNumero.setText(actaViewModel.comHabNumero)
        binding.etRubro.setText(actaViewModel.comRubro)

        binding.etCirc.setText(actaViewModel.catCirc)
        binding.etSeccion.setText(actaViewModel.catSeccion)
        binding.etChacraNro.setText(actaViewModel.catChacraNro)
        binding.etChacraLet.setText(actaViewModel.catChacraLet)
        binding.etQuintaNro.setText(actaViewModel.catQuintaNro)
        binding.etQuintaLet.setText(actaViewModel.catQuintaLet)
        binding.etFraccionNro.setText(actaViewModel.catFraccionNro)
        binding.etFraccionLet.setText(actaViewModel.catFraccionLet)
        binding.etManzanaNro.setText(actaViewModel.catManzanaNro)
        binding.etManzanaLet.setText(actaViewModel.catManzanaLet)
        binding.etParcelaNro.setText(actaViewModel.catParcelaNro)
        binding.etParcelaLet.setText(actaViewModel.catParcelaLet)
        binding.etSubparcelaNro.setText(actaViewModel.catSubparcelaNro)
        binding.etSubparcelaLet.setText(actaViewModel.catSubparcelaLet)
        binding.etUf.setText(actaViewModel.catUF)
        binding.etPartidaMunicipal.setText(actaViewModel.catPartidaMunicipal)

        actualizarVisibilidadCatastro()
    }

    private fun guardarDatos() {
        val accionActual = binding.spinnerAccion.text.toString().trim()
        actaViewModel.procAccion = accionActual

        // 🚀 Lógica de seguridad: Solo guardar referencia si el tipo de acta lo requiere
        val tipoEncontrado = actaViewModel.listaTiposActaDisponibles
            .find { it.id == actaViewModel.idTipoActaSeleccionada || it.nombre.equals(accionActual, ignoreCase = true) }
        
        val requiereFaltas = tipoEncontrado?.requiereFaltas ?: false
        
        actaViewModel.procRefActa = if (requiereFaltas) {
            binding.etRefActa.text.toString().trim()
        } else {
            ""
        }

        // 🚀 Guardar las categorías seleccionadas unidas por coma para tipoInmueble
        actaViewModel.procTipoInmueble = actaViewModel.categoriasSeleccionadas.joinToString(", ")

        // Guardar Campos Comercio
        actaViewModel.comNombreFantasia = binding.etNombreFantasia.text.toString().trim()
        actaViewModel.comHabNumero = binding.etHabNumero.text.toString().trim()
        actaViewModel.comRubro = binding.etRubro.text.toString().trim()

        // Guardar Campos Catastrales
        actaViewModel.catCirc = binding.etCirc.text.toString().trim()
        actaViewModel.catSeccion = binding.etSeccion.text.toString().trim()
        actaViewModel.catChacraNro = binding.etChacraNro.text.toString().trim()
        actaViewModel.catChacraLet = binding.etChacraLet.text.toString().trim()
        actaViewModel.catQuintaNro = binding.etQuintaNro.text.toString().trim()
        actaViewModel.catQuintaLet = binding.etQuintaLet.text.toString().trim()
        actaViewModel.catFraccionNro = binding.etFraccionNro.text.toString().trim()
        actaViewModel.catFraccionLet = binding.etFraccionLet.text.toString().trim()
        actaViewModel.catManzanaNro = binding.etManzanaNro.text.toString().trim()
        actaViewModel.catManzanaLet = binding.etManzanaLet.text.toString().trim()
        actaViewModel.catParcelaNro = binding.etParcelaNro.text.toString().trim()
        actaViewModel.catParcelaLet = binding.etParcelaLet.text.toString().trim()
        actaViewModel.catSubparcelaNro = binding.etSubparcelaNro.text.toString().trim()
        actaViewModel.catSubparcelaLet = binding.etSubparcelaLet.text.toString().trim()
        actaViewModel.catUF = binding.etUf.text.toString().trim()
        actaViewModel.catPartidaMunicipal = binding.etPartidaMunicipal.text.toString().trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun crearAdapterLimitado(lista: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_dropdown,
            lista.toMutableList()
        ) {
            private val listaOriginal = ArrayList(lista)

            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        val query = constraint?.toString()?.trim()?.lowercase() ?: ""
                        
                        val listaFiltrada = if (query.isEmpty()) {
                            listaOriginal
                        } else {
                            listaOriginal.filter {
                                it.lowercase().contains(query)
                            }
                        }
                        
                        results.values = listaFiltrada
                        results.count = listaFiltrada.size
                        return results
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        clear()
                        if (results != null && results.count > 0) {
                            addAll(results.values as List<String>)
                            notifyDataSetChanged()
                        } else {
                            notifyDataSetInvalidated()
                        }
                    }
                }
            }
        }
    }
}