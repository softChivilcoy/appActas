package com.chivilcoyactas

import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import com.chivilcoyactas.databinding.FragmentVehiculoBinding
import kotlin.getValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class VehiculoFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    // 2. Definimos el binding
    private var _binding: FragmentVehiculoBinding? = null
    private val binding get() = _binding!!



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVehiculoBinding.inflate(inflater, container, false)
        return binding.root // Esto reemplaza al 'return view'
    }

    // Guardamos la lista de objetos devuelta por Room para consultar el ID de modelo al hacer click
    private var listaModelosActuales: List<TipoModeloEntity> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Forzar scroll al inicio al entrar/volver
        binding.scrollVehiculo.post { binding.scrollVehiculo.scrollTo(0, 0) }

        // Aplicar padding dinámico según el teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollVehiculo) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val density = resources.displayMetrics.density
            val extraPadding = (32 * density).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeHeight + extraPadding)
            insets
        }

        // 1. Actualizar barra al Paso 4
        (activity as? MainActivity)?.actualizarProgreso(4)

        val autoFilterUpper = InputFilter.AllCaps()

        // Configurar selector de formato de patente
        configurarSelectorFormatoPatente()

        // Redirigir foco si el dominio está deshabilitado
        binding.viewPatenteOverlay.setOnClickListener {
            if (!binding.etDominio.isEnabled) {
                resaltarCardFormatoPatente()
            } else {
                binding.etDominio.requestFocus()
                (activity as? MainActivity)?.mostrarTeclado(binding.etDominio)
            }
        }
        binding.cardFormatoPatente.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.cardFormatoPatente.strokeColor = android.graphics.Color.LTGRAY
                binding.cardFormatoPatente.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            }
        }

        binding.autoCompleteMarca.filters = arrayOf(autoFilterUpper)
        binding.autoCompleteModelo.filters = arrayOf(autoFilterUpper)

        // Habilitar edición de texto libre y teclado estándar en Marcas y Modelos
        binding.autoCompleteMarca.keyListener = android.text.method.TextKeyListener.getInstance()
        binding.autoCompleteModelo.keyListener = android.text.method.TextKeyListener.getInstance()

        binding.autoCompleteMarca.dropDownVerticalOffset = -20
        binding.autoCompleteModelo.dropDownVerticalOffset = -20

        // ==========================================
        // 🚀 CONTROL DE COMBOS DINÁMICOS DESDE ROOM
        // ==========================================

        // 1. CARGA DE TIPOS DE VEHÍCULO
        actaViewModel.todosLosTiposVehiculo.observe(viewLifecycleOwner) { listaTipos ->
            val nombresTipos = listaTipos.map { it.nombre }
            val adapterTipo = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresTipos)
            binding.autoCompleteTipoVehiculo.setAdapter(adapterTipo)

            if (actaViewModel.tipoVehiculo.isNotEmpty()) {
                binding.autoCompleteTipoVehiculo.setText(actaViewModel.tipoVehiculo, false)
            }
        }

        binding.autoCompleteTipoVehiculo.setOnItemClickListener { parent, _, position, _ ->
            val seleccion = parent.getItemAtPosition(position).toString()
            actaViewModel.tipoVehiculo = seleccion

            val esMoto = seleccion.equals("MOTOCICLETA", ignoreCase = true) ||
                    seleccion.equals("CICLOMOTOR", ignoreCase = true)

            // 1. Actualizamos textos de los Chips según tipo de vehículo
            actualizarTextosChips(esMoto)

            // 2. Limpiamos el texto ingresado y desmarcamos la patente
            binding.etDominio.text?.clear()
            binding.cgFormatoPatente.clearCheck()
            binding.etDominio.isEnabled = false
            binding.viewPatenteOverlay.visibility = View.VISIBLE

            // 3. Resaltar la selección de patente y hacer scroll
            binding.cardFormatoPatente.strokeColor = resources.getColor(R.color.primary_chivilcoy, null)
            binding.cardFormatoPatente.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            binding.cardFormatoPatente.requestFocus()
            ejecutarAutoScroll(binding.cardFormatoPatente)
        }

        // 2. CARGA DE MARCAS Y RESTAURACIÓN DE MODELOS
        actaViewModel.todasLasMarcas.observe(viewLifecycleOwner) { listaMarcas ->
            val nombresMarcas = listaMarcas.map { it.nombre }
            val adapterMarca = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresMarcas)
            binding.autoCompleteMarca.setAdapter(adapterMarca)

            if (actaViewModel.marca.isNotEmpty()) {
                binding.autoCompleteMarca.setText(actaViewModel.marca, false)

                val idMarca = actaViewModel.idMarcaSeleccionada
                if (idMarca != null && idMarca > 0) {
                    cargarModelosPorMarca(idMarca)
                }
            }
        }

        binding.autoCompleteMarca.setOnItemClickListener { parent, _, position, _ ->
            val marcaSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.marca = marcaSeleccionada

            val objetoMarca = actaViewModel.todasLasMarcas.value?.find { it.nombre == marcaSeleccionada }
            val marcaId = objetoMarca?.id ?: 0

            actaViewModel.idMarcaSeleccionada = if (marcaId > 0) marcaId else null

            binding.autoCompleteModelo.text?.clear()
            actaViewModel.modelo = ""
            actaViewModel.idModeloSeleccionado = null

            if (marcaId > 0) {
                cargarModelosPorMarca(marcaId)
            }
            binding.autoCompleteModelo.requestFocus()
        }

        // 3. SELECCIÓN DE MODELO
        binding.autoCompleteModelo.setOnItemClickListener { parent, view, position, _ ->
            val modeloSeleccionado = parent.getItemAtPosition(position).toString()
            actaViewModel.modelo = modeloSeleccionado

            // Buscamos el ID real desde la lista filtrada guardada en memoria
            val objetoModelo = listaModelosActuales.find { it.nombre.equals(modeloSeleccionado, ignoreCase = true) }
            actaViewModel.idModeloSeleccionado = objetoModelo?.id ?: 0

            // Ocultar teclado y quitar el foco de la vista
            (activity as? MainActivity)?.hideKeyboard()
            binding.autoCompleteModelo.clearFocus()
        }

        binding.autoCompleteModelo.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                // 1. Ocultar el teclado del POS/dispositivo
                (activity as? MainActivity)?.hideKeyboard()

                // 2. Quitar el foco para no mantener resaltado el campo
                v.clearFocus()

                true // Consume el evento
            } else {
                false
            }
        }

        // Listener para desplegar/ocultar datos del propietario
        binding.checkEsPropietario.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.esPropietario = isChecked

            if (isChecked) {
                binding.layoutDatosPropietario.visibility = View.GONE
            } else {
                binding.layoutDatosPropietario.visibility = View.VISIBLE
                binding.etNombrePropietario.requestFocus()
            }
        }

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollVehiculo.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        // Restaurar valores guardados previamente en el ViewModel
        binding.etDominio.setText(actaViewModel.dominio)
        binding.autoCompleteMarca.setText(actaViewModel.marca, false)
        binding.autoCompleteModelo.setText(actaViewModel.modelo, false)
        binding.checkEsPropietario.isChecked = actaViewModel.esPropietario

        // Navegación
        binding.btnSiguienteVehiculo.setOnClickListener {
            ejecutarSiguiente()
        }

        binding.btnVolverVehiculo.setOnClickListener {
            findNavController().navigateUp()
        }

        // Configurar autoscroll y selección
        setupFocusListeners()
    }

    private fun setupFocusListeners() {
        // AutoCompleteTextViews
        binding.autoCompleteTipoVehiculo.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.autoCompleteTipoVehiculo.showDropDown()
                ejecutarAutoScroll(binding.inputLayoutTipoVehiculo)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }
        binding.autoCompleteMarca.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.autoCompleteMarca.showDropDown()
                ejecutarAutoScroll(binding.inputMarca)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }
        binding.autoCompleteModelo.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.autoCompleteModelo.showDropDown()
                ejecutarAutoScroll(binding.inputModelo)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }

        binding.autoCompleteTipoVehiculo.setOnClickListener { binding.autoCompleteTipoVehiculo.showDropDown() }
        binding.autoCompleteMarca.setOnClickListener { binding.autoCompleteMarca.showDropDown() }
        binding.autoCompleteModelo.setOnClickListener { binding.autoCompleteModelo.showDropDown() }

        // EditTexts
        binding.etDominio.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.etDominio.selectAll()
                ejecutarAutoScroll(binding.inputDominio)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }
        binding.etNombrePropietario.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.etNombrePropietario.selectAll()
                ejecutarAutoScroll(binding.inputNombrePropietario)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }
        binding.etDniPropietario.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.etDniPropietario.selectAll()
                ejecutarAutoScroll(binding.inputDniPropietario)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }
    }

    private fun ejecutarAutoScroll(view: View) {
        binding.scrollVehiculo.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            val rect = android.graphics.Rect()
            view.getDrawingRect(rect)
            binding.scrollVehiculo.offsetDescendantRectToMyCoords(view, rect)

            val density = resources.displayMetrics.density
            val offsetTop = (90 * density).toInt()

            val targetY = rect.top - offsetTop
            binding.scrollVehiculo.smoothScrollTo(0, targetY.coerceAtLeast(0))
        }, 500)
    }

    // Función auxiliar privada para cargar los modelos filtrados desde Room
    private fun cargarModelosPorMarca(marcaId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            listaModelosActuales = db.catalogoDao().obtenerModelosPorMarca(marcaId)

            val nombresModelos = listaModelosActuales.map { it.nombre }
            val adapterModelo = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresModelos)
            binding.autoCompleteModelo.setAdapter(adapterModelo)

            if (actaViewModel.modelo.isNotEmpty()) {
                binding.autoCompleteModelo.setText(actaViewModel.modelo, false)
            }
        }
    }

    private fun ejecutarSiguiente() {
        // 2. Guardar textos en el ViewModel desde las vistas
        actaViewModel.tipoVehiculo = binding.autoCompleteTipoVehiculo.text.toString().trim()
        actaViewModel.marca = binding.autoCompleteMarca.text.toString().trim().uppercase()
        actaViewModel.modelo = binding.autoCompleteModelo.text.toString().trim().uppercase()
        actaViewModel.dominio = binding.etDominio.text.toString().trim().uppercase()
        actaViewModel.esPropietario = binding.checkEsPropietario.isChecked

        // 3. Validación de consistencia para IDs
        if (actaViewModel.marca.isEmpty()) {
            actaViewModel.idMarcaSeleccionada = null
        }
        if (actaViewModel.modelo.isEmpty()) {
            actaViewModel.idModeloSeleccionado = null
        }

        // 4. Navegar al paso 5 (Alcoholemia)
        findNavController().navigate(R.id.action_vehiculo_to_alcoholemia)
    }

    private fun configurarSelectorFormatoPatente() {
        // Listener del ChipGroup
        binding.cgFormatoPatente.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull()
            binding.etDominio.isEnabled = selectedId != null

            if (selectedId != null) {
                // Restaurar color de borde original al seleccionar
                binding.cardFormatoPatente.setStrokeColor(android.graphics.Color.LTGRAY)
                binding.cardFormatoPatente.strokeWidth = (1 * resources.displayMetrics.density).toInt()

                // Ocultar mensaje de error
                binding.tvErrorPatente.visibility = View.GONE

                // Ocultar overlay para permitir interacción directa
                binding.viewPatenteOverlay.visibility = View.GONE

                binding.etDominio.text?.clear()

                // Aplica los filtros de caracteres y el tipo de teclado
                actualizarFormatoSegunSeleccion()

                // Hacer foco en el EditText de la patente y solicitar el teclado
                binding.etDominio.requestFocus()
            }
        }

        // Listener de formateo del texto (mayúsculas/transición automática)
        binding.etDominio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val textoLimpio = s.toString().replace(" ", "").uppercase()
                if (s.toString() != textoLimpio) {
                    binding.etDominio.setText(textoLimpio)
                    binding.etDominio.setSelection(binding.etDominio.text?.length ?: 0)
                }

                gestionarTransicionTeclado(textoLimpio)
            }
        })
    }

    // 🚀 Actualiza los textos de los Chips según si es Auto o Moto
    private fun actualizarTextosChips(esMoto: Boolean) {
        if (esMoto) {
            binding.chipPatenteVieja.text = "Vieja (123ABC)"
            binding.chipPatenteNueva.text = "Nueva (A123BCD)"
        } else {
            binding.chipPatenteVieja.text = "Vieja (ABC123)"
            binding.chipPatenteNueva.text = "Nueva (AA123BB)"
        }
    }

    private fun actualizarFormatoSegunSeleccion() {
        val esVieja = binding.chipPatenteVieja.isChecked
        val esMoto = actaViewModel.tipoVehiculo.equals("MOTOCICLETA", ignoreCase = true) ||
                actaViewModel.tipoVehiculo.equals("CICLOMOTOR", ignoreCase = true)

        // Ajusta la longitud máxima (Vieja: 6, Nueva: 7)
        binding.etDominio.filters = arrayOf(android.text.InputFilter.LengthFilter(if (esVieja) 6 else 7))

        // Asigna el tipo de entrada y despliega el teclado
        if (esMoto && esVieja) {
            abrirTecladoNumerico() // Moto vieja: 123ABC
        } else {
            abrirTecladoTexto()    // Autos y moto nueva arrancan con letras
        }
        (activity as? MainActivity)?.mostrarTeclado(binding.etDominio)
    }

    private fun gestionarTransicionTeclado(texto: String) {
        val esVieja = binding.chipPatenteVieja.isChecked
        val esMoto = actaViewModel.tipoVehiculo.equals("MOTOCICLETA", ignoreCase = true) ||
                actaViewModel.tipoVehiculo.equals("CICLOMOTOR", ignoreCase = true)

        if (esMoto) {
            if (esVieja) {
                // MOTO VIEJA: 123 ABC (3 Números -> 3 Letras)
                if (texto.length < 3) abrirTecladoNumerico() else abrirTecladoTexto()
            } else {
                // MOTO NUEVA: A 123 BCD (1 Letra -> 3 Números -> 3 Letras)
                when (texto.length) {
                    0 -> abrirTecladoTexto()
                    in 1..3 -> abrirTecladoNumerico()
                    else -> abrirTecladoTexto()
                }
            }
        } else {
            if (esVieja) {
                // AUTO VIEJO: ABC 123 (3 Letras -> 3 Números)
                if (texto.length < 3) abrirTecladoTexto() else abrirTecladoNumerico()
            } else {
                // AUTO NUEVO: AA 123 BB (2 Letras -> 3 Números -> 2 Letras)
                when (texto.length) {
                    in 0..1 -> abrirTecladoTexto()
                    in 2..4 -> abrirTecladoNumerico()
                    else -> abrirTecladoTexto()
                }
            }
        }
    }

    private fun abrirTecladoNumerico() {
        val nuevoType = InputType.TYPE_CLASS_NUMBER
        if (binding.etDominio.inputType != nuevoType) {
            binding.etDominio.inputType = nuevoType
            refrescarTeclado()
        }
    }

    private fun abrirTecladoTexto() {
        val nuevoType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        if (binding.etDominio.inputType != nuevoType) {
            binding.etDominio.inputType = nuevoType
            refrescarTeclado()
        }
    }

    // 🚀 Fuerza a Android / POS 3nStar a reconstruir el teclado activo
    private fun refrescarTeclado() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.restartInput(binding.etDominio)
    }

    private fun resaltarCardFormatoPatente() {
        binding.cardFormatoPatente.strokeColor = resources.getColor(R.color.primary_chivilcoy, null)
        binding.cardFormatoPatente.strokeWidth = (2 * resources.displayMetrics.density).toInt()

        // Mostrar mensaje de error
        binding.tvErrorPatente.visibility = View.VISIBLE

        binding.cardFormatoPatente.requestFocus()
        ejecutarAutoScroll(binding.cardFormatoPatente)
    }
}