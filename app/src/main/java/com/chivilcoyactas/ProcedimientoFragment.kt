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

        // Lógica de carga dinámica de categorías
        if (actaViewModel.catalogoCategorias.isEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val categoriasDb = AppDatabase.getDatabase(requireContext()).categoriaDao().obtenerTodas()
                actaViewModel.catalogoCategorias = categoriasDb
                renderizarCheckboxes(categoriasDb)
            }
        } else {
            renderizarCheckboxes(actaViewModel.catalogoCategorias)
        }

        // Configurar el Spinner de Acciones
        val acciones = arrayOf("Inspección General", "Notificación", "Clausura Preventiva", "Cese de Actividad")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, acciones)
        binding.spinnerAccion.setAdapter(adapter)

        recuperarDatos()

        binding.btnVolverProcedimiento.setOnClickListener {
            findNavController().navigateUp() // Más seguro que usar el ID de acción
        }

        binding.btnSiguienteProcedimiento.setOnClickListener {
            if (actaViewModel.categoriasSeleccionadas.isEmpty()) {
                Toast.makeText(context, "Debe seleccionar al menos una categoría", Toast.LENGTH_SHORT).show()
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

    private fun renderizarCheckboxes(lista: List<CategoriaEntity>) {
        binding.containerCheckboxes.removeAllViews()
        lista.forEach { categoria ->
            val cb = CheckBox(requireContext()).apply {
                text = categoria.nombre
                isChecked = actaViewModel.categoriasSeleccionadas.contains(categoria.nombre)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!actaViewModel.categoriasSeleccionadas.contains(categoria.nombre)) {
                            actaViewModel.categoriasSeleccionadas.add(categoria.nombre)
                        }
                    } else {
                        actaViewModel.categoriasSeleccionadas.remove(categoria.nombre)
                    }
                    // --- AGREGADO: Actualiza el Card cada vez que tocan un check ---
                    actualizarVisibilidadCatastro()
                }
            }
            binding.containerCheckboxes.addView(cb)
        }
        // --- AGREGADO: Ejecutamos una vez al cargar por si ya había algo seleccionado ---
        actualizarVisibilidadCatastro()
    }

    private fun actualizarVisibilidadCatastro() {
        val categoriasCatastro = listOf("Comercio / Local", "Vereda / Terreno", "Obra en Construcción", "Baldío")
        val mostrarCatastro = actaViewModel.categoriasSeleccionadas.any { it in categoriasCatastro }
        binding.cardCatastro.visibility = if (mostrarCatastro) View.VISIBLE else View.GONE

        // 2. Lógica para Comercio (Solo un disparador específico)
        val mostrarComercio = actaViewModel.categoriasSeleccionadas.contains("Comercio / Local")
        binding.cardComercio.visibility = if (mostrarComercio) View.VISIBLE else View.GONE

        // 3. Actualizamos el flujo de focos según los cambios de visibilidad de arriba
        actualizarFlujoDeFocos()
    }

    private fun actualizarFlujoDeFocos() {
        // Verificamos si la tarjeta de catastro quedó visible o no
        val esCatastroVisible = binding.cardCatastro.visibility == View.VISIBLE

        if (esCatastroVisible) {
            // Si Catastro está visible, el "Siguiente" de Rubro salta a Circunscripción
            binding.etRubro.nextFocusForwardId = binding.etCirc.id
        } else {
            // Si Catastro está oculto, el "Siguiente" de Rubro salta directo a Referencia Acta
            binding.etRubro.nextFocusForwardId = binding.etRefActa.id
        }
    }

    private fun recuperarDatos() {
        binding.spinnerAccion.setText(actaViewModel.procAccion, false)
        binding.etRefActa.setText(actaViewModel.procRefActa)
        binding.etDetalleProcedimiento.setText(actaViewModel.procSeProcedeA)

        //Recuperar Campos Comercio
        binding.etNombreFantasia.setText(actaViewModel.comNombreFantasia)
        binding.etHabNumero.setText(actaViewModel.comHabNumero)
        binding.etRubro.setText(actaViewModel.comRubro)

        // Recuperar Campos Catastrales
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
        actaViewModel.procAccion = binding.spinnerAccion.text.toString()
        actaViewModel.procRefActa = binding.etRefActa.text.toString().trim()
        actaViewModel.procSeProcedeA = binding.etDetalleProcedimiento.text.toString().trim()

        //Guardar Campos Comercio
        actaViewModel.comNombreFantasia = binding.etNombreFantasia.text.toString().trim()
        actaViewModel.comHabNumero = binding.etHabNumero.text.toString().trim()
        actaViewModel.comRubro = binding.etRubro.text.toString().trim()

        // Guardar Campos Catastrales
        actaViewModel.catCirc = binding.etCirc.text.toString()
        actaViewModel.catSeccion = binding.etSeccion.text.toString()
        actaViewModel.catChacraNro = binding.etChacraNro.text.toString()
        actaViewModel.catChacraLet = binding.etChacraLet.text.toString()
        actaViewModel.catQuintaNro = binding.etQuintaNro.text.toString()
        actaViewModel.catQuintaLet = binding.etQuintaLet.text.toString()
        actaViewModel.catFraccionNro = binding.etFraccionNro.text.toString()
        actaViewModel.catFraccionLet = binding.etFraccionLet.text.toString()
        actaViewModel.catManzanaNro = binding.etManzanaNro.text.toString()
        actaViewModel.catManzanaLet = binding.etManzanaLet.text.toString()
        actaViewModel.catParcelaNro = binding.etParcelaNro.text.toString()
        actaViewModel.catParcelaLet = binding.etParcelaLet.text.toString()
        actaViewModel.catSubparcela = binding.etSubparcela.text.toString()
        actaViewModel.catUF = binding.etUF.text.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}