package com.chivilcoyactas

import android.graphics.Rect
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.chivilcoyactas.databinding.FragmentInfractorBinding
import com.chivilcoyactas.databinding.ItemDatosVinculoBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Filter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout

class InfractorFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentInfractorBinding? = null
    private val binding get() = _binding!!

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Escaneo cancelado", Toast.LENGTH_LONG).show()
        } else {
            activeScanningBinding?.let { binding ->
                parsearDatosDNI(result.contents, binding)
            }
        }
    }

    private var activeScanningBinding: ItemDatosVinculoBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfractorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Forzar scroll al inicio al entrar/volver
        binding.scrollInfractor.post { binding.scrollInfractor.scrollTo(0, 0) }

        (activity as? MainActivity)?.actualizarProgreso(3)

        // Reset inicial de foco para hardware 3nStar
        binding.rootInfractor.requestFocus()

        // 1. Ajuste automático del ScrollView según la altura REAL del teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollInfractor) { v, insets ->
            val keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val density = resources.displayMetrics.density
            val bottomPadding = if (keyboardHeight > 0) keyboardHeight + (32 * density).toInt() else (20 * density).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottomPadding)
            insets
        }

        // Ajustar el ancho de los 3 chips para que sean iguales dividiendo el espacio disponible
        binding.chipGroupEstado.post {
            val spacing = (8 * resources.displayMetrics.density).toInt()
            val totalSpacing = spacing * 2 // 2 espacios entre 3 chips
            val anchoDisponible = binding.chipGroupEstado.width - binding.chipGroupEstado.paddingLeft - binding.chipGroupEstado.paddingRight - totalSpacing
            val anchoPorChip = anchoDisponible / 3

            binding.chipAportaDatos.width = anchoPorChip
            binding.chipSeNiega.width = anchoPorChip
            binding.chipNoEsta.width = anchoPorChip
        }

        actaViewModel.todosLosRolesInvolucrado.observe(viewLifecycleOwner) { roles ->
            renderizarVinculosChips(roles)
        }

        binding.layoutVinculo.isVisible = true
        // renderizarVinculosChips() // Se llama ahora vía observer de LiveData

        // 2. Respuesta del Infractor
        binding.chipGroupEstado.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener

            val esInspeccion = actaViewModel.tipoFormulario == TipoFormulario.INSPECCION

            //if (checkedId == binding.apSiDatos.id) {
            if (checkedId == R.id.chipAportaDatos) {
                binding.layoutVinculo.isVisible = true
                binding.containerFormulariosDinamicos.isVisible = true
                binding.tilObservaciones.isVisible = false
            } else if (checkedId == R.id.chipSeNiega || checkedId == R.id.chipNoEsta) {
                binding.layoutVinculo.isVisible = false
                binding.containerFormulariosDinamicos.isVisible = false
                binding.tilObservaciones.isVisible = true

                // Hacer foco en el EditText interno de tilObservaciones
                binding.etObservaciones.requestFocus()
                (activity as? MainActivity)?.mostrarTeclado(binding.etObservaciones)
            }
        }

        binding.etObservaciones.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                ejecutarAutoScroll(view)
                view.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                view.setBackgroundResource(0)
            }
        }
        

        binding.btnVolver.setOnClickListener {
            if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                findNavController().navigate(R.id.action_infractor_to_procedimiento_VUELTA)
            } else {
                findNavController().navigate(R.id.action_infractor_to_faltas_VUELTA)
            }
        }

        binding.btnSiguiente.setOnClickListener {
            //val opcionSeleccionada = binding.chipGroupEstado.checkedChipId
            val opcionSeleccionada = binding.chipGroupEstado.checkedChipId

            //if (opcionSeleccionada == -1) {
            if (opcionSeleccionada == View.NO_ID) { // Equivalente a == -1
                Toast.makeText(context, "Por favor, seleccione una condición del infractor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //if (opcionSeleccionada == binding.apSiDatos.id) {
            if (opcionSeleccionada == R.id.chipAportaDatos) {
                if (binding.containerFormulariosDinamicos.childCount == 0) {
                    Toast.makeText(context, "Debe seleccionar al menos un vínculo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Validar cada formulario dinámico
                for (i in 0 until binding.containerFormulariosDinamicos.childCount) {
                    val view = binding.containerFormulariosDinamicos.getChildAt(i)
                    val item = ItemDatosVinculoBinding.bind(view)
                    if (item.etNombre.text.isNullOrBlank()) {
                        item.etNombre.error = "Campo Obligatorio"
                        item.etNombre.requestFocus()
                        return@setOnClickListener
                    }
                    // Se elimina la validación obligatoria de etDni
                }
            }

            //if (opcionSeleccionada == binding.apNoDatos.id || opcionSeleccionada == binding.apNoIdentifica.id) {
            if (opcionSeleccionada == R.id.chipSeNiega || opcionSeleccionada == R.id.chipNoEsta) {
                if (binding.etObservaciones.text.isNullOrBlank()) {
                    binding.etObservaciones.error = "Debe justificar el motivo de la falta de datos"
                    return@setOnClickListener
                }
            }

            guardarDatosEnCaja()

            if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                (activity as? MainActivity)?.actualizarProgreso(4)
                findNavController().navigate(R.id.action_infractor_to_faltas_inspeccion)
            } else {
                findNavController().navigate(R.id.action_infractor_to_vehiculo)
            }
        }

        binding.scrollInfractor.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            binding.rootInfractor.requestFocus()
            false
        }

        cargarDatosDeCaja()
    }

    private fun cargarDatosDeCaja() {
        when (actaViewModel.niegaDatos) {
            0 -> {
                binding.chipGroupEstado.check(R.id.chipAportaDatos)
                binding.layoutVinculo.isVisible = true
                binding.containerFormulariosDinamicos.isVisible = true
                binding.tilObservaciones.isVisible = false
            }
            1 -> {
                binding.chipGroupEstado.check(R.id.chipSeNiega)
                binding.layoutVinculo.isVisible = false
                binding.containerFormulariosDinamicos.isVisible = false
                binding.tilObservaciones.isVisible = true
            }
            2 -> {
                binding.chipGroupEstado.check(R.id.chipNoEsta)
                binding.layoutVinculo.isVisible = false
                binding.containerFormulariosDinamicos.isVisible = false
                binding.tilObservaciones.isVisible = true
            }
            else -> {
                binding.chipGroupEstado.clearCheck()
                binding.layoutVinculo.isVisible = false
                binding.containerFormulariosDinamicos.isVisible = false
                binding.tilObservaciones.isVisible = false
            }
        }

        binding.etObservaciones.setText(actaViewModel.motivoDatos)

        // Migración de datos para modo TRANSITO (si existen en campos individuales pero no en el mapa dinámico)
    }

    private fun renderizarVinculosChips(roles: List<TipoRolInvolucradoEntity>) {
        val seleccionadosPreviamente = actaViewModel.vinculoLugar.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        binding.containerVinculos.removeAllViews()
        // No borramos los formularios aquí si ya existen para evitar parpadeos, 
        // pero la lógica de abajo se encarga de sincronizar.

        val density = resources.displayMetrics.density
        val marginHorizontal = (4 * density).toInt()
        val marginVertical = (4 * density).toInt()

        roles.forEach { rolEntity ->
            val opcion = rolEntity.codigo
            
            val button = com.google.android.material.button.MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = opcion
                isCheckable = true
                isAllCaps = false
                textSize = 12f
                cornerRadius = (12 * density).toInt()
                
                // Apariencia compacta
                minHeight = 0
                minWidth = 0
                val verticalInset = (4 * density).toInt()
                insetTop = verticalInset
                insetBottom = verticalInset
                setPaddingRelative((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())

                isSingleLine = false
                maxLines = 2
                gravity = android.view.Gravity.CENTER
                
                isChecked = seleccionadosPreviamente.contains(opcion.lowercase())
            }

            // Si ya venía marcado (al volver de otra pantalla), asegurar que el formulario exista
            if (button.isChecked) {
                if (binding.containerFormulariosDinamicos.findViewWithTag<View>(opcion) == null) {
                    agregarFormularioPersona(rolEntity, scrollAndFocus = false)
                }
            }

            button.addOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (binding.containerFormulariosDinamicos.findViewWithTag<View>(opcion) == null) {
                        agregarFormularioPersona(rolEntity, scrollAndFocus = true)
                    }
                } else {
                    eliminarFormularioPersona(rolEntity)
                }
            }

            // Configuración de LayoutParams para el GridLayout (2 columnas)
            val params = androidx.gridlayout.widget.GridLayout.LayoutParams(
                androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, androidx.gridlayout.widget.GridLayout.FILL),
                androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, androidx.gridlayout.widget.GridLayout.FILL, 1f)
            )
            params.width = 0
            params.height = androidx.gridlayout.widget.GridLayout.LayoutParams.WRAP_CONTENT
            params.setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical)
            button.layoutParams = params

            binding.containerVinculos.addView(button)
        }
    }

    private fun agregarFormularioPersona(rol: TipoRolInvolucradoEntity, scrollAndFocus: Boolean = false) {
        val rolNombre = rol.codigo
        val itemBinding = ItemDatosVinculoBinding.inflate(layoutInflater, binding.containerFormulariosDinamicos, false)
        val view = itemBinding.root
        view.tag = rolNombre

        itemBinding.tvTituloFormulario.text = "DATOS DEL ${rolNombre.uppercase()}"

        val datos = actaViewModel.datosPersonasVinculadas.entries.find { 
            it.key.lowercase() == rolNombre.lowercase() 
        }?.value ?: PersonaVinculada()
        
        setupLogicaFormularioDinamico(itemBinding, datos, rol)

        binding.containerFormulariosDinamicos.addView(view)

        if (scrollAndFocus) {
            view.post {
                ejecutarAutoScroll(itemBinding.tvTituloFormulario)

                itemBinding.etNombre.requestFocus()
                (activity as? MainActivity)?.mostrarTeclado(itemBinding.etNombre)
            }
        }
    }

    private fun eliminarFormularioPersona(rol: TipoRolInvolucradoEntity) {
        val view = binding.containerFormulariosDinamicos.findViewWithTag<View>(rol.codigo)
        if (view != null) {
            binding.containerFormulariosDinamicos.removeView(view)
        }
        // Eliminar del mapa usando búsqueda insensible a mayúsculas
        val keyToRemove = actaViewModel.datosPersonasVinculadas.keys.find { it.lowercase() == rol.codigo.lowercase() }
        if (keyToRemove != null) {
            actaViewModel.datosPersonasVinculadas.remove(keyToRemove)
        }
    }

    private fun setupLogicaFormularioDinamico(item: ItemDatosVinculoBinding, datos: PersonaVinculada, rol: TipoRolInvolucradoEntity) {
        item.etNombre.setText(datos.nombre)
        item.etDni.setText(datos.dni)
        item.etCalle.setText(datos.calle)
        item.etCalleNro.setText(datos.altura)
        
        val provInicial = datos.provincia.ifEmpty { "BUENOS AIRES" }
        val locInicial = datos.localidad.ifEmpty { "CHIVILCOY" }
        
        item.autoCompleteProvincia.setText(provInicial, false)
        item.autoCompleteProvincia.tag = datos.idProvincia
        item.autoCompletePais.setText(datos.pais.ifEmpty { "ARGENTINA" }, false)
        item.autoCompletePais.tag = datos.idPais ?: 32
        item.autoCompleteLocalidad.setText(locInicial, false)
        item.autoCompleteLocalidad.tag = datos.idLocalidad
        item.etCP.setText(datos.cp.ifEmpty { "6620" })

        val autoFilterUpper = InputFilter.AllCaps()

        // 1. Configuración de autocompletado de CALLES (Editable + Sugerencias)
        item.etCalle.filters = arrayOf(autoFilterUpper)
        item.etCalle.dropDownVerticalOffset = -20
        val callesChivilcoy = resources.getStringArray(R.array.calles_chivilcoy_list)
        
        // 🚀 ADAPTER PERSONALIZADO CON FILTRO "CONTAINS" CORREGIDO
        val adapterCalles = crearAdapterLimitado(callesChivilcoy.toList())
        
        item.etCalle.setAdapter(adapterCalles)
        // 🚀 Limitar la altura física del dropdown para mostrar ~4 items y habilitar scroll
        item.etCalle.dropDownHeight = (200 * resources.displayMetrics.density).toInt()
        // Forzamos que sea editable y muestre el teclado normalmente como en Testigos.kt
        item.etCalle.keyListener = android.text.method.TextKeyListener.getInstance()
        item.etCalle.threshold = 1
        
        item.etCalle.setOnClickListener { 
            (item.etCalle as? AutoCompleteTextView)?.showDropDown()
        }
        item.etCalle.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                (item.etCalle as? AutoCompleteTextView)?.showDropDown()
                ejecutarAutoScroll(view)
                view.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                view.setBackgroundResource(0)
            }
        }

        item.etCalle.setOnItemClickListener { _, _, _, _ ->
            item.etCalleNro.requestFocus()
        }

        // Lógica de Licencia
        val requiereLicencia = rol.requiereLicencia
        
        item.cardLicencia.isVisible = requiereLicencia
        if (!requiereLicencia) {
            item.tilLicencia.isVisible = false
            item.chipGroupLicencia.check(R.id.chipLicenciaNo)
            item.etLicencia.text?.clear()
        } else {
            // Si requiere licencia, mostramos la sección y cargamos los datos
            if (datos.tieneLicencia) {
                item.chipGroupLicencia.check(R.id.chipLicenciaSi)
                item.tilLicencia.isVisible = true
                item.etLicencia.setText(datos.nroLicencia)
            } else {
                item.chipGroupLicencia.check(R.id.chipLicenciaNo)
                item.tilLicencia.isVisible = false
            }
        }

        item.chipGroupLicencia.setOnCheckedStateChangeListener { _, checkedIds ->
            val isSi = checkedIds.contains(R.id.chipLicenciaSi)
            item.tilLicencia.isVisible = isSi
            if (!isSi) {
                item.etLicencia.text?.clear()
            }
        }

        item.cardUbicacion.setOnClickListener {
            item.containerUbicacionExtra.isVisible = !item.containerUbicacionExtra.isVisible
        }

        item.btnEscanearDNI.setOnClickListener {
            activeScanningBinding = item
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setPrompt("ALEJE EL DNI (5-10 cm) HASTA QUE HAGA FOCO")
            options.setBeepEnabled(true)
            options.setOrientationLocked(false)
            options.setBarcodeImageEnabled(false)
            options.setTorchEnabled(false)
            barcodeLauncher.launch(options)
        }

        val actualizarResumen = {
            val pa = item.autoCompletePais.text.toString()
            val p = item.autoCompleteProvincia.text.toString()
            val l = item.autoCompleteLocalidad.text.toString()
            val c = item.etCP.text.toString()
            item.tvUbicacionResumen.text = "$l, $p, $pa (CP $c)"
        }

        actualizarResumen()

        // Helper para cargar y configurar localidades
        fun cargarLocalidades(provNombre: String) {
            lifecycleScope.launch {
                val objetoProvincia = actaViewModel.todasLasProvincias.value?.find { it.nombre.equals(provNombre, ignoreCase = true) }
                val provinciaId = objetoProvincia?.id ?: 0
                val localidades = AppDatabase.getDatabase(requireContext()).catalogoDao().obtenerLocalidadesPorProvincia(provinciaId)
                val nombresLocalidades = localidades.map { it.nombre.uppercase() }
                
                val adapterLocalidad = crearAdapterLimitado(nombresLocalidades)
                item.autoCompleteLocalidad.setAdapter(adapterLocalidad)
                item.autoCompleteLocalidad.threshold = 1
            }
        }

        fun cargarProvincias(paisId: Int) {
            lifecycleScope.launch {
                val provincias = AppDatabase.getDatabase(requireContext()).catalogoDao().obtenerProvinciasPorPais(paisId)
                val nombresProvincias = provincias.map { it.nombre.uppercase() }
                val adapterProvincia = crearAdapterLimitado(nombresProvincias)
                item.autoCompleteProvincia.setAdapter(adapterProvincia)
            }
        }

        // 2. Configuración de PAISES
        actaViewModel.todosLosPaises.observe(viewLifecycleOwner) { listaPaises ->
            val nombresPaises = listaPaises.map { it.nombre.uppercase() }
            val adapterPais = crearAdapterLimitado(nombresPaises)
            item.autoCompletePais.setAdapter(adapterPais)

            val dropHeight = (200 * resources.displayMetrics.density).toInt()
            item.autoCompletePais.dropDownHeight = dropHeight
        }

        item.autoCompletePais.setOnItemClickListener { parent, _, position, _ ->
            val paisSeleccionado = parent.getItemAtPosition(position).toString().uppercase()
            val objetoPais = actaViewModel.todosLosPaises.value?.find { it.nombre.equals(paisSeleccionado, ignoreCase = true) }
            item.autoCompletePais.tag = objetoPais?.id
            
            item.autoCompleteProvincia.setText("", false)
            item.autoCompleteProvincia.tag = null
            item.autoCompleteLocalidad.setText("", false)
            item.autoCompleteLocalidad.tag = null
            
            objetoPais?.id?.let { cargarProvincias(it) }
            actualizarResumen()
            item.autoCompleteProvincia.requestFocus()
            (item.autoCompleteProvincia as? AutoCompleteTextView)?.showDropDown()
        }

        // 🚀 Resetear niveles inferiores al cambiar texto manualmente del País
        item.autoCompletePais.doAfterTextChanged {
            if (item.autoCompletePais.hasFocus()) {
                item.autoCompletePais.tag = null
                item.autoCompleteProvincia.setText("", false)
                item.autoCompleteProvincia.tag = null
                item.autoCompleteLocalidad.setText("", false)
                item.autoCompleteLocalidad.tag = null
            }
            actualizarResumen()
        }

        // 3. Configuración de PROVINCIAS (Combo reactivo)
        actaViewModel.todasLasProvincias.observe(viewLifecycleOwner) { listaProvincias ->
            val nombresProvincias = listaProvincias.map { it.nombre.uppercase() }
            val adapterProvincia = crearAdapterLimitado(nombresProvincias)
            item.autoCompleteProvincia.setAdapter(adapterProvincia)

            // 🚀 Limitar la altura física del dropdown para mostrar ~4 items y habilitar scroll
            val dropHeight = (200 * resources.displayMetrics.density).toInt()
            item.autoCompleteProvincia.dropDownHeight = dropHeight
            item.autoCompleteLocalidad.dropDownHeight = dropHeight
            item.etCalle.dropDownHeight = dropHeight

            // Si es la carga inicial y el campo está vacío, nos aseguramos de cargar localidades de BA
            if (item.autoCompleteProvincia.text.toString().uppercase() == "BUENOS AIRES") {
                cargarLocalidades("BUENOS AIRES")
            }
        }

        item.autoCompleteProvincia.doAfterTextChanged {
            if (item.autoCompleteProvincia.hasFocus()) {
                item.autoCompleteProvincia.tag = null
                item.autoCompleteLocalidad.setText("", false)
                item.autoCompleteLocalidad.tag = null
            }
            actualizarResumen()
        }

        // Configuración de botón Volver Arriba
        item.btnVolverArriba.isVisible = true
        item.btnVolverArriba.setOnClickListener {
            val rect = Rect()
            binding.layoutVinculo.getDrawingRect(rect)
            binding.scrollInfractor.offsetDescendantRectToMyCoords(binding.layoutVinculo, rect)
            binding.scrollInfractor.smoothScrollTo(0, rect.top)

            // Opcional: darle foco a los chips de vínculos
            binding.containerVinculos.requestFocus()
        }

        // Carga inicial de localidades para la provincia actual
        cargarLocalidades(provInicial)

        item.autoCompleteProvincia.setOnItemClickListener { parent, _, position, _ ->
            val provSeleccionada = parent.getItemAtPosition(position).toString().uppercase()
            val objetoProvincia = actaViewModel.todasLasProvincias.value?.find { it.nombre.equals(provSeleccionada, ignoreCase = true) }
            item.autoCompleteProvincia.tag = objetoProvincia?.id
            
            item.autoCompleteLocalidad.setText("", false)
            item.autoCompleteLocalidad.tag = null
            cargarLocalidades(provSeleccionada)
            actualizarResumen()
            // Al seleccionar provincia, pasar foco a localidad
            item.autoCompleteLocalidad.requestFocus()
            (item.autoCompleteLocalidad as? AutoCompleteTextView)?.showDropDown()
        }

        // 3. Configuración de LOCALIDADES (Combo dinámico - Editable)
        item.autoCompleteLocalidad.keyListener = android.text.method.TextKeyListener.getInstance()
        item.autoCompleteLocalidad.filters = arrayOf(autoFilterUpper)
        item.autoCompleteLocalidad.threshold = 1

        item.autoCompleteLocalidad.doAfterTextChanged {
            // Si el usuario cambia el texto manualmente, el ID del catálogo ya no es válido
            if (item.autoCompleteLocalidad.hasFocus()) {
                item.autoCompleteLocalidad.tag = null
            }
            actualizarResumen()
        }

        item.autoCompleteLocalidad.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                item.etCP.requestFocus()
                true
            } else false
        }

        item.autoCompleteLocalidad.setOnItemClickListener { parent, _, position, _ ->
            val locSeleccionada = parent.getItemAtPosition(position).toString().uppercase()
            
            lifecycleScope.launch {
                val provinciaId = (item.autoCompleteProvincia.tag as? Int) ?: 0
                val dao = AppDatabase.getDatabase(requireContext()).catalogoDao()
                val localidades = dao.obtenerLocalidadesPorProvincia(provinciaId)
                val objetoLocalidad = localidades.find { it.nombre.equals(locSeleccionada, ignoreCase = true) }
                
                if (objetoLocalidad != null) {
                    item.autoCompleteLocalidad.tag = objetoLocalidad.id
                    
                    // 🚀 BUSQUEDA DINÁMICA DE CP
                    val cps = dao.obtenerCpostalPorLocalidad(objetoLocalidad.id)
                    if (cps.isNotEmpty()) {
                        item.etCP.setText(cps.first().codigo)
                    } else if (locSeleccionada.equals("CHIVILCOY", ignoreCase = true)) {
                        item.etCP.setText("6620")
                    }
                } else if (locSeleccionada.equals("CHIVILCOY", ignoreCase = true)) {
                    item.etCP.setText("6620")
                }
                
                actualizarResumen()
                // Al seleccionar localidad, pasar foco a CP
                item.etCP.requestFocus()
            }
        }

        item.etCP.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                (activity as? MainActivity)?.hideKeyboard()
                item.etCP.clearFocus()
                true
            } else false
        }

        item.etCP.doAfterTextChanged { actualizarResumen() }

        // Configurar auto-scroll para este formulario
        configurarAutoScrollEnCampos(item)
    }

    private fun configurarAutoScrollEnCampos(item: ItemDatosVinculoBinding) {
        val inputs = listOf(
            item.etNombre to item.tvTituloFormulario,
            item.etDni to null,
            item.etLicencia to null,
            item.etCalle to null,
            item.etCalleNro to null,
            item.autoCompletePais to null,
            item.autoCompleteProvincia to null,
            item.autoCompleteLocalidad to null,
            item.etCP to null
        )

        inputs.forEach { (editText, target) ->
            // Restaurar click para mostrar dropdown si es AutoComplete
            if (editText is AutoCompleteTextView) {
                editText.setOnClickListener { 
                    if (!editText.isPopupShowing) editText.showDropDown() 
                }
            }

            editText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    (view as? AutoCompleteTextView)?.showDropDown()

                    if (editText.text.isNotEmpty()) {
                        editText.post {
                            editText.selectAll()
                        }
                    }
                    ejecutarAutoScroll(target ?: view)
                    view.setBackgroundResource(R.drawable.bg_edittext_focused)
                } else {
                    view.setBackgroundResource(0)
                }
            }
        }
    }

    private fun ejecutarAutoScroll(view: View) {
        binding.scrollInfractor.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            val rect = Rect()
            view.getDrawingRect(rect)
            binding.scrollInfractor.offsetDescendantRectToMyCoords(view, rect)

            val density = resources.displayMetrics.density
            val offsetTop = (90 * density).toInt()

            val targetY = rect.top - offsetTop
            binding.scrollInfractor.smoothScrollTo(0, targetY.coerceAtLeast(0))
        }, 500)
    }

    private fun guardarDatosEnCaja() {
        val niegaVal = when (binding.chipGroupEstado.checkedChipId) {
            R.id.chipAportaDatos -> 0
            R.id.chipSeNiega -> 1
            R.id.chipNoEsta -> 2
            else -> -1
        }
        actaViewModel.niegaDatos = niegaVal
        actaViewModel.motivoDatos = binding.etObservaciones.text.toString()

        if (niegaVal == 0) {
            val seleccionados = mutableListOf<String>()
            val nuevosDatosMap = mutableMapOf<String, PersonaVinculada>()

            for (i in 0 until binding.containerFormulariosDinamicos.childCount) {
                val view = binding.containerFormulariosDinamicos.getChildAt(i)
                val rolOriginal = view.tag.toString()
                val item = ItemDatosVinculoBinding.bind(view)
                
                seleccionados.add(rolOriginal)

                val persona = PersonaVinculada(
                    nombre = item.etNombre.text.toString(),
                    dni = item.etDni.text.toString(),
                    tieneLicencia = item.chipGroupLicencia.checkedChipId == R.id.chipLicenciaSi,
                    nroLicencia = item.etLicencia.text.toString(),
                    calle = item.etCalle.text.toString(),
                    altura = item.etCalleNro.text.toString(),
                    pais = item.autoCompletePais.text.toString(),
                    provincia = item.autoCompleteProvincia.text.toString(),
                    localidad = item.autoCompleteLocalidad.text.toString(),
                    cp = item.etCP.text.toString(),
                    idPais = (item.autoCompletePais.tag as? Int),
                    idProvincia = (item.autoCompleteProvincia.tag as? Int),
                    idLocalidad = (item.autoCompleteLocalidad.tag as? Int)
                )
                nuevosDatosMap[rolOriginal] = persona
            }
            actaViewModel.vinculoLugar = seleccionados.joinToString(", ")
            actaViewModel.datosPersonasVinculadas = nuevosDatosMap

            // Sincronizar con campos principales para compatibilidad (usando el primer formulario inflado)
            if (nuevosDatosMap.isNotEmpty()) {
                val primeraPersona = nuevosDatosMap.values.first()
                actaViewModel.ApellidoNombreInfractor = primeraPersona.nombre
                actaViewModel.dniInfractor = primeraPersona.dni
                actaViewModel.tieneLicencia = primeraPersona.tieneLicencia
                actaViewModel.nroLicencia = primeraPersona.nroLicencia
                actaViewModel.calleInfractor = primeraPersona.calle
                actaViewModel.alturaInfractor = primeraPersona.altura
                actaViewModel.provinciaInfractor = primeraPersona.provincia
                actaViewModel.idProvinciaInfractor = primeraPersona.idProvincia
                actaViewModel.paisInfractor = primeraPersona.pais
                actaViewModel.idPaisInfractor = primeraPersona.idPais
                actaViewModel.localidadInfractor = primeraPersona.localidad
                actaViewModel.idLocalidadInfractor = primeraPersona.idLocalidad
                actaViewModel.cpInfractor = primeraPersona.cp
            }
        } else {
            // Si se niega o no está, limpiamos los datos para no persistir basura
            actaViewModel.vinculoLugar = ""
            actaViewModel.datosPersonasVinculadas.clear()
            actaViewModel.ApellidoNombreInfractor = ""
            actaViewModel.dniInfractor = ""
            actaViewModel.tieneLicencia = false
            actaViewModel.nroLicencia = ""
            actaViewModel.calleInfractor = ""
            actaViewModel.alturaInfractor = ""
            actaViewModel.idProvinciaInfractor = null
            actaViewModel.idLocalidadInfractor = null
        }
    }

    private fun parsearDatosDNI(datos: String, item: ItemDatosVinculoBinding) {
        try {
            val rawData = datos.trim()
            Log.d("SCANNER_RAW", "Trama leída ($rawData)")

            if (rawData.contains("@")) {
                val campos = rawData.split("@")
                Log.d("SCANNER_RAW", "Cantidad de campos: ${campos.size}")

                if (campos.size >= 5) {
                    val apellido = campos[1].trim().uppercase()
                    val nombre = campos[2].trim().uppercase()
                    val dni = campos[4].trim()

                    item.etNombre.setText("$apellido $nombre")
                    item.etDni.setText(dni)

                    // Si el chip de licencia está en "Sí" y la sección es visible, autocompletamos
                    if (item.chipLicenciaSi.isChecked && item.cardLicencia.isVisible) {
                        item.etLicencia.setText(dni)
                    }

                    // Limpiar calle y nro al escanear un nuevo DNI para evitar datos cruzados
                    item.etCalle.text?.clear()
                    item.etCalleNro.text?.clear()
                    
                    // Foco en la calle para continuar la carga
                    item.etCalle.requestFocus()

                    Toast.makeText(requireContext(), "DNI Escaneado: $dni", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Formato de DNI no reconocido", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("SCANNER", "Error al parsear DNI: ${e.message}")
            Toast.makeText(requireContext(), "Error al leer datos del DNI", Toast.LENGTH_SHORT).show()
        }
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
