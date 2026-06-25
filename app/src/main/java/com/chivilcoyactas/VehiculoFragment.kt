package com.chivilcoyactas

import android.os.Bundle
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Actualizar barra al Paso 4
        (activity as? MainActivity)?.actualizarProgreso(4)

        // 2. Configurar el selector de Tipo de Vehículo
        /*val tiposVehiculo = arrayOf("AUTOMOVIL", "CAMION", "CAMIONETA", "OMNIBUS", "MOTOCICLETA", "CICLOMOTOR", "CUATRICICLO", "OTROS")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            tiposVehiculo
        )
        binding.autoCompleteTipoVehiculo.setAdapter(adapter)
        if (actaViewModel.tipoVehiculo.isEmpty()) {
            actaViewModel.tipoVehiculo = "AUTOMOVIL"
        }

       // Cargamos el dato guardado sin filtrar
       if (actaViewModel.tipoVehiculo.isNotEmpty()) {
           binding.autoCompleteTipoVehiculo.setText(actaViewModel.tipoVehiculo, false)
       }

       // Escuchamos el click en el CONTENEDOR (el Layout), no en el texto
       binding.inputLayoutTipoVehiculo.setOnClickListener {
           (binding.autoCompleteTipoVehiculo.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
           binding.autoCompleteTipoVehiculo.showDropDown()
       }

       // También forzamos que si tocan el AutoComplete, se lo pase al padre
       binding.autoCompleteTipoVehiculo.setOnClickListener {
           binding.inputLayoutTipoVehiculo.performClick()
       }

        binding.autoCompleteTipoVehiculo.setOnItemClickListener { parent, _, position, _ ->
            val seleccion = parent.getItemAtPosition(position).toString()
            actaViewModel.tipoVehiculo = seleccion
            // Limpiamos el dominio si cambian de tipo para evitar errores de formato
            binding.etDominio.text?.clear()
        }*/

        // ==========================================
        // 🚀 CONTROL DE COMBOS DINÁMICOS DESDE ROOM
        // ==========================================

        // 1. CARGA DE TIPOS DE VEHÍCULO
        actaViewModel.todosLosTiposVehiculo.observe(viewLifecycleOwner) { listaTipos ->
            val nombresTipos = listaTipos.map { it.nombre }
            val adapterTipo = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresTipos)
            binding.autoCompleteTipoVehiculo.setAdapter(adapterTipo)

            // Si el ViewModel ya tenía un ID o un nombre, lo seleccionamos
            if (actaViewModel.tipoVehiculo.isNotEmpty()) {
                binding.autoCompleteTipoVehiculo.setText(actaViewModel.tipoVehiculo, false)
            }
        }

        binding.autoCompleteTipoVehiculo.setOnItemClickListener { parent, _, position, _ ->
            val seleccion = parent.getItemAtPosition(position).toString()
            actaViewModel.tipoVehiculo = seleccion

            // Guardamos el ID real de Postgres
            val objetoTipo = actaViewModel.todosLosTiposVehiculo.value?.find { it.nombre == seleccion }
            actaViewModel.idTipoVehiculoSeleccionado = objetoTipo?.id ?: 0

            binding.etDominio.text?.clear()
        }

        // 2. CARGA DE MARCAS
        actaViewModel.todasLasMarcas.observe(viewLifecycleOwner) { listaMarcas ->
            val nombresMarcas = listaMarcas.map { it.nombre }
            val adapterMarca = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresMarcas)
            binding.autoCompleteMarca.setAdapter(adapterMarca)

            if (actaViewModel.marca.isNotEmpty()) {
                binding.autoCompleteMarca.setText(actaViewModel.marca, false)
            }
        }

        binding.autoCompleteMarca.setOnItemClickListener { parent, _, position, _ ->
            val marcaSeleccionada = parent.getItemAtPosition(position).toString()
            actaViewModel.marca = marcaSeleccionada

            // Buscamos el ID de la marca elegida
            val objetoMarca = actaViewModel.todasLasMarcas.value?.find { it.nombre == marcaSeleccionada }
            val marcaId = objetoMarca?.id ?: 0
            actaViewModel.idMarcaSeleccionada = marcaId

            // Limpiamos el combo de modelos porque cambió la marca
            binding.autoCompleteModelo.text.clear()
            actaViewModel.modelo = ""
            actaViewModel.idModeloSeleccionado = 0

            // 🔄 Disparamos la búsqueda de modelos de esta marca en Room de forma asíncrona
            lifecycleScope.launch {
                val modelosFiltrados = AppDatabase.getDatabase(requireContext()).catalogoDao().obtenerModelosPorMarca(marcaId)
                val nombresModelos = modelosFiltrados.map { it.nombre }
                val adapterModelo = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresModelos)
                binding.autoCompleteModelo.setAdapter(adapterModelo)
            }
        }

        // 3. SELECCIÓN DE MODELO
        binding.autoCompleteModelo.setOnItemClickListener { parent, _, position, _ ->
            val modeloSeleccionado = parent.getItemAtPosition(position).toString()
            actaViewModel.modelo = modeloSeleccionado

            // Para obtener el ID del modelo podés consultar rápido la base o guardarlo en memoria
            // En el ejecutarSiguiente() nos aseguramos de persistirlo todo.
        }

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollVehiculo.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

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

        /*

       binding.autoCompleteTipoVehiculo.apply {
           // 1. Cargamos el dato si existe
           if (actaViewModel.tipoVehiculo.isNotEmpty()) {
               setText(actaViewModel.tipoVehiculo, false)
           }

           // 2. Quitamos el umbral para que siempre tenga data lista
           threshold = 100

           // 3. Este es el listener que arregla el parpadeo
           setOnClickListener {
               // Limpiamos el filtro antes de mostrar
               (adapter as? ArrayAdapter<*>)?.filter?.filter(null)
               showDropDown()
           }
       }*/

        // 3. Cargar datos si el inspector volvió atrás
        // Usamos el valor del ViewModel o "AUTOMOVIL" por defecto
        /*val tipoGuardado = actaViewModel.tipoVehiculo
        if (tipoGuardado.isNotEmpty()) {
            binding.autoCompleteTipoVehiculo.setText(tipoGuardado, false) // El 'false' es CLAVE
        }*/

        // LA SOLUCIÓN DEFINITIVA:
        // Usamos el OnTouchListener para limpiar el filtro ANTES de mostrar el DropDown
       /* binding.autoCompleteTipoVehiculo.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                // 1. Limpiamos cualquier texto para que el filtro sea nulo
                binding.autoCompleteTipoVehiculo.text = null
                // 2. Mostramos todas las opciones
                binding.autoCompleteTipoVehiculo.showDropDown()
                // 3. Restauramos el texto original (opcional, para que no quede vacío si no elige nada)
                if (tipoGuardado.isNotEmpty()) {
                    binding.autoCompleteTipoVehiculo.postDelayed({
                        if (binding.autoCompleteTipoVehiculo.text.isNullOrEmpty()) {
                            binding.autoCompleteTipoVehiculo.setText(tipoGuardado, false)
                        }
                    }, 100)
                }
            }
            false
        }*/


        // Opcionalmente, forzar que el adaptador no filtre:
        //(binding.autoCompleteTipoVehiculo.adapter as? ArrayAdapter<*>)?.filter?.filter(null)

        /*binding.autoCompleteTipoVehiculo.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                binding.autoCompleteTipoVehiculo.showDropDown()
            }
            false
        }*/

        // 1. Definimos qué pasa cuando cambia el estado
        binding.checkEsPropietario.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.esPropietario = isChecked

            if (isChecked) {
                // Si es el propietario, ocultamos el formulario extra
                binding.layoutDatosPropietario.visibility = View.GONE
            } else {
                // Si NO es el propietario, mostramos los campos para completar
                binding.layoutDatosPropietario.visibility = View.VISIBLE

                // Opcional: Hacer scroll hacia abajo para que el inspector vea que aparecieron campos
                binding.etNombrePropietario.requestFocus()
            }
        }


        // cargamos el valor del ViewModel
        // Al hacer esto, se dispara automáticamente el Listener de arriba
        // y el layout se muestra u oculta solo.
        binding.etDominio.setText(actaViewModel.dominio)
        binding.autoCompleteMarca.setText(actaViewModel.marca, false)  // El 'false' es clave para que no filtre al cargar
        binding.autoCompleteModelo.setText(actaViewModel.modelo, false) // El 'false' es clave para que no filtre al cargar
        binding.checkEsPropietario.isChecked = actaViewModel.esPropietario
        //binding.etProcedimiento.setText(actaViewModel.procedimientoVeh)

        // 4. Listeners para el teclado y botones
       /* binding.etProcedimiento.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                ejecutarSiguiente()
                true
            } else {
                false
            }
        }*/

        binding.btnSiguienteVehiculo.setOnClickListener {
            ejecutarSiguiente()
        }

        binding.btnVolverVehiculo.setOnClickListener {
            findNavController().navigateUp()
        }

        // 5. Aplicar auto-scroll a todos los campos, incluido el nuevo selector
        //configurarAutoScroll(binding.autoCompleteTipoVehiculo)
        configurarAutoScroll(binding.etDominio)
        configurarAutoScroll(binding.autoCompleteMarca)
        configurarAutoScroll(binding.autoCompleteModelo)
        //configurarAutoScroll(binding.etProcedimiento)
    }

    private fun ejecutarSiguiente() {
        // GUARDAR TODO EN EL VIEWMODEL

        if (!actaViewModel.esPropietario) {
            actaViewModel.nombreResponsable = binding.etNombrePropietario.text.toString().uppercase()
            actaViewModel.dniResponsable = binding.etDniPropietario.text.toString()
        } else {
            actaViewModel.nombreResponsable = actaViewModel.ApellidoNombreInfractor
            actaViewModel.dniResponsable = actaViewModel.dniInfractor
        }

        // Leemos de los AutoCompleteTextViews nuevos
        actaViewModel.tipoVehiculo = binding.autoCompleteTipoVehiculo.text.toString()
        actaViewModel.marca = binding.autoCompleteMarca.text.toString()
        actaViewModel.modelo = binding.autoCompleteModelo.text.toString()
        actaViewModel.dominio = binding.etDominio.text.toString().uppercase()
        actaViewModel.esPropietario = binding.checkEsPropietario.isChecked

        // Navegar al paso 5 (Alcoholemia)
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