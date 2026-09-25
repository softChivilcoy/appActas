package com.chivilcoyactas

import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
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

        // Ajustar el número de paso según el área
        if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
            (activity as? MainActivity)?.actualizarProgreso(5)
        } else {
            (activity as? MainActivity)?.actualizarProgreso(7)
        }

        val autoFilterUpper = InputFilter.AllCaps()

        binding.etTestigo1Domicilio.filters = arrayOf(autoFilterUpper)
        binding.etTestigo2Domicilio.filters = arrayOf(autoFilterUpper)

        val callesChivilcoy = resources.getStringArray(R.array.calles_chivilcoy_list)
        val adapterCalles = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            callesChivilcoy
        )

        val etDomicilio = binding.etTestigo1Domicilio as MaterialAutoCompleteTextView

        // 1. Asignar el adaptador de sugerencias
                etDomicilio.setAdapter(adapterCalles)

        // 2. FORZAR que sea editable y muestre el teclado normalmente
                etDomicilio.keyListener = android.text.method.TextKeyListener.getInstance()

        // 3. Sugerir a partir de la primera letra tipeada
                etDomicilio.threshold = 1

        // 4. Si toca la flechita del desplegable, mostrar todas las sugerencias
                etDomicilio.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && etDomicilio.text.isNullOrEmpty()) {
                        etDomicilio.showDropDown()
                    }
                }

        val etDomicilioDos = binding.etTestigo2Domicilio as MaterialAutoCompleteTextView

        // 1. Asignar el adaptador de sugerencias
        etDomicilioDos.setAdapter(adapterCalles)

        // 2. FORZAR que sea editable y muestre el teclado normalmente
        etDomicilioDos.keyListener = android.text.method.TextKeyListener.getInstance()

        // 3. Sugerir a partir de la primera letra tipeada
        etDomicilioDos.threshold = 1

        // 4. Si toca la flechita del desplegable, mostrar todas las sugerencias
        etDomicilioDos.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && etDomicilioDos.text.isNullOrEmpty()) {
                etDomicilioDos.showDropDown()
            }
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

        // Al presionar "Siguiente" o "Hecho" en el teclado del CP Testigo 1
        binding.etTestigo1Cp.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                (activity as? MainActivity)?.hideKeyboard()
                binding.etTestigo1Cp.clearFocus()
                binding.btnAgregarTestigo.requestFocus()
                true
            } else {
                false
            }
        }

        binding.btnAgregarTestigo.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollTestigos.postDelayed({
                    binding.scrollTestigos.smoothScrollTo(0, v.top - 100)
                }, 200)
            }
        }

        // =========================================================
        // 🔄 COMBOS ANIDADOS PROVINCIAS Y LOCALIDADES (SISTEMA IGUAL)
        // =========================================================

        actaViewModel.todasLasProvincias.observe(viewLifecycleOwner) { listaProvincias ->
            val nombresProvincias = listaProvincias.map { it.nombre.uppercase() }
            val adapterProvincia = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresProvincias)

            binding.autoCompleteTestigo1Provincia.setAdapter(adapterProvincia)
            binding.autoCompleteTestigo2Provincia.setAdapter(adapterProvincia)

            if (actaViewModel.Testigo1Provincia.isNotEmpty()) {
                binding.autoCompleteTestigo1Provincia.setText(actaViewModel.Testigo1Provincia, false)
            }
            if (actaViewModel.Testigo2Provincia.isNotEmpty()) {
                binding.autoCompleteTestigo2Provincia.setText(actaViewModel.Testigo2Provincia, false)
            }
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
            autoCompleteView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    autoCompleteView.showDropDown()
                }
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
        }

        binding.autoCompleteTestigo1Localidad.setOnItemClickListener { parent, _, position, _ ->
            val locSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.Testigo1Localidad = locSeleccionada

            if (locSeleccionada.equals("CHIVILCOY", ignoreCase = true)) {
                binding.etTestigo1Cp.setText("6620")
            }
        }

        // --- LÓGICA TESTIGO 2 ---
        binding.autoCompleteTestigo2Provincia.setOnItemClickListener { parent, _, position, _ ->
            val provSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.Testigo2Provincia = provSeleccionada

            val objetoProvincia = actaViewModel.todasLasProvincias.value?.find { it.nombre.uppercase() == provSeleccionada }
            val provinciaId = objetoProvincia?.id ?: 0

            binding.autoCompleteTestigo2Localidad.setText("", false)
            actaViewModel.Testigo2Localidad = ""

            lifecycleScope.launch {
                val localidadesFiltradas = AppDatabase.getDatabase(requireContext())
                    .catalogoDao()
                    .obtenerLocalidadesPorProvincia(provinciaId)

                val nombresLocalidades = localidadesFiltradas.map { it.nombre.uppercase() }

                configurarAutoCompleteLocalidad(
                    binding.autoCompleteTestigo2Localidad as MaterialAutoCompleteTextView,
                    nombresLocalidades
                )

                if (binding.autoCompleteTestigo2Localidad.hasFocus()) {
                    binding.autoCompleteTestigo2Localidad.showDropDown()
                }
            }
        }

        binding.autoCompleteTestigo2Localidad.setOnItemClickListener { parent, _, position, _ ->
            val locSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.Testigo2Localidad = locSeleccionada

            if (locSeleccionada.equals("CHIVILCOY", ignoreCase = true)) {
                binding.etTestigo2Cp.setText("6620")
            }
        }

        vincularBotones()
        recuperarDatosDeCaja()

        configurarAutoScroll(binding.etTestigo1Dni)
        configurarAutoScroll(binding.etTestigo1Nombre)
        configurarAutoScroll(binding.etTestigo2Dni)
        configurarAutoScroll(binding.etTestigo2Nombre)
    }

    private fun vincularBotones() {
        binding.btnVolverTestigos.setOnClickListener { findNavController().navigateUp() }

        binding.btnSiguienteTestigos.setOnClickListener {
            // Guardamos todo en el ViewModel
            actaViewModel.Testigo1Dni = binding.etTestigo1Dni.text.toString()
            actaViewModel.Testigo1Nombre = binding.etTestigo1Nombre.text.toString()
            actaViewModel.Testigo1Provincia = binding.autoCompleteTestigo1Provincia.text.toString()
            actaViewModel.Testigo1Domicilio = binding.etTestigo1Domicilio.text.toString().uppercase()
            actaViewModel.Testigo1Localidad = binding.autoCompleteTestigo1Localidad.text.toString()
            actaViewModel.Testigo1Cp = binding.etTestigo1Cp.text.toString()

            actaViewModel.Testigo2Dni = binding.etTestigo2Dni.text.toString()
            actaViewModel.Testigo2Nombre = binding.etTestigo2Nombre.text.toString()
            actaViewModel.Testigo2Provincia = binding.autoCompleteTestigo2Provincia.text.toString()
            actaViewModel.Testigo2Domicilio = binding.etTestigo2Domicilio.text.toString().uppercase()
            actaViewModel.Testigo2Localidad = binding.autoCompleteTestigo2Localidad.text.toString()
            actaViewModel.Testigo2Cp = binding.etTestigo2Cp.text.toString()

            findNavController().navigate(R.id.action_testigos_to_fotos)
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
        binding.autoCompleteTestigo1Provincia.setText(actaViewModel.Testigo1Provincia, false)
        binding.etTestigo1Domicilio.setText(actaViewModel.Testigo1Domicilio)
        binding.autoCompleteTestigo1Localidad.setText(actaViewModel.Testigo1Localidad, false)
        binding.etTestigo1Cp.setText(actaViewModel.Testigo1Cp)

        binding.etTestigo2Dni.setText(actaViewModel.Testigo2Dni)
        binding.etTestigo2Nombre.setText(actaViewModel.Testigo2Nombre)
        binding.autoCompleteTestigo2Provincia.setText(actaViewModel.Testigo2Provincia, false)
        binding.etTestigo2Domicilio.setText(actaViewModel.Testigo2Domicilio)
        binding.autoCompleteTestigo2Localidad.setText(actaViewModel.Testigo2Localidad, false)
        binding.etTestigo2Cp.setText(actaViewModel.Testigo2Cp)
    }

    override fun onPause() {
        super.onPause()

        // Testigo 1
        actaViewModel.Testigo1Dni = binding.etTestigo1Dni.text.toString().trim()
        actaViewModel.Testigo1Nombre = binding.etTestigo1Nombre.text.toString().trim()
        actaViewModel.Testigo1Provincia = binding.autoCompleteTestigo1Provincia.text.toString().trim()
        actaViewModel.Testigo1Domicilio = binding.etTestigo1Domicilio.text.toString().trim()
        actaViewModel.Testigo1Localidad = binding.autoCompleteTestigo1Localidad.text.toString().trim()
        actaViewModel.Testigo1Cp = binding.etTestigo1Cp.text.toString().trim()

        // Testigo 2
        actaViewModel.Testigo2Dni = binding.etTestigo2Dni.text.toString().trim()
        actaViewModel.Testigo2Nombre = binding.etTestigo2Nombre.text.toString().trim()
        actaViewModel.Testigo2Provincia = binding.autoCompleteTestigo2Provincia.text.toString().trim()
        actaViewModel.Testigo2Domicilio = binding.etTestigo2Domicilio.text.toString().trim()
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

    private fun configurarAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollTestigos.postDelayed({
                    binding.scrollTestigos.smoothScrollTo(0, v.top - 50)
                }, 300)
            }
        }
    }
}