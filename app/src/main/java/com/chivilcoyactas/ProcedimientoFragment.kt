package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
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
        (activity as? MainActivity)?.actualizarProgreso(2)

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            // 1. Cargar las categorías de la base de datos (o sembrar si está vacía)
            if (actaViewModel.catalogoCategorias.isEmpty()) {
                var categoriasDb = db.catalogoDao().obtenerTodasLasCategorias()

                if (categoriasDb.isEmpty()) {
                    val categoriasSemilla = listOf(
                        CategoriaEntity(nombre = "Baldío"),
                        CategoriaEntity(nombre = "Comercio / Local"),
                        CategoriaEntity(nombre = "Higiene Urbana"),
                        CategoriaEntity(nombre = "Obra en Construcción"),
                        CategoriaEntity(nombre = "Vereda / Terreno"),
                        CategoriaEntity(nombre = "Vía Pública / Ruidos")
                    )
                    db.catalogoDao().insertarCategorias(categoriasSemilla)
                    categoriasDb = db.catalogoDao().obtenerTodasLasCategorias()
                }
                actaViewModel.catalogoCategorias = categoriasDb
            }

            // 2. Renderizar dinámicamente los Checkboxes
            renderizarCheckboxes(actaViewModel.catalogoCategorias)

            // 3. Configurar el Spinner de Acciones (Tipos de Acta)
            setupSpinnerAcciones()

            // 4. Volcar y recuperar los datos en los controles de la vista
            recuperarDatos()
        }

        binding.btnVolverProcedimiento.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSiguienteProcedimiento.setOnClickListener {
            // Validar categorías tildadas
            if (actaViewModel.categoriasSeleccionadas.isEmpty()) {
                Toast.makeText(context, "Debe seleccionar al menos una categoría", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validar que se haya seleccionado un Tipo de Acta o escrito una acción válida
            val accionTexto = binding.spinnerAccion.text.toString().trim()
            if (actaViewModel.idTipoActaSeleccionada == null && accionTexto.isEmpty()) {
                Toast.makeText(context, "Debe seleccionar un tipo de acción/acta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            guardarDatos()
            findNavController().navigate(R.id.action_procedimiento_to_infractor)
        }

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollProcedimiento.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    private fun setupSpinnerAcciones() {
        val tiposActaInspeccion = actaViewModel.listaTiposActaDisponibles

        if (tiposActaInspeccion.isNotEmpty()) {
            val nombresAcciones = tiposActaInspeccion.map { it.nombre }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresAcciones)
            binding.spinnerAccion.setAdapter(adapter)

            // Al seleccionar una opción, guardamos el ID real de la BD y actualizamos el ViewModel
            binding.spinnerAccion.setOnItemClickListener { _, _, position, _ ->
                val tipoSeleccionado = tiposActaInspeccion[position]
                actaViewModel.idTipoActaSeleccionada = tipoSeleccionado.id
                actaViewModel.procAccion = tipoSeleccionado.nombre
            }
        } else {
            Toast.makeText(requireContext(), "No hay tipos de acta configurados para esta repartición", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderizarCheckboxes(lista: List<CategoriaEntity>) {
        binding.containerCheckboxes.removeAllViews()

        lista.forEach { categoria ->
            val nombreLimpio = categoria.nombre.trim()
            val cb = CheckBox(requireContext()).apply {
                text = nombreLimpio
                textSize = 15f
                setPadding(12, 12, 12, 12)

                // 🔹 Verificación flexible: coincide ignorando espacios extras y mayúsculas
                isChecked = actaViewModel.categoriasSeleccionadas.any { seleccionada ->
                    normalizarTexto(seleccionada) == normalizarTexto(nombreLimpio)
                }

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        val yaExiste = actaViewModel.categoriasSeleccionadas.any {
                            normalizarTexto(it) == normalizarTexto(nombreLimpio)
                        }
                        if (!yaExiste) {
                            actaViewModel.categoriasSeleccionadas.add(nombreLimpio)
                        }
                    } else {
                        actaViewModel.categoriasSeleccionadas.removeAll {
                            normalizarTexto(it) == normalizarTexto(nombreLimpio)
                        }
                    }
                    actualizarVisibilidadCatastro()
                }
            }
            binding.containerCheckboxes.addView(cb)
        }
        actualizarVisibilidadCatastro()
    }

    // Función auxiliar para comparar nombres de categorías de forma segura
    private fun normalizarTexto(texto: String): String {
        return texto.trim().replace("\\s+".toRegex(), " ").lowercase()
    }

    private fun actualizarVisibilidadCatastro() {
        val categoriasCatastro = listOf("Baldío", "Comercio / Local", "Higiene Urbana", "Obra en Construcción", "Vereda / Terreno", "Vía Pública / Ruidos")

        // Comparación insensible a mayúsculas/espacios
        val mostrarCatastro = actaViewModel.categoriasSeleccionadas.any { seleccionada ->
            categoriasCatastro.any { it.equals(seleccionada.trim(), ignoreCase = true) }
        }
        binding.cardCatastro.visibility = if (mostrarCatastro) View.VISIBLE else View.GONE

        val mostrarComercio = actaViewModel.categoriasSeleccionadas.any {
            it.trim().equals("Comercio / Local", ignoreCase = true)
        }
        binding.cardComercio.visibility = if (mostrarComercio) View.VISIBLE else View.GONE

        actualizarFlujoDeFocos()
    }

    private fun actualizarFlujoDeFocos() {
        val esCatastroVisible = binding.cardCatastro.visibility == View.VISIBLE

        if (esCatastroVisible) {
            binding.etRubro.nextFocusForwardId = binding.etCirc.id
        } else {
            binding.etRubro.nextFocusForwardId = binding.etRefActa.id
        }
    }

    private fun recuperarDatos() {
        // 1. Si no tenemos ID pero tenemos el texto recuperado de la BD, buscamos su ID en la lista
        if (actaViewModel.idTipoActaSeleccionada == null && actaViewModel.procAccion.isNotEmpty()) {
            val tipoEncontrado = actaViewModel.listaTiposActaDisponibles.find {
                it.nombre.trim().equals(actaViewModel.procAccion.trim(), ignoreCase = true)
            }
            if (tipoEncontrado != null) {
                actaViewModel.idTipoActaSeleccionada = tipoEncontrado.id
            }
        }

        // 2. Seteamos el texto en el Spinner según el ID o el texto guardado
        val tipoActual = actaViewModel.listaTiposActaDisponibles.find { it.id == actaViewModel.idTipoActaSeleccionada }
        if (tipoActual != null) {
            binding.spinnerAccion.setText(tipoActual.nombre, false)
        } else if (actaViewModel.procAccion.isNotEmpty()) {
            binding.spinnerAccion.setText(actaViewModel.procAccion, false)
        }

        // 3. Forzamos la sincronización de los Checkboxes tildados físicamente en las vistas
        for (i in 0 until binding.containerCheckboxes.childCount) {
            val child = binding.containerCheckboxes.getChildAt(i)
            if (child is CheckBox) {
                val nombreCb = child.text.toString()
                val estaMarcado = actaViewModel.categoriasSeleccionadas.any {
                    normalizarTexto(it) == normalizarTexto(nombreCb)
                }
                child.isChecked = estaMarcado
            }
        }

        // Resto del recuperarDatos...
        binding.etRefActa.setText(actaViewModel.procRefActa)
        binding.etDetalleProcedimiento.setText(actaViewModel.procSeProcedeA)

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
        binding.etSubparcela.setText(actaViewModel.catSubparcela)
        binding.etUF.setText(actaViewModel.catUF)

        actualizarVisibilidadCatastro()
    }

    private fun guardarDatos() {
        actaViewModel.procAccion = binding.spinnerAccion.text.toString().trim()
        actaViewModel.procRefActa = binding.etRefActa.text.toString().trim()
        actaViewModel.procSeProcedeA = binding.etDetalleProcedimiento.text.toString().trim()

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
        actaViewModel.catSubparcela = binding.etSubparcela.text.toString().trim()
        actaViewModel.catUF = binding.etUF.text.toString().trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}