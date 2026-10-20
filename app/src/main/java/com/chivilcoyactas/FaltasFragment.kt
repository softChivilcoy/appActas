package com.chivilcoyactas

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.chivilcoyactas.databinding.FragmentFaltasBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.getValue

class FaltasFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    // 2. Definimos el binding
    private var _binding: FragmentFaltasBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFaltasBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Reinicio de scroll al entrar
        binding.scrollFaltas.post {
            binding.scrollFaltas.scrollTo(0, 0)
        }

        // Ajustar el número de paso según el área
        if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
            (activity as? MainActivity)?.actualizarProgreso(4) // Es el paso 4 en Inspección
        } else {
            (activity as? MainActivity)?.actualizarProgreso(2) // Es el paso 2 en Tránsito
        }


        // 1. Conectamos con el Catálogo Real de Room 🚀
        actaViewModel.todasLasFaltas.observe(viewLifecycleOwner) { listaFaltasRoom ->

            val listaMaestra = listaFaltasRoom.map { "${it.codigo} - ${it.descripcionCorta}" }

            // 🚀 ADAPTER PERSONALIZADO CON FILTRO "CONTAINS" CORREGIDO
            val adapter = object : ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                listaMaestra.toMutableList()
            ) {
                private val listaOriginal = ArrayList(listaMaestra)

                override fun getFilter(): android.widget.Filter {
                    return object : android.widget.Filter() {
                        override fun performFiltering(constraint: CharSequence?): FilterResults {
                            val results = FilterResults()

                            // 🚀 Verificación de nulo o vacío corregida en Kotlin
                            if (constraint.isNullOrEmpty()) {
                                results.values = listaOriginal
                                results.count = listaOriginal.size
                            } else {
                                val query = constraint.toString().trim().lowercase()
                                val listaFiltrada = listaOriginal.filter { item ->
                                    item.lowercase().contains(query) // Busca en números y texto por igual
                                }
                                results.values = listaFiltrada
                                results.count = listaFiltrada.size
                            }
                            return results
                        }

                        @Suppress("UNCHECKED_CAST")
                        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                            clear()
                            if (results != null && results.count > 0) {
                                addAll(results.values as List<String>)
                            }
                            notifyDataSetChanged()
                        }
                    }
                }
            }

            binding.autoCompleteFaltas.setAdapter(adapter)

            // Captura del clic
            binding.autoCompleteFaltas.setOnItemClickListener { parent, _, position, _ ->
                val seleccion = parent.getItemAtPosition(position).toString()
                agregarFaltaLogica(seleccion, listaMaestra.toTypedArray(), esClicDirecto = true)
            }

            binding.autoCompleteFaltas.setOnEditorActionListener { _, actionId, event ->
                val esEnter = actionId == EditorInfo.IME_ACTION_NEXT ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)

                if (esEnter) {
                    val texto = binding.autoCompleteFaltas.text.toString()
                    if (texto.isNotEmpty()) {
                        agregarFaltaLogica(texto, listaMaestra.toTypedArray(), esClicDirecto = false)
                    }

                    // 🚀 Pasa el foco al campo de observaciones
                    binding.ftObservaciones.requestFocus()
                    binding.ftObservaciones.text?.let { editable ->
                        binding.ftObservaciones.setSelection(editable.length)
                    }
                    true
                } else {
                    false
                }
            }
        }


        binding.textInputLayoutObservaciones.setEndIconOnClickListener {
            // Limpiamos saltos de línea al final
            val textoLimpio = binding.ftObservaciones.text.toString().trimEnd()
            binding.ftObservaciones.setText(textoLimpio)

            // Quitamos el foco y ocultamos teclado
            binding.ftObservaciones.clearFocus()
            binding.contenedorPrincipal.requestFocus()
            (activity as? MainActivity)?.hideKeyboard()

            // Un solo scroll suave hacia arriba sin competir con lostFocus
            binding.scrollFaltas.post {
                binding.scrollFaltas.smoothScrollTo(0, 0)
            }
        }

        // --- MEJORA 3: SCROLLS DINÁMICOS ---
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollFaltas) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val isVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val density = resources.displayMetrics.density
            
            // Si el teclado está visible, agregamos un padding extra al final del scroll
            // para que los campos inferiores puedan subir por encima del teclado.
            // Dado que MainActivity usa adjustResize, el ScrollView ya se achica,
            // por lo que sumamos un margen de seguridad (100dp) en lugar de toda la altura.
            val paddingBase = (20 * density).toInt()
            val paddingExtra = if (isVisible) (120 * density).toInt() else paddingBase
            
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, paddingExtra)
            insets
        }

        binding.autoCompleteFaltas.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                (v as? AutoCompleteTextView)?.showDropDown()
                ejecutarAutoScroll(binding.textInputLayoutFaltas)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }

        binding.ftObservaciones.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                ejecutarAutoScroll(binding.textInputLayoutObservaciones)
                v.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                v.setBackgroundResource(0)
            }
        }

        // 3. Ocultar teclado al tocar fuera
        binding.scrollFaltas.setOnTouchListener { _, _ ->
            if (binding.ftObservaciones.hasFocus() || binding.autoCompleteFaltas.hasFocus()) {
                binding.ftObservaciones.clearFocus()
                binding.autoCompleteFaltas.clearFocus()
                binding.contenedorPrincipal.requestFocus()
                (activity as? MainActivity)?.hideKeyboard()
            }
            false
        }

        vincularBotones()
        recuperarDatosDeCaja()
    }

    private fun ejecutarAutoScroll(view: View) {
        // Usamos post para esperar a que el teclado y el layout se estabilicen
        binding.scrollFaltas.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            val rect = android.graphics.Rect()
            view.getDrawingRect(rect)
            binding.scrollFaltas.offsetDescendantRectToMyCoords(view, rect)

            val density = resources.displayMetrics.density
            val offsetTop = (90 * density).toInt()

            val targetY = rect.top - offsetTop
            binding.scrollFaltas.smoothScrollTo(0, targetY.coerceAtLeast(0))
        }, 500)
    }

    private fun dibujarFaltaEnPantalla(falta: Infraccion) {
        val density = resources.displayMetrics.density
        
        // Creamos el item visual con medidas en DP
        val layoutFalta = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = ContextCompat.getDrawable(requireContext(), android.R.drawable.editbox_dropdown_light_frame)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (8 * density).toInt())
            }
            layoutParams = params
        }

        val textoAMostrar = "${falta.codigo} - ${falta.nombre}"
        val tvTexto = TextView(requireContext()).apply {
            text = textoAMostrar
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(android.graphics.Color.BLACK)

            // --- AQUÍ EL CAMBIO PARA RECORTAR EL TEXTO ---
            setSingleLine(true)              // Fuerza a que sea una sola línea
            ellipsize = android.text.TextUtils.TruncateAt.END // Pone los "..." al final
            isFocusable = true               // Opcional: para que si es muy largo se pueda ver
        }

        val btnEliminar = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_delete)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener {
                binding.contenedorListaFaltas.removeView(layoutFalta)

                // Eliminamos el objeto de la lista comparando por nombre
                actaViewModel.listaFaltasSeleccionadas.removeAll { it.nombre == falta.nombre }

                if (actaViewModel.listaFaltasSeleccionadas.isEmpty()) {
                    binding.tvSinFaltas.visibility = View.VISIBLE
                    // Si borra todas las faltas, limpiamos las observaciones sugeridas
                    binding.ftObservaciones.setText("")
                    actaViewModel.ftObservaciones = ""
                }
            }
        }

        layoutFalta.addView(tvTexto)
        layoutFalta.addView(btnEliminar)
        binding.contenedorListaFaltas.addView(layoutFalta)
    }

    private fun agregarFaltaLogica(faltaTexto: String, faltasArray: Array<String>, esClicDirecto: Boolean = false) {
        val textoLimpio = faltaTexto.trim()

        // Si es clic directo, sabemos que existe. Si escribió y tocó "Enter", validamos contra el array.
        val esValido = esClicDirecto || faltasArray.any { it.equals(textoLimpio, ignoreCase = true) }

        if (textoLimpio.isNotEmpty() && esValido) {
            val partes = textoLimpio.split(" - ")
            val codigoExtraido = if (partes.size > 1) partes[0] else "S/C"
            val nombreExtraido = if (partes.size > 1) partes[1] else textoLimpio

            val yaExiste = actaViewModel.listaFaltasSeleccionadas.any { it.nombre == nombreExtraido }

            if (!yaExiste) {
                binding.tvSinFaltas.visibility = View.GONE
                binding.autoCompleteFaltas.error = null

                // 1. Buscamos el objeto completo desde la lista cargada de Room
                val objetoFaltaRoom = actaViewModel.todasLasFaltas.value?.find { it.codigo == codigoExtraido }
                val idReal = objetoFaltaRoom?.id ?: 0

                val nuevaInfraccion = Infraccion(id = idReal, codigo = codigoExtraido, nombre = nombreExtraido)

                actaViewModel.listaFaltasSeleccionadas.add(nuevaInfraccion)
                dibujarFaltaEnPantalla(nuevaInfraccion)

                // 2. 🚀 INSERCIÓN AUTOMÁTICA DE PLANTILLA EN OBSERVACIONES
                objetoFaltaRoom?.descripcionPlantilla?.let { plantilla ->
                    if (plantilla.isNotBlank()) {
                        val textoActual = binding.ftObservaciones.text?.toString()?.trim() ?: ""

                        val nuevoTexto = if (textoActual.isEmpty()) {
                            plantilla
                        } else {
                            "$textoActual\n$plantilla"
                        }

                        // Actualizamos tanto la UI como el ViewModel
                        binding.ftObservaciones.setText(nuevoTexto)
                        actaViewModel.ftObservaciones = nuevoTexto

                        // Posicionamos el cursor al final del texto cargado
                        binding.ftObservaciones.setSelection(nuevoTexto.length)
                    }
                }

                binding.autoCompleteFaltas.setText("")
            } else {
                binding.autoCompleteFaltas.setText("")
                Toast.makeText(context, "⚠️ Esta falta ya fue agregada al acta", Toast.LENGTH_SHORT).show()
            }
        } else if (textoLimpio.isNotEmpty() && !esValido) {
            binding.autoCompleteFaltas.setText("")
            binding.autoCompleteFaltas.error = "Seleccione una falta de la lista oficial"
            Toast.makeText(context, "⚠️ La falta ingresada no es oficial", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recuperarDatosDeCaja() {
        if (actaViewModel.listaFaltasSeleccionadas.isNotEmpty()) {
            binding.tvSinFaltas.visibility = View.GONE
            actaViewModel.listaFaltasSeleccionadas.forEach { objetoInfraccion ->
                dibujarFaltaEnPantalla(objetoInfraccion)
            }
        }
       binding.ftObservaciones.setText(actaViewModel.ftObservaciones)
    }

    private fun vincularBotones() {
        binding.btnVolverFaltas.setOnClickListener {
            if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                findNavController().navigate(R.id.action_faltas_to_infractor_VUELTA)
            } else {
                findNavController().navigate(R.id.action_faltas_to_ubicacion)
            }
        }

        binding.btnSiguienteFaltas.setOnClickListener {
            if (actaViewModel.listaFaltasSeleccionadas.isEmpty()) {
                Toast.makeText(context, "Debe agregar al menos una falta", Toast.LENGTH_SHORT).show()
            } else {
                if (binding.ftObservaciones.text.toString().isEmpty()) {
                    binding.ftObservaciones.error = "Campo Obligatorio"
                    Toast.makeText(requireContext(), "Descripcion de la falta", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                actaViewModel.ftObservaciones = binding.ftObservaciones.text.toString()

                if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                    findNavController().navigate(R.id.action_faltas_to_testigos)
                } else {
                    findNavController().navigate(R.id.action_faltas_to_infractor)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}