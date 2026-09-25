package com.chivilcoyactas

import android.os.Bundle
import android.text.InputFilter
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
import androidx.fragment.app.activityViewModels
import com.chivilcoyactas.databinding.FragmentVehiculoBinding
import kotlin.getValue
import android.view.inputmethod.EditorInfo
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

        // 1. Actualizar barra al Paso 4
        (activity as? MainActivity)?.actualizarProgreso(4)

        // En el onViewCreated o al inicializar tus vistas:
        val autoFilterUpper = InputFilter.AllCaps()


        binding.autoCompleteMarca.filters = arrayOf(autoFilterUpper)
        binding.autoCompleteModelo.filters = arrayOf(autoFilterUpper)

        // Habilitar edición de texto libre y teclado estándar en Marcas y Modelos
        binding.autoCompleteMarca.keyListener = android.text.method.TextKeyListener.getInstance()
        binding.autoCompleteModelo.keyListener = android.text.method.TextKeyListener.getInstance()

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

            val objetoTipo = actaViewModel.todosLosTiposVehiculo.value?.find { it.nombre == seleccion }
            actaViewModel.idTipoVehiculoSeleccionado = objetoTipo?.id ?: 0

            binding.etDominio.text?.clear()
        }

        // 2. CARGA DE MARCAS Y RESTAURACIÓN DE MODELOS
        actaViewModel.todasLasMarcas.observe(viewLifecycleOwner) { listaMarcas ->
            val nombresMarcas = listaMarcas.map { it.nombre }
            val adapterMarca = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresMarcas)
            binding.autoCompleteMarca.setAdapter(adapterMarca)

            if (actaViewModel.marca.isNotEmpty()) {
                binding.autoCompleteMarca.setText(actaViewModel.marca, false)

                // Verificamos de forma segura usando el operador elvis (?:) o asignación local
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
            val marcaId = objetoMarca?.id ?: 0 // Si no lo encuentra, asigna 0

            // Guardamos en el ViewModel (será null si es 0)
            actaViewModel.idMarcaSeleccionada = if (marcaId > 0) marcaId else null

            // Limpiamos el combo de modelos al cambiar la marca
            binding.autoCompleteModelo.text?.clear()
            actaViewModel.modelo = ""
            actaViewModel.idModeloSeleccionado = null

            // Si tenemos un ID válido, cargamos los modelos de esa marca
            if (marcaId > 0) {
                cargarModelosPorMarca(marcaId)
            }
        }

        // 3. SELECCIÓN DE MODELO
        binding.autoCompleteModelo.setOnItemClickListener { parent, _, position, _ ->
            val modeloSeleccionado = parent.getItemAtPosition(position).toString()
            actaViewModel.modelo = modeloSeleccionado

            // Buscamos el ID real desde la lista filtrada guardada en memoria
            val objetoModelo = listaModelosActuales.find { it.nombre.equals(modeloSeleccionado, ignoreCase = true) }
            actaViewModel.idModeloSeleccionado = objetoModelo?.id ?: 0
        }

        // Listener para formatear patente
        binding.etDominio.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                val texto = s.toString().replace(" ", "").uppercase()
                val tipo = actaViewModel.tipoVehiculo

                if (tipo == "MOTOCICLETA" || tipo == "CICLOMOTOR") {
                    actualizarTecladoMoto(texto)
                } else {
                    actualizarTecladoAuto(texto)
                }

                binding.etDominio.setSelection(binding.etDominio.text?.length ?: 0)
            }
        })

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

        // Configurar autoscroll
        configurarAutoScroll(binding.etDominio)
        configurarAutoScroll(binding.autoCompleteMarca)
        configurarAutoScroll(binding.autoCompleteModelo)
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
        // 1. Datos del responsable/propietario
        if (!binding.checkEsPropietario.isChecked) {
            actaViewModel.nombreResponsable = binding.etNombrePropietario.text.toString().trim().uppercase()
            actaViewModel.dniResponsable = binding.etDniPropietario.text.toString().trim()
        } else {
            actaViewModel.nombreResponsable = actaViewModel.ApellidoNombreInfractor
            actaViewModel.dniResponsable = actaViewModel.dniInfractor
        }

        // 2. Guardar textos en el ViewModel desde las vistas
        actaViewModel.tipoVehiculo = binding.autoCompleteTipoVehiculo.text.toString().trim()
        actaViewModel.marca = binding.autoCompleteMarca.text.toString().trim().uppercase()
        actaViewModel.modelo = binding.autoCompleteModelo.text.toString().trim().uppercase()
        actaViewModel.dominio = binding.etDominio.text.toString().trim().uppercase()
        actaViewModel.esPropietario = binding.checkEsPropietario.isChecked

        // 3. Validación de consistencia para IDs
        // Si el usuario modificó manualmente el texto y no coincide con una selección previa de catálogo, reseteamos el ID a null
        if (actaViewModel.marca.isEmpty()) {
            actaViewModel.idMarcaSeleccionada = null
        }
        if (actaViewModel.modelo.isEmpty()) {
            actaViewModel.idModeloSeleccionado = null
        }

        // 4. Navegar al paso 5 (Alcoholemia)
        findNavController().navigate(R.id.action_vehiculo_to_alcoholemia)
    }

    private fun configurarAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollVehiculo.isSmoothScrollingEnabled = true
                binding.scrollVehiculo.postDelayed({
                    val rect = android.graphics.Rect(0, 0, v.width, v.height)
                    v.requestRectangleOnScreen(rect, false)
                }, 300)
            }
        }
    }

    private fun actualizarTecladoAuto(texto: String) {
        when {
            texto.length < 3 -> abrirTecladoTexto()
            texto.length in 3..4 -> abrirTecladoNumerico()
            texto.length >= 5 -> {
                val centroNumerico = texto.substring(2, 5)
                if (centroNumerico.all { it.isDigit() }) {
                    abrirTecladoTexto()
                } else {
                    abrirTecladoNumerico()
                }
            }
        }
    }

    private fun actualizarTecladoMoto(texto: String) {
        when {
            // 1. Si está vacío, empezamos con TEXTO (sirve para ambos formatos)
            texto.isEmpty() -> abrirTecladoTexto()

            // 2. DETECCIÓN DE FORMATO: Miramos el primer carácter
            texto.length >= 1 -> {
                val empiezaConLetra = texto[0].isLetter()

                if (empiezaConLetra) {
                    // FORMATO NUEVO: A 123 BCD
                    if (texto.length in 1..3) {
                        abrirTecladoNumerico() // Después de la A, vienen números
                    } else {
                        abrirTecladoTexto()    // Después de los números, vuelven letras
                    }
                } else {
                    // FORMATO VIEJO: 123 ABC
                    if (texto.length < 3) {
                        abrirTecladoNumerico() // Siguen siendo números
                    } else {
                        abrirTecladoTexto()    // Después de 3 números, vienen letras
                    }
                }
            }
        }
    }

    private fun abrirTecladoNumerico() {
        if (binding.etDominio.inputType != android.text.InputType.TYPE_CLASS_NUMBER) {
            binding.etDominio.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
    }

    private fun abrirTecladoTexto() {
        val modoTexto = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        if (binding.etDominio.inputType != modoTexto) {
            binding.etDominio.inputType = modoTexto
        }
    }
}