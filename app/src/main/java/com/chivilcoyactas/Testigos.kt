package com.chivilcoyactas

import android.graphics.Rect
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chivilcoyactas.databinding.FragmentTestigosBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class TestigosFragment : Fragment() {

    private var testigoActualEscaneando = 1 // Para saber si cargar en el 1 o en el 2

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentTestigosBinding? = null
    private val binding get() = _binding!!

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Escaneo cancelado", Toast.LENGTH_LONG).show()
        } else {
            parsearDatosDNI(result.contents)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTestigosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Forzar scroll al inicio al entrar o volver
        binding.scrollTestigos.post {
            binding.scrollTestigos.scrollTo(0, 0)
        }

        // Aplicar padding dinámico según el teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollTestigos) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val density = resources.displayMetrics.density
            val extraPadding = (32 * density).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeHeight + extraPadding)
            insets
        }

        // Ajustar el número de paso según el área
        if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
            (activity as? MainActivity)?.actualizarProgreso(5)
        } else {
            (activity as? MainActivity)?.actualizarProgreso(7)
        }

        val autoFilterUpper = InputFilter.AllCaps()

        binding.etTestigo1Calle.filters = arrayOf(autoFilterUpper)
        binding.etTestigo2Calle.filters = arrayOf(autoFilterUpper)

        val callesChivilcoy = resources.getStringArray(R.array.calles_chivilcoy_list)
        val adapterCalles = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            callesChivilcoy
        )

        binding.etTestigo1Calle.dropDownVerticalOffset = -20
        binding.etTestigo2Calle.dropDownVerticalOffset = -20

        val etDomicilio = binding.etTestigo1Calle as MaterialAutoCompleteTextView

        // 1. Asignar el adaptador de sugerencias
                etDomicilio.setAdapter(adapterCalles)

        // 2. FORZAR que sea editable y muestre el teclado normalmente
                etDomicilio.keyListener = android.text.method.TextKeyListener.getInstance()

        // 3. Sugerir a partir de la primera letra tipeada
                etDomicilio.threshold = 1

        // 4. Si toca la flechita del desplegable, mostrar todas las sugerencias
        etDomicilio.setOnClickListener {
            etDomicilio.showDropDown()
        }

        etDomicilio.setOnItemClickListener { _, _, _, _ ->
            binding.etTestigo1CalleNro.requestFocus()
        }

        val etDomicilioDos = binding.etTestigo2Calle as MaterialAutoCompleteTextView

        // 1. Asignar el adaptador de sugerencias
        etDomicilioDos.setAdapter(adapterCalles)

        // 2. FORZAR que sea editable y muestre el teclado normalmente
        etDomicilioDos.keyListener = android.text.method.TextKeyListener.getInstance()

        // 3. Sugerir a partir de la primera letra tipeada
        etDomicilioDos.threshold = 1

        // 4. Si toca la flechita del desplegable, mostrar todas las sugerencias
        etDomicilioDos.setOnClickListener {
            etDomicilioDos.showDropDown()
        }

        etDomicilioDos.setOnItemClickListener { _, _, _, _ ->
            binding.etTestigo2CalleNro.requestFocus()
        }

        // 1. Lógica del botón Agregar
        binding.btnAgregarTestigo.setOnClickListener {
            if (binding.cardTestigo1.visibility == View.GONE) {
                binding.cardTestigo1.visibility = View.VISIBLE
                binding.btnEscanearDniTestigo.visibility = View.VISIBLE
                binding.etTestigo1Dni.requestFocus()
                testigoActualEscaneando = 1
            } else if (binding.cardTestigo2.visibility == View.GONE) {
                binding.cardTestigo2.visibility = View.VISIBLE
                testigoActualEscaneando = 2
                binding.btnAgregarTestigo.visibility = View.GONE
                binding.etTestigo2Dni.requestFocus()
            }
        }

        // Al presionar Escanear
        binding.btnEscanearDniTestigo.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setPrompt("ALEJE EL DNI (5-10 cm) HASTA QUE HAGA FOCO")
            options.setBeepEnabled(true)
            options.setOrientationLocked(false)
            options.setBarcodeImageEnabled(false)
            options.setTorchEnabled(false)
            barcodeLauncher.launch(options)
        }

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollTestigos.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }



        binding.btnAgregarTestigo.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollTestigos.postDelayed({
                    binding.scrollTestigos.smoothScrollTo(0, v.top - 100)
                }, 200)
            }
        }

        // Testigo 1. Nro Calle PResionar Eneter
        binding.etTestigo1CalleNro.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                (activity as? MainActivity)?.hideKeyboard()
                binding.etTestigo1CalleNro.clearFocus()
                true
            } else {
                false
            }
        }


        // ==========================================
        // 🚀 COMBOS DINÁMICOS DESDE ROOM (PROV / LOC)
        // ==========================================

        // 1. Carga Dinámica de Provincias desde Room
        // 1. CARGA DE PROVINCIAS
        actaViewModel.todasLasProvincias.observe(viewLifecycleOwner) { listaProvincias ->
            if (listaProvincias.isNullOrEmpty()) return@observe

            // Llenamos el combo de Provincias
            val nombresProvincias = listaProvincias.map { it.nombre.uppercase() }
            val adapterProvincia = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                nombresProvincias
            )
            binding.autoCompleteTestigo1Provincia.setAdapter(adapterProvincia)
            binding.autoCompleteTestigo2Provincia.setAdapter(adapterProvincia)

            // Seteamos la provincia activa en la vista y en el ViewModel si está vacía
            val provActual = if (actaViewModel.provinciaInfractor.isNotEmpty()) {
                actaViewModel.provinciaInfractor
            } else {
                "BUENOS AIRES".also { actaViewModel.provinciaInfractor = it }
            }
            //binding.autoCompleteProvincia.setText(provActual, false)
            if (actaViewModel.Testigo1Provincia.isNotEmpty()) {
                binding.autoCompleteTestigo1Provincia.setText(provActual, false)
            }
            if (actaViewModel.Testigo2Provincia.isNotEmpty()) {
                binding.autoCompleteTestigo2Provincia.setText(provActual, false)
            }

            // 🚀 AHORA SÍ: Como la lista de provincias ya existe en memoria,
            // cargamos las localidades para la provincia seleccionada (Buenos Aires por defecto)
            cargarLocalidadesPorProvinciaNombre(provActual)
        }


        // =========================================================
        // 🔄 HELPER PARA CONFIGURAR LOCALIDADES
        // =========================================================
        fun configurarAutoCompleteLocalidad(
            autoCompleteView: MaterialAutoCompleteTextView,
            nombresLocalidades: List<String>
        ) {
            val adapterLocalidad = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                nombresLocalidades
            )

            autoCompleteView.setAdapter(adapterLocalidad)
            autoCompleteView.threshold = 1

            // Forzar que despliegue la lista al hacer clic o ganar foco
            autoCompleteView.setOnClickListener {
                autoCompleteView.showDropDown()
            }
        }

        // --- LÓGICA TESTIGO 1 ---
        binding.autoCompleteTestigo1Provincia.setOnItemClickListener { parent, _, position, _ ->
            val provSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.Testigo1Provincia = provSeleccionada

            val objetoProvincia = actaViewModel.todasLasProvincias.value?.find { it.nombre.uppercase() == provSeleccionada }
            val provinciaId = objetoProvincia?.id ?: 0

            // Limpiamos el texto sin disparar filtros
            binding.autoCompleteTestigo1Localidad.setText("", false)
            actaViewModel.Testigo1Localidad = ""

            lifecycleScope.launch {
                val localidadesFiltradas = AppDatabase.getDatabase(requireContext())
                    .catalogoDao()
                    .obtenerLocalidadesPorProvincia(provinciaId)

                val nombresLocalidades = localidadesFiltradas.map { it.nombre.uppercase() }

                // Aplicamos la configuración con el helper
                configurarAutoCompleteLocalidad(
                    binding.autoCompleteTestigo1Localidad as MaterialAutoCompleteTextView,
                    nombresLocalidades
                )

                // Si el usuario ya estaba parado sobre el campo, desplegar de inmediato
                if (binding.autoCompleteTestigo1Localidad.hasFocus()) {
                    binding.autoCompleteTestigo1Localidad.showDropDown()
                }
            }
            actualizarTextoResumenUbicacion()
            binding.autoCompleteTestigo1Localidad.requestFocus()
        }

        binding.autoCompleteTestigo1Localidad.setOnItemClickListener { parent, _, position, _ ->
            val locSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.Testigo1Localidad = locSeleccionada

            if (locSeleccionada.equals("CHIVILCOY", ignoreCase = true)) {
                binding.etTestigo1Cp.setText("6620")
            }
            actualizarTextoResumenUbicacion()
            binding.etTestigo1Cp.requestFocus()
        }

        // 3. Al presionar "Hecho / Enter" en C.P. -> Oculta el teclado
        binding.etTestigo1Cp.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                actualizarTextoResumenUbicacion()
                (activity as? MainActivity)?.hideKeyboard()
                binding.etTestigo1Cp.clearFocus()
                true
            } else {
                false
            }
        }




        // --- LÓGICA TESTIGO 2 ---
        binding.autoCompleteTestigo2Provincia.setOnItemClickListener { parent, _, position, _ ->
            val provSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.Testigo2Provincia = provSeleccionada

            val objetoProvincia = actaViewModel.todasLasProvincias.value?.find { it.nombre.uppercase() == provSeleccionada }
            val provinciaId = objetoProvincia?.id ?: 0

            // Limpiamos el texto sin disparar filtros
            binding.autoCompleteTestigo2Localidad.setText("", false)
            actaViewModel.Testigo2Localidad = ""

            lifecycleScope.launch {
                val localidadesFiltradas = AppDatabase.getDatabase(requireContext())
                    .catalogoDao()
                    .obtenerLocalidadesPorProvincia(provinciaId)

                val nombresLocalidades = localidadesFiltradas.map { it.nombre.uppercase() }

                // Aplicamos la configuración con el helper
                configurarAutoCompleteLocalidad(
                    binding.autoCompleteTestigo2Localidad as MaterialAutoCompleteTextView,
                    nombresLocalidades
                )

                // Si el usuario ya estaba parado sobre el campo, desplegar de inmediato
                if (binding.autoCompleteTestigo2Localidad.hasFocus()) {
                    binding.autoCompleteTestigo2Localidad.showDropDown()
                }
            }
            actualizarTextoResumenUbicacionT2()
            binding.autoCompleteTestigo2Localidad.requestFocus()
        }

        binding.autoCompleteTestigo2Localidad.setOnItemClickListener { parent, _, position, _ ->
            val locSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.Testigo2Localidad = locSeleccionada

            if (locSeleccionada.equals("CHIVILCOY", ignoreCase = true)) {
                binding.etTestigo2Cp.setText("6620")
            }
            actualizarTextoResumenUbicacionT2()
            binding.etTestigo2Cp.requestFocus()
        }

        // 3. Al presionar "Hecho / Enter" en C.P. -> Oculta el teclado
        binding.etTestigo2Cp.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                actualizarTextoResumenUbicacionT2()
                (activity as? MainActivity)?.hideKeyboard()
                binding.etTestigo2Cp.clearFocus()
                true
            } else {
                false
            }
        }


        // Nro Calle PResionar Eneter
        binding.etTestigo2CalleNro.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                (activity as? MainActivity)?.hideKeyboard()
                binding.etTestigo2CalleNro.clearFocus()
                true
            } else {
                false
            }
        }

        // 6. UBicacion Provincia.Localidad
        //TESTIGO 1
        // Mostrar / Ocultar ubicación avanzada
        binding.cardUbicacionTestigo1.setOnClickListener {
            if (binding.containerUbicacionExtraTestigo1.visibility == View.GONE) {
                binding.containerUbicacionExtraTestigo1.visibility = View.VISIBLE
                binding.autoCompleteTestigo1Provincia.requestFocus()
            } else {
                binding.containerUbicacionExtraTestigo1.visibility = View.GONE
            }
        }

        //TESTIGO 2
        // Mostrar / Ocultar ubicación avanzada
        binding.cardUbicacionTestigo2.setOnClickListener {
            if (binding.containerUbicacionExtraTestigo2.visibility == View.GONE) {
                binding.containerUbicacionExtraTestigo2.visibility = View.VISIBLE
                binding.autoCompleteTestigo2Provincia.requestFocus()
            } else {
                binding.containerUbicacionExtraTestigo2.visibility = View.GONE
            }
        }

        vincularBotones()
        recuperarDatosDeCaja()

        setupFocusListeners()
    }

    private fun setupFocusListeners() {
        // AutoCompleteTextViews
        val combos = listOf(
            binding.etTestigo1Calle,
            binding.autoCompleteTestigo1Provincia,
            binding.autoCompleteTestigo1Localidad,
            binding.etTestigo2Calle,
            binding.autoCompleteTestigo2Provincia,
            binding.autoCompleteTestigo2Localidad
        )
        combos.forEach { combo ->
            combo.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as? android.widget.AutoCompleteTextView)?.showDropDown()
                    ejecutarAutoScroll(v)
                    v.setBackgroundResource(R.drawable.bg_edittext_focused)
                } else {
                    v.setBackgroundResource(0)
                }
            }
            combo.setOnClickListener {
                (it as? android.widget.AutoCompleteTextView)?.showDropDown()
            }
        }

        // EditTexts
        val edits = listOf(
            binding.etTestigo1Dni,
            binding.etTestigo1Nombre,
            binding.etTestigo1CalleNro,
            binding.etTestigo1Cp,
            binding.etTestigo2Dni,
            binding.etTestigo2Nombre,
            binding.etTestigo2CalleNro,
            binding.etTestigo2Cp
        )
        edits.forEach { edit ->
            edit.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as? com.google.android.material.textfield.TextInputEditText)?.setSelectAllOnFocus(true)
                    ejecutarAutoScroll(v)
                    v.setBackgroundResource(R.drawable.bg_edittext_focused)
                } else {
                    v.setBackgroundResource(0)
                }
            }
        }
    }

    private fun ejecutarAutoScroll(view: View) {
        binding.scrollTestigos.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            val rect = Rect()
            view.getDrawingRect(rect)
            binding.scrollTestigos.offsetDescendantRectToMyCoords(view, rect)

            val density = resources.displayMetrics.density
            val offsetTop = (90 * density).toInt()

            val targetY = rect.top - offsetTop
            binding.scrollTestigos.smoothScrollTo(0, targetY.coerceAtLeast(0))
        }, 500)
    }

    private fun vincularBotones() {
        binding.btnVolverTestigos.setOnClickListener { findNavController().navigateUp() }

        binding.btnSiguienteTestigos.setOnClickListener {
            // Guardamos todo en el ViewModel
            actaViewModel.Testigo1Dni = binding.etTestigo1Dni.text.toString()
            actaViewModel.Testigo1Nombre = binding.etTestigo1Nombre.text.toString()
            actaViewModel.Testigo1Provincia = binding.autoCompleteTestigo1Provincia.text.toString()
            actaViewModel.Testigo1Calle = binding.etTestigo1Calle.text.toString().uppercase()
            actaViewModel.Testigo1Localidad = binding.autoCompleteTestigo1Localidad.text.toString()
            actaViewModel.Testigo1Cp = binding.etTestigo1Cp.text.toString()

            actaViewModel.Testigo2Dni = binding.etTestigo2Dni.text.toString()
            actaViewModel.Testigo2Nombre = binding.etTestigo2Nombre.text.toString()
            actaViewModel.Testigo2Provincia = binding.autoCompleteTestigo2Provincia.text.toString()
            actaViewModel.Testigo2Calle = binding.etTestigo2Calle.text.toString().uppercase()
            actaViewModel.Testigo2Localidad = binding.autoCompleteTestigo2Localidad.text.toString()
            actaViewModel.Testigo2Cp = binding.etTestigo2Cp.text.toString()

            findNavController().navigate(R.id.action_testigos_to_fotos)
        }
    }

    /**
     * Carga asíncrona de localidades en el AutoCompleteTextView según la provincia elegida
     */
    private fun cargarLocalidadesPorProvinciaNombre(nombreProvincia: String) {
        lifecycleScope.launch {
            val objetoProvincia = actaViewModel.todasLasProvincias.value?.find {
                it.nombre.equals(nombreProvincia, ignoreCase = true)
            }
            val provinciaId = objetoProvincia?.id ?: 0

            val localidadesFiltradas = AppDatabase.getDatabase(requireContext())
                .catalogoDao()
                .obtenerLocalidadesPorProvincia(provinciaId)

            val nombresLocalidades = localidadesFiltradas.map { it.nombre.uppercase() }
            val adapterLocalidad = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                nombresLocalidades
            )
            binding.autoCompleteTestigo1Localidad.setAdapter(adapterLocalidad)
            binding.autoCompleteTestigo2Localidad.setAdapter(adapterLocalidad)
        }
    }
    private fun recuperarDatosDeCaja() {
        if (actaViewModel.Testigo1Dni.isNotEmpty() || actaViewModel.Testigo1Nombre.isNotEmpty()) {
            binding.cardTestigo1.visibility = View.VISIBLE
        }
        if (actaViewModel.Testigo2Dni.isNotEmpty() || actaViewModel.Testigo2Nombre.isNotEmpty()) {
            binding.cardTestigo2.visibility = View.VISIBLE
            binding.btnAgregarTestigo.visibility = View.GONE
        } else if (binding.cardTestigo1.visibility == View.VISIBLE) {
            binding.btnAgregarTestigo.visibility = View.VISIBLE
        }

        binding.etTestigo1Dni.setText(actaViewModel.Testigo1Dni)
        binding.etTestigo1Nombre.setText(actaViewModel.Testigo1Nombre)

        binding.autoCompleteTestigo1Provincia.setText(if (actaViewModel.Testigo1Provincia.isEmpty()) "BUENOS AIRES" else actaViewModel.Testigo1Provincia, false)
        binding.autoCompleteTestigo1Localidad.setText(if (actaViewModel.Testigo1Localidad.isEmpty()) "CHIVILCOY" else actaViewModel.Testigo1Localidad, false)

        binding.etTestigo1Calle.setText(actaViewModel.Testigo1Calle)
        binding.etTestigo1CalleNro.setText(actaViewModel.Testigo1CalleNro)

        //binding.autoCompleteTestigo1Localidad.setText(actaViewModel.Testigo1Localidad, false)
        binding.etTestigo1Cp.setText(if (actaViewModel.Testigo1Cp.isEmpty()) "6620" else actaViewModel.Testigo1Cp)

        binding.etTestigo2Dni.setText(actaViewModel.Testigo2Dni)
        binding.etTestigo2Nombre.setText(actaViewModel.Testigo2Nombre)
        binding.autoCompleteTestigo2Provincia.setText(actaViewModel.Testigo2Provincia, false)

        binding.etTestigo2Calle.setText(actaViewModel.Testigo2Calle)
        binding.etTestigo2CalleNro.setText(actaViewModel.Testigo2CalleNro)

        binding.autoCompleteTestigo2Localidad.setText(actaViewModel.Testigo2Localidad, false)
        binding.etTestigo2Cp.setText(if (actaViewModel.Testigo2Cp.isEmpty()) "6620" else actaViewModel.Testigo2Cp)
    }

    // --- FUNCIÓN DE ACTUALIZACIÓN DEL CARD RESUMEN ---
    private fun actualizarTextoResumenUbicacion() {
        val prov = binding.autoCompleteTestigo1Provincia.text.toString().trim()
        val loc = binding.autoCompleteTestigo1Localidad.text.toString().trim()
        val cp = binding.etTestigo1Cp.text.toString().trim()

        binding.tvUbicacionResumenT1.text = "$loc, $prov (CP $cp)"
    }

    // --- FUNCIÓN DE ACTUALIZACIÓN DEL CARD RESUMEN ---
    private fun actualizarTextoResumenUbicacionT2() {
        val prov = binding.autoCompleteTestigo2Provincia.text.toString().trim()
        val loc = binding.autoCompleteTestigo2Localidad.text.toString().trim()
        val cp = binding.etTestigo2Cp.text.toString().trim()

        binding.tvUbicacionResumenT2.text = "$loc, $prov (CP $cp)"
    }


    override fun onPause() {
        super.onPause()

        // Testigo 1
        actaViewModel.Testigo1Dni = binding.etTestigo1Dni.text.toString().trim()
        actaViewModel.Testigo1Nombre = binding.etTestigo1Nombre.text.toString().trim()
        actaViewModel.Testigo1Provincia = binding.autoCompleteTestigo1Provincia.text.toString().trim()
        actaViewModel.Testigo1Calle = binding.etTestigo1Calle.text.toString().trim()
        actaViewModel.Testigo1CalleNro = binding.etTestigo1CalleNro.text.toString().trim()
        actaViewModel.Testigo1Localidad = binding.autoCompleteTestigo1Localidad.text.toString().trim()
        actaViewModel.Testigo1Cp = binding.etTestigo1Cp.text.toString().trim()

        // Testigo 2
        actaViewModel.Testigo2Dni = binding.etTestigo2Dni.text.toString().trim()
        actaViewModel.Testigo2Nombre = binding.etTestigo2Nombre.text.toString().trim()
        actaViewModel.Testigo2Provincia = binding.autoCompleteTestigo2Provincia.text.toString().trim()

        actaViewModel.Testigo2Calle = binding.etTestigo2Calle.text.toString().trim()
        actaViewModel.Testigo2CalleNro = binding.etTestigo2CalleNro.text.toString().trim()

        actaViewModel.Testigo2Localidad = binding.autoCompleteTestigo2Localidad.text.toString().trim()
        actaViewModel.Testigo2Cp = binding.etTestigo2Cp.text.toString().trim()
    }

    private fun parsearDatosDNI(datos: String) {
        try {
            val rawData = datos.trim()
            if (rawData.contains("@")) {
                val campos = rawData.split("@")
                if (campos.size >= 5) {
                    val apellido = campos[1].trim().uppercase()
                    val nombre = campos[2].trim().uppercase()
                    val dni = campos[4].trim()

                    if (testigoActualEscaneando == 1) {
                        binding.etTestigo1Dni.setText(dni)
                        binding.etTestigo1Nombre.setText("$apellido $nombre")
                    } else {
                        binding.etTestigo2Dni.setText(dni)
                        binding.etTestigo2Nombre.setText("$apellido $nombre")
                    }
                    Toast.makeText(requireContext(), "DNI detectado: $dni", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w("SCANNER", "Formato no estándar: $rawData")
            }
        } catch (e: Exception) {
            Log.e("SCANNER", "Error al parsear: ${e.message}")
            Toast.makeText(requireContext(), "Error al leer los datos del documento", Toast.LENGTH_SHORT).show()
        }
    }


}