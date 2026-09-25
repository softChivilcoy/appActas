package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentAlcoholemiaBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class AlcoholemiaFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentAlcoholemiaBinding? = null
    private val binding get() = _binding!!

    // Lista de alcoholímetros disponibles (puedes reemplazar esto con los datos reales de la BD/SessionManager)

    private var listaEquipos: List<AlcoholimetrosEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlcoholemiaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.actualizarProgreso(5)

        // 1. Cargar catálogo de equipos y configurar el Spinner
        configurarDropdownEquipos()

        // 2. Cargar estado guardado en ViewModel
        recuperarDatos()

        // 3. Configurar Listeners de UI
        setupListeners()

        // 4. Configuración de Teclado y Scroll
        configurarTecladoYFocus()
    }

    private fun configurarDropdownEquipos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext().applicationContext)
            val equiposDb = db.catalogoDao().obtenerAlcoholimetros()

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext

                listaEquipos = equiposDb

                if (listaEquipos.isNotEmpty()) {
                    // Formato para diferenciar equipos de la misma marca
                    val itemsDropdown = listaEquipos.map { "${it.marca ?: "Sin Marca"} (Serie: ${it.nroSerie ?: "S/C"})" }

                    val context = context ?: return@withContext
                    val adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        itemsDropdown
                    )
                    binding.spinnerAlcoMarca.setAdapter(adapter)

                    // Listener al seleccionar un equipo manualmente del catálogo
                    binding.spinnerAlcoMarca.setOnItemClickListener { _, _, position, _ ->
                        if (position in listaEquipos.indices) {
                            val equipoSeleccionado = listaEquipos[position]

                            // 1. Muestra solo la marca limpia en el campo de texto
                            binding.spinnerAlcoMarca.setText(equipoSeleccionado.marca ?: "", false)

                            // 2. Autocompleta modelo, serie y homologación
                            autocompletarEquipo(equipoSeleccionado)
                        }
                    }

                    // Si no había selección en SessionManager, intentamos restaurar por ID en ViewModel
                    if (SessionManager.obtenerAlcoholimetro(requireContext()) == null) {
                        restaurarSeleccionPrevia()
                    }
                }
            }
        }
    }

    private fun restaurarSeleccionPrevia() {
        val idGuardado = actaViewModel.alcoholimetroId
        if (idGuardado != null && idGuardado > 0) {
            val equipoGuardado = listaEquipos.find { it.id == idGuardado }
            equipoGuardado?.let { equipo ->
                val marca = equipo.marca ?: ""

                // Muestra ÚNICAMENTE la marca en el combo sin desplegar la lista
                binding.spinnerAlcoMarca.setText(marca, false)

                // Completa automáticamente los demás campos en la pantalla
                binding.etAlcoModelo.setText(equipo.modelo ?: "")
                binding.etAlcoSerie.setText(equipo.nroSerie ?: "")
                binding.etAlcoAprobacion.setText(equipo.codHomologacion ?: "")
            }
        }
    }


    private fun autocompletarEquipo(equipo: AlcoholimetrosEntity) {
        binding.apply {
            etAlcoModelo.setText(equipo.modelo)
            etAlcoSerie.setText(equipo.nroSerie)
            etAlcoAprobacion.setText(equipo.codHomologacion)
        }

        // Persistir en ViewModel los datos y el ID
        actaViewModel.alcoholimetroId = equipo.id
        actaViewModel.alcoMarca = equipo.marca
        actaViewModel.alcoModelo = equipo.modelo
        actaViewModel.alcoSerie = equipo.nroSerie
        actaViewModel.alcoAprobacion = equipo.codHomologacion
    }

    private fun setupListeners() {
        // Test de Alcoholemia Toggle
        binding.checkHacerTest.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.hacerTestAlcoholemia = isChecked
            binding.cardTest.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                binding.scrollAlcoholemia.postDelayed({
                    binding.scrollAlcoholemia.smoothScrollTo(0, binding.cardTest.top)
                }, 100)
            } else {
                limpiarCamposTest()
            }
        }

        binding.checkPlanillaMedica.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.seAdjuntaPlanillaMedica = isChecked
        }

        // Medidas Preventivas
        binding.checkRetencionVehiculo.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.retencionVehiculo = isChecked
        }
        binding.checkRetencionLicencia.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.retencionLicencia = isChecked
        }
        binding.checkAnimal.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.retencionAnimal = isChecked
        }

        setupErrorClearing()

        // Botones de Navegación
        binding.btnVolverAlco.setOnClickListener { findNavController().navigateUp() }
        binding.btnSiguienteAlco.setOnClickListener { procesarSiguientePaso() }
    }

    private fun setupErrorClearing() {
        binding.spinnerAlcoMarca.doOnTextChanged { _, _, _, _ -> binding.spinnerAlcoMarca.error = null }
        binding.etResultado.doOnTextChanged { _, _, _, _ -> binding.etResultado.error = null }
    }

    private fun procesarSiguientePaso() {
        if (binding.checkHacerTest.isChecked) {
            if (!validarFormularioTest()) return
        }

        guardarEnViewModel()

        if (binding.checkRetencionVehiculo.isChecked) {
            evaluarFlujoSecuestro()
        } else {
            findNavController().navigate(R.id.action_alcoholemia_to_testigos)
        }
    }

    private fun validarFormularioTest(): Boolean {
        var esValido = true

        if (binding.spinnerAlcoMarca.text.toString().trim().isEmpty()) {
            binding.spinnerAlcoMarca.error = "Seleccione una marca"
            esValido = false
        }
        if (binding.etResultado.text.toString().trim().isEmpty()) {
            binding.etResultado.error = "Ingrese el resultado (grs/l)"
            esValido = false
        }

        if (!esValido) {
            Toast.makeText(requireContext(), "Complete los campos obligatorios del test", Toast.LENGTH_SHORT).show()
        }
        return esValido
    }

    private fun evaluarFlujoSecuestro() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Retención Vehicular")
            .setMessage("¿Desea realizar el inventario de secuestro ahora o derivarlo al Inspector B?")
            .setCancelable(false)
            .setPositiveButton("Hacer Ahora") { dialog, _ ->
                dialog.dismiss()
                actaViewModel.modoSecuestroDerivado = false
                findNavController().navigate(R.id.action_alcoholemia_to_secuestro)
            }
            .setNegativeButton("Derivar a Inspector B") { dialog, _ ->
                dialog.dismiss()
                actaViewModel.modoSecuestroDerivado = true
                findNavController().navigate(R.id.action_alcoholemia_to_testigos)
            }
            .show()
    }

    private fun guardarEnViewModel() {
        val textoCombo = binding.spinnerAlcoMarca.text.toString().trim()

        // Si viene en formato "Marca (Serie: XXX)", extraemos solo la Marca
        val marcaLimpia = if (textoCombo.contains(" (Serie:")) {
            textoCombo.substringBefore(" (Serie:")
        } else {
            textoCombo
        }

        actaViewModel.alcoMarca = marcaLimpia
        actaViewModel.alcoModelo = binding.etAlcoModelo.text.toString().trim()
        actaViewModel.alcoSerie = binding.etAlcoSerie.text.toString().trim()
        actaViewModel.alcoAprobacion = binding.etAlcoAprobacion.text.toString().trim()

        val resultadoStr = binding.etResultado.text.toString().trim().replace(",", ".")
        actaViewModel.ftResultado = resultadoStr.toDoubleOrNull() ?: 0.0
    }

    private fun recuperarDatos() {
        // 1. Obtener el Mapa cargado desde SessionManager
        val equipoSession   = SessionManager.obtenerAlcoholimetro(requireContext())
        val marcaRaw        = equipoSession["marca"] ?: ""
        val modelo          = equipoSession["modelo"] ?: ""
        val serie           = equipoSession["serie"] ?: ""
        val codHomologacion = equipoSession["codHomologacion"] ?: ""

        // Si por alguna razón la marca venía guardada como "Marca (Serie: XXX)", extraemos solo la marca
        val marcaLimpia = if (marcaRaw.contains(" (Serie:")) {
            marcaRaw.substringBefore(" (Serie:")
        } else {
            marcaRaw
        }

        // Verificamos si realmente hay un equipo cargado (si la marca o la serie no están vacías)
        if (marcaLimpia.isNotEmpty() || serie.isNotEmpty()) {
            binding.checkHacerTest.isChecked = true
            binding.cardTest.visibility = View.VISIBLE

            // 👈 Setea ÚNICAMENTE la marca limpia en la vista
            binding.spinnerAlcoMarca.setText(marcaLimpia, false)

            binding.etAlcoModelo.setText(modelo)
            binding.etAlcoSerie.setText(serie)
            binding.etAlcoAprobacion.setText(codHomologacion)

            // Guardar en el ViewModel
            actaViewModel.hacerTestAlcoholemia = true
            actaViewModel.alcoMarca = marcaLimpia
            actaViewModel.alcoModelo = modelo
            actaViewModel.alcoSerie = serie
            actaViewModel.alcoAprobacion = codHomologacion
        } else {
            // Carga normal desde ViewModel si no vino nada en SessionManager
            binding.checkHacerTest.isChecked = actaViewModel.hacerTestAlcoholemia
            binding.cardTest.visibility = if (actaViewModel.hacerTestAlcoholemia) View.VISIBLE else View.GONE

            binding.spinnerAlcoMarca.setText(actaViewModel.alcoMarca, false)
            binding.etAlcoModelo.setText(actaViewModel.alcoModelo)
            binding.etAlcoSerie.setText(actaViewModel.alcoSerie)
            binding.etAlcoAprobacion.setText(actaViewModel.alcoAprobacion)
        }

        // Cargar resultado numérico y checkboxes
        if (actaViewModel.ftResultado > 0.0) {
            binding.etResultado.setText(actaViewModel.ftResultado.toString())
        } else {
            binding.etResultado.setText("")
        }

        binding.checkPlanillaMedica.isChecked = actaViewModel.seAdjuntaPlanillaMedica
        binding.checkRetencionVehiculo.isChecked = actaViewModel.retencionVehiculo
        binding.checkRetencionLicencia.isChecked = actaViewModel.retencionLicencia
        binding.checkAnimal.isChecked = actaViewModel.retencionAnimal
    }

    private fun limpiarCamposTest() {
        binding.checkPlanillaMedica.isChecked = false
        actaViewModel.seAdjuntaPlanillaMedica = false
        binding.spinnerAlcoMarca.text?.clear()
        binding.etAlcoModelo.text?.clear()
        binding.etAlcoSerie.text?.clear()
        binding.etAlcoAprobacion.text?.clear()
        binding.etResultado.text?.clear()

        actaViewModel.alcoMarca = ""
        actaViewModel.alcoModelo = ""
        actaViewModel.alcoSerie = ""
        actaViewModel.alcoAprobacion = ""
        actaViewModel.ftResultado = 0.0
    }

    private fun configurarTecladoYFocus() {
        binding.scrollAlcoholemia.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        val views = listOf(binding.spinnerAlcoMarca, binding.etResultado)
        views.forEach { view ->
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    binding.scrollAlcoholemia.postDelayed({
                        val rect = android.graphics.Rect(0, 0, v.width, v.height)
                        v.requestRectangleOnScreen(rect, false)
                    }, 300)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}