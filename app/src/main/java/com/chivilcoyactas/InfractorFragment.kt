package com.chivilcoyactas

import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.chivilcoyactas.databinding.FragmentInfractorBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.util.Log
import kotlinx.coroutines.launch

class InfractorFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentInfractorBinding? = null
    private val binding get() = _binding!!

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Escaneo cancelado", Toast.LENGTH_LONG).show()
        } else {
            parsearDatosDNI(result.contents)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfractorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.actualizarProgreso(3)

        val esInspeccion = actaViewModel.tipoActa == TipoActa.INSPECCION

        if (esInspeccion) {
            binding.layoutVinculo.visibility = View.VISIBLE
            binding.layoutSoloTransito.visibility = View.GONE
            binding.tvTituloDatos1.text = "DATOS DEL PROPIETARIO"
            configurarSpinnerVinculo()
        } else {
            binding.layoutVinculo.visibility = View.GONE
            binding.layoutSoloTransito.visibility = View.VISIBLE
            binding.tvTituloDatos1.text = "DATOS DEL INFRACTOR"
        }

        binding.btnEscanearDNI.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setPrompt("ALEJE EL DNI (5-10 cm) HASTA QUE HAGA FOCO")
            options.setBeepEnabled(true)
            options.setOrientationLocked(false)
            options.setBarcodeImageEnabled(false)
            options.setTorchEnabled(false)
            barcodeLauncher.launch(options)
        }

        // ==========================================
        // 🚀 COMBOS DINÁMICOS DESDE ROOM (PROV / LOC)
        // ==========================================

        // 1. Carga Dinámica de Provincias desde Room
        // 1. CARGA DE PROVINCIAS
        actaViewModel.todasLasProvincias.observe(viewLifecycleOwner) { listaProvincias ->
            val nombresProvincias = listaProvincias.map { it.nombre.uppercase() }
            val adapterProvincia = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresProvincias)
            binding.autoCompleteProvincia.setAdapter(adapterProvincia)

            if (actaViewModel.provinciaInfractor.isNotEmpty()) {
                binding.autoCompleteProvincia.setText(actaViewModel.provinciaInfractor, false)
            }
        }

        binding.autoCompleteProvincia.setOnItemClickListener { parent, _, position, _ ->
            val provSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.provinciaInfractor = provSeleccionada

            // Buscamos el ID de la provincia elegida
            val objetoProvincia = actaViewModel.todasLasProvincias.value?.find { it.nombre.uppercase() == provSeleccionada }
            val provinciaId = objetoProvincia?.id ?: 0

            // Limpiamos el combo de localidades porque cambió la provincia
            binding.autoCompleteLocalidad.text.clear()
            actaViewModel.localidadInfractor = ""

            // 🔄 Disparamos la búsqueda de localidades de esta provincia en Room (Igual que con los modelos)
            lifecycleScope.launch {
                val localidadesFiltradas = AppDatabase.getDatabase(requireContext()).catalogoDao().obtenerLocalidadesPorProvincia(provinciaId)
                val nombresLocalidades = localidadesFiltradas.map { it.nombre.uppercase() }
                val adapterLocalidad = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresLocalidades)
                binding.autoCompleteLocalidad.setAdapter(adapterLocalidad)
            }
        }

        // 2. SELECCIÓN DE LOCALIDAD
        binding.autoCompleteLocalidad.setOnItemClickListener { parent, _, position, _ ->
            val locSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.localidadInfractor = locSeleccionada

            if (locSeleccionada.equals("CHIVILCOY", ignoreCase = true)) {
                binding.etCP.setText("6620")
            }
        }

        // Configuración de auto scrolls
        configurarAutoScroll(binding.etNombre)
        configurarAutoScroll(binding.etDni)
        configurarAutoScroll(binding.etLicencia)
        configurarAutoScroll(binding.autoCompleteProvincia)
        configurarAutoScroll(binding.autoCompleteLocalidad)
        configurarAutoScroll(binding.etCalle)

        binding.rgDato.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.apSiDatos -> {
                    binding.layoutDatosPersonales.visibility = View.VISIBLE
                    binding.btnEscanearDNI.visibility = View.VISIBLE
                    binding.tilObservaciones.visibility = View.GONE
                }
                R.id.apNoDatos, R.id.apNoIdentifica -> {
                    binding.layoutDatosPersonales.visibility = View.GONE
                    binding.btnEscanearDNI.visibility = View.GONE
                    binding.tilObservaciones.visibility = View.VISIBLE
                }
            }
        }

        binding.rgLicencia.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbSiLicencia -> {
                    binding.tilLicencia.visibility = View.VISIBLE
                }
                R.id.rbNoLicencia -> {
                    binding.tilLicencia.visibility = View.GONE
                    binding.etLicencia.text?.clear()
                    actaViewModel.nroLicencia = ""
                }
            }
        }

        binding.etObservaciones.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollInfractor.postDelayed({
                    binding.scrollInfractor.smoothScrollTo(0, binding.tilObservaciones.top)
                }, 200)
            }
        }

        binding.etCalleNro.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                binding.btnSiguiente.performClick()
                true
            } else false
        }

        binding.btnVolver.setOnClickListener {
            if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
                findNavController().navigate(R.id.action_infractor_to_procedimiento_VUELTA)
            } else {
                findNavController().navigate(R.id.action_infractor_to_faltas_VUELTA)
            }
        }

        binding.btnSiguiente.setOnClickListener {
            val opcionSeleccionada = binding.rgDato.checkedRadioButtonId

            if (opcionSeleccionada == -1) {
                Toast.makeText(context, "Por favor, seleccione una condición del infractor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (opcionSeleccionada == binding.apSiDatos.id) {
                if (binding.etNombre.text.isNullOrBlank()) {
                    binding.etNombre.error = "Campo Obligatorio"
                    return@setOnClickListener
                }
                if (binding.etDni.text.toString().isEmpty()) {
                    binding.etDni.error = "Campo Obligatorio"
                    return@setOnClickListener
                }
            }

            if (opcionSeleccionada == binding.apNoDatos.id || opcionSeleccionada == binding.apNoIdentifica.id) {
                if (binding.etObservaciones.text.isNullOrBlank()) {
                    binding.etObservaciones.error = "Debe justificar el motivo de la falta de datos"
                    return@setOnClickListener
                }
            }

            guardarDatosEnCaja()

            if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
                (activity as? MainActivity)?.actualizarProgreso(4)
                findNavController().navigate(R.id.action_infractor_to_faltas_inspeccion)
            } else {
                findNavController().navigate(R.id.action_infractor_to_vehiculo)
            }
        }

        binding.scrollInfractor.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        cargarDatosDeCaja()
    }

    private fun cargarDatosDeCaja() {
        binding.etNombre.setText(actaViewModel.ApellidoNombreInfractor)
        binding.etDni.setText(actaViewModel.dniInfractor)
        binding.etLicencia.setText(actaViewModel.nroLicencia)

        // CORREGIDO: Apunta a los AutocompleteTextView
        binding.autoCompleteProvincia.setText(if (actaViewModel.provinciaInfractor.isEmpty()) "BUENOS AIRES" else actaViewModel.provinciaInfractor, false)
        binding.autoCompleteLocalidad.setText(if (actaViewModel.localidadInfractor.isEmpty()) "CHIVILCOY" else actaViewModel.localidadInfractor, false)
        binding.etCP.setText(if (actaViewModel.cpInfractor.isEmpty()) "6620" else actaViewModel.cpInfractor)

        binding.etCalle.setText(actaViewModel.calleInfractor)
        binding.etCalleNro.setText(actaViewModel.alturaInfractor)

        when (actaViewModel.niegaDatos) {
            0 -> {
                binding.rgDato.check(R.id.apSiDatos)
                binding.layoutDatosPersonales.visibility = View.VISIBLE
                binding.btnEscanearDNI.visibility = View.VISIBLE
                binding.tilObservaciones.visibility = View.GONE
            }
            1 -> {
                binding.rgDato.check(R.id.apNoDatos)
                binding.layoutDatosPersonales.visibility = View.GONE
                binding.btnEscanearDNI.visibility = View.GONE
                binding.tilObservaciones.visibility = View.VISIBLE
            }
            2 -> {
                binding.rgDato.check(R.id.apNoIdentifica)
                binding.layoutDatosPersonales.visibility = View.GONE
                binding.btnEscanearDNI.visibility = View.GONE
                binding.tilObservaciones.visibility = View.VISIBLE
            }
            else -> {
                binding.rgDato.clearCheck()
                binding.layoutDatosPersonales.visibility = View.GONE
                binding.btnEscanearDNI.visibility = View.GONE
                binding.tilObservaciones.visibility = View.GONE
            }
        }

        if (actaViewModel.tieneLicencia) {
            binding.rgLicencia.check(R.id.rbSiLicencia)
            binding.tilLicencia.visibility = View.VISIBLE
        } else {
            binding.rgLicencia.check(R.id.rbNoLicencia)
            binding.tilLicencia.visibility = View.GONE
        }

        binding.etObservaciones.setText(actaViewModel.motivoDatos)

        if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
            binding.spinnerVinculo.setText(actaViewModel.vinculoLugar, false)

            if (actaViewModel.vinculoLugar != "Propietario" && !actaViewModel.vinculoLugar.isNullOrBlank()) {
                binding.layoutDatosVinculoExtra.visibility = View.VISIBLE
                binding.etNombreVinculo.setText(actaViewModel.nombreResponsable)
                binding.etDniVinculo.setText(actaViewModel.dniResponsable)
                binding.etCalleVinculo.setText(actaViewModel.calleResponsable)
                binding.etCalleNroVinculo.setText(actaViewModel.alturaResponsable)
            }
        }
    }

    private fun configurarSpinnerVinculo() {
        val opciones = arrayOf("Propietario", "Encargado", "Razon Social", "Locatario", "Conductor", "Otro")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opciones)
        (binding.spinnerVinculo as? AutoCompleteTextView)?.apply {
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                val seleccion = opciones[position]
                actaViewModel.vinculoLugar = seleccion

                if (seleccion == "Propietario") {
                    binding.layoutDatosVinculoExtra.visibility = View.GONE
                } else {
                    binding.layoutDatosVinculoExtra.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun guardarDatosEnCaja() {
        actaViewModel.niegaDatos = when (binding.rgDato.checkedRadioButtonId) {
            R.id.apSiDatos -> 0
            R.id.apNoDatos -> 1
            R.id.apNoIdentifica -> 2
            else -> -1
        }

        actaViewModel.motivoDatos = binding.etObservaciones.text.toString()
        actaViewModel.ApellidoNombreInfractor = binding.etNombre.text.toString()
        actaViewModel.dniInfractor = binding.etDni.text.toString()
        actaViewModel.tieneLicencia = (binding.rgLicencia.checkedRadioButtonId == R.id.rbSiLicencia)
        actaViewModel.nroLicencia = binding.etLicencia.text.toString()

        // CORREGIDO: Lee de los AutocompleteTextView nuevos
        actaViewModel.provinciaInfractor = binding.autoCompleteProvincia.text.toString().uppercase()
        actaViewModel.localidadInfractor = binding.autoCompleteLocalidad.text.toString().uppercase()
        actaViewModel.cpInfractor = binding.etCP.text.toString()

        actaViewModel.calleInfractor = binding.etCalle.text.toString()
        actaViewModel.alturaInfractor = binding.etCalleNro.text.toString()

        if (binding.layoutDatosVinculoExtra.visibility == View.VISIBLE) {
            actaViewModel.nombreResponsable = binding.etNombreVinculo.text.toString()
            actaViewModel.dniResponsable = binding.etDniVinculo.text.toString()
            actaViewModel.calleResponsable = binding.etCalleVinculo.text.toString()
            actaViewModel.alturaResponsable = binding.etCalleNroVinculo.text.toString()
        }
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

                    binding.etNombre.setText("$apellido $nombre")
                    binding.etDni.setText(dni)
                    binding.etLicencia.setText(dni)

                    Toast.makeText(requireContext(), "DNI detectado: $dni", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("SCANNER", "Error al parsear: ${e.message}")
        }
    }

    private fun configurarAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollInfractor.isSmoothScrollingEnabled = true
                binding.scrollInfractor.postDelayed({
                    v.scrollTo(0, 0)
                    val rect = android.graphics.Rect(0, 0, v.width, v.height)
                    v.requestRectangleOnScreen(rect, false)
                }, 300)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}