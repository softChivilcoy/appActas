package com.chivilcoyactas

import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chivilcoyactas.databinding.FragmentAlcoholemiaBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlcoholemiaFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentAlcoholemiaBinding? = null
    private val binding get() = _binding!!

    private var listaEquipos: List<AlcoholimetrosEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlcoholemiaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Forzar scroll al inicio al entrar o volver
        binding.scrollAlcoholemia.post {
            binding.scrollAlcoholemia.scrollTo(0, 0)
        }

        // Aplicar padding dinámico según el teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollAlcoholemia) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val density = resources.displayMetrics.density
            val extraPadding = (32 * density).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeHeight + extraPadding)
            insets
        }

        (activity as? MainActivity)?.actualizarProgreso(5)

        // 1. Ocultar o Mostrar el Test de Alcoholemia según el Tipo de Formulario
        evaluarVisibilidadAlcoholemia()

        // 2. Renderizar dinámicamente los Bienes de la Repartición seleccionada
        generarCheckboxesBienes()

        // 3. Cargar catálogo de equipos y configurar el Spinner
        if (actaViewModel.tipoFormulario != TipoFormulario.INSPECCION) {
            configurarDropdownEquipos()
        }

        // 4. Cargar estado guardado en ViewModel
        recuperarDatos()

        // 5. Configurar Listeners de UI
        setupListeners()

        // 6. Configuración de Teclado y Scroll
        configurarTecladoYFocus()
    }

    private fun evaluarVisibilidadAlcoholemia() {
        val esInspeccion = actaViewModel.tipoFormulario == TipoFormulario.INSPECCION

        if (esInspeccion) {
            binding.checkHacerTest.visibility = View.GONE
            binding.cardTest.visibility = View.GONE
            actaViewModel.hacerTestAlcoholemia = false
        } else {
            binding.checkHacerTest.visibility = View.VISIBLE
        }
    }

    private fun generarCheckboxesBienes() {
        val container = binding.containerBienesPreventivos
        container.removeAllViews()

        val bienes = actaViewModel.listaTiposBienesDisponibles

        if (bienes.isEmpty()) return

        bienes.forEach { bien ->
            val itemLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            val esVehiculo = bien.nombre.contains("Vehiculo", ignoreCase = true) ||
                    bien.nombre.contains("Vehículo", ignoreCase = true) ||
                    bien.nombre.contains("Auto", ignoreCase = true) ||
                    bien.nombre.contains("Moto", ignoreCase = true)

            // 1. CheckBox
            val checkBox = MaterialCheckBox(requireContext()).apply {
                id = View.generateViewId()
                text = "Retención / Secuestro de ${bien.nombre}"
                textSize = 15f
                tag = bien.id

                isChecked = actaViewModel.bienesSeleccionadosIds.contains(bien.id)

                // Scroll suave al hacer foco
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        binding.scrollAlcoholemia.post {
                            val positionY = container.top + itemLayout.top
                            binding.scrollAlcoholemia.smoothScrollTo(0, positionY)
                        }
                    }
                }
            }

            // 2. Campo de Texto (TextInputLayout + TextInputEditText) - Solo si NO es vehículo
            val textInputLayout = if (!esVehiculo) {
                TextInputLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    hint = "Observaciones / Descripción de ${bien.nombre}"
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    visibility = if (checkBox.isChecked) View.VISIBLE else View.GONE

                    val editText = TextInputEditText(context).apply {
                        setText(actaViewModel.bienesDescripciones[bien.id] ?: "")
                        isSingleLine = true
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        setSelectAllOnFocus(true)

                        // Scroll suave al enfocar
                        setOnFocusChangeListener { v, hasFocus ->
                            if (hasFocus) {
                                ejecutarAutoScroll(v)
                                v.setBackgroundResource(R.drawable.bg_edittext_focused)
                            } else {
                                v.setBackgroundResource(0)
                            }
                        }

                        // 🚀 Al presionar Enter / Listo: ocultar teclado y quitar foco
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                                (activity as? MainActivity)?.hideKeyboard()
                                clearFocus()
                                true
                            } else {
                                false
                            }
                        }

                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                if (checkBox.isChecked) {
                                    actaViewModel.bienesDescripciones[bien.id] = s.toString()
                                }
                            }
                            override fun afterTextChanged(s: android.text.Editable?) {}
                        })
                    }
                    addView(editText)
                }
            } else null

            // 3. Lógica al marcar / desmarcar
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (!actaViewModel.bienesSeleccionadosIds.contains(bien.id)) {
                        actaViewModel.bienesSeleccionadosIds.add(bien.id)
                    }
                    textInputLayout?.visibility = View.VISIBLE

                    textInputLayout?.editText?.let { input ->
                        input.requestFocus()
                        (activity as? MainActivity)?.mostrarTeclado(input)
                    }
                } else {
                    actaViewModel.bienesSeleccionadosIds.remove(bien.id)
                    actaViewModel.bienesDescripciones.remove(bien.id)
                    textInputLayout?.visibility = View.GONE
                }

                if (esVehiculo) {
                    actaViewModel.retencionVehiculo = isChecked
                }
            }

            itemLayout.addView(checkBox)
            textInputLayout?.let { itemLayout.addView(it) }

            container.addView(itemLayout)
        }
    }

    private fun configurarDropdownEquipos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext().applicationContext)
            val equiposDb = db.catalogoDao().obtenerAlcoholimetros()

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext

                listaEquipos = equiposDb

                if (listaEquipos.isNotEmpty()) {
                    val itemsDropdown = listaEquipos.map { "${it.marca ?: "Sin Marca"} (Serie: ${it.nroSerie ?: "S/C"})" }

                    val context = context ?: return@withContext
                    val adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        itemsDropdown
                    )
                    binding.spinnerAlcoMarca.setAdapter(adapter)

                    binding.spinnerAlcoMarca.setOnItemClickListener { _, _, position, _ ->
                        if (position in listaEquipos.indices) {
                            val equipoSeleccionado = listaEquipos[position]
                            binding.spinnerAlcoMarca.setText(equipoSeleccionado.marca ?: "", false)
                            autocompletarEquipo(equipoSeleccionado)

                            // Pasar foco al resultado automáticamente
                            binding.etResultado.requestFocus()
                            (activity as? MainActivity)?.mostrarTeclado(binding.etResultado)
                        }
                    }

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
                binding.spinnerAlcoMarca.setText(marca, false)
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

        actaViewModel.alcoholimetroId = equipo.id
        actaViewModel.alcoMarca = equipo.marca
        actaViewModel.alcoModelo = equipo.modelo
        actaViewModel.alcoSerie = equipo.nroSerie
        actaViewModel.alcoAprobacion = equipo.codHomologacion
    }

    private fun setupListeners() {
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

        setupErrorClearing()

        binding.etResultado.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                (activity as? MainActivity)?.hideKeyboard()
                binding.etResultado.clearFocus()
                true
            } else {
                false
            }
        }

        binding.btnVolverAlco.setOnClickListener { findNavController().navigateUp() }
        binding.btnSiguienteAlco.setOnClickListener { procesarSiguientePaso() }
    }

    private fun setupErrorClearing() {
        binding.spinnerAlcoMarca.doOnTextChanged { _, _, _, _ -> binding.spinnerAlcoMarca.error = null }
        binding.etResultado.doOnTextChanged { _, _, _, _ -> binding.etResultado.error = null }
    }

    private fun procesarSiguientePaso() {
        if (binding.checkHacerTest.visibility == View.VISIBLE && binding.checkHacerTest.isChecked) {
            if (!validarFormularioTest()) return
        }

        guardarEnViewModel()

        // Si se seleccionó retención de vehículo en los CheckBoxes dinámicos
        if (actaViewModel.retencionVehiculo) {
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
        if (binding.checkHacerTest.visibility == View.VISIBLE) {
            val textoCombo = binding.spinnerAlcoMarca.text.toString().trim()
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
    }

    private fun recuperarDatos() {
        if (actaViewModel.tipoFormulario != TipoFormulario.INSPECCION) {
            val equipoSession = SessionManager.obtenerAlcoholimetro(requireContext())
            val marcaRaw = equipoSession["marca"] ?: ""
            val modelo = equipoSession["modelo"] ?: ""
            val serie = equipoSession["serie"] ?: ""
            val codHomologacion = equipoSession["codHomologacion"] ?: ""

            val marcaLimpia = if (marcaRaw.contains(" (Serie:")) {
                marcaRaw.substringBefore(" (Serie:")
            } else {
                marcaRaw
            }

            if (marcaLimpia.isNotEmpty() || serie.isNotEmpty()) {
                binding.checkHacerTest.isChecked = true
                binding.cardTest.visibility = View.VISIBLE

                binding.spinnerAlcoMarca.setText(marcaLimpia, false)
                binding.etAlcoModelo.setText(modelo)
                binding.etAlcoSerie.setText(serie)
                binding.etAlcoAprobacion.setText(codHomologacion)

                actaViewModel.hacerTestAlcoholemia = true
                actaViewModel.alcoMarca = marcaLimpia
                actaViewModel.alcoModelo = modelo
                actaViewModel.alcoSerie = serie
                actaViewModel.alcoAprobacion = codHomologacion
            } else {
                binding.checkHacerTest.isChecked = actaViewModel.hacerTestAlcoholemia
                binding.cardTest.visibility = if (actaViewModel.hacerTestAlcoholemia) View.VISIBLE else View.GONE

                binding.spinnerAlcoMarca.setText(actaViewModel.alcoMarca, false)
                binding.etAlcoModelo.setText(actaViewModel.alcoModelo)
                binding.etAlcoSerie.setText(actaViewModel.alcoSerie)
                binding.etAlcoAprobacion.setText(actaViewModel.alcoAprobacion)
            }

            if (actaViewModel.ftResultado > 0.0) {
                binding.etResultado.setText(actaViewModel.ftResultado.toString())
            } else {
                binding.etResultado.setText("")
            }

            binding.checkPlanillaMedica.isChecked = actaViewModel.seAdjuntaPlanillaMedica
        }
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

        // AutoCompleteTextViews
        binding.spinnerAlcoMarca.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                (v as? android.widget.AutoCompleteTextView)?.showDropDown()
                ejecutarAutoScroll(v)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }
        binding.spinnerAlcoMarca.setOnClickListener {
            (it as? android.widget.AutoCompleteTextView)?.showDropDown()
        }

        // EditTexts
        binding.etResultado.setSelectAllOnFocus(true)
        binding.etResultado.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                ejecutarAutoScroll(v)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }
    }

    private fun ejecutarAutoScroll(view: View) {
        binding.scrollAlcoholemia.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            val rect = Rect()
            view.getDrawingRect(rect)
            binding.scrollAlcoholemia.offsetDescendantRectToMyCoords(view, rect)

            val density = resources.displayMetrics.density
            val offsetTop = (90 * density).toInt()

            val targetY = rect.top - offsetTop
            binding.scrollAlcoholemia.smoothScrollTo(0, targetY.coerceAtLeast(0))
        }, 500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}