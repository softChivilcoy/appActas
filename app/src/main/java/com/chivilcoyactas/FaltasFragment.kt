package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import com.chivilcoyactas.databinding.FragmentFaltasBinding
import com.google.android.material.internal.ViewUtils.hideKeyboard
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

        // Ajustar el número de paso según el área
        if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
            (activity as? MainActivity)?.actualizarProgreso(4) // Es el paso 4 en Inspección
        } else {
            (activity as? MainActivity)?.actualizarProgreso(2) // Es el paso 2 en Tránsito
        }


        // 1. Conectamos con el Catálogo Real de Room 🚀
        actaViewModel.todasLasFaltas.observe(viewLifecycleOwner) { listaFaltasRoom ->

            val listaMaestra = listaFaltasRoom.map { "${it.codigo} - ${it.descripcion}" }

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                listaMaestra
            )
            binding.autoCompleteFaltas.setAdapter(adapter)

            // Captura del clic
            binding.autoCompleteFaltas.setOnItemClickListener { parent, _, position, _ ->
                val seleccion = parent.getItemAtPosition(position).toString()

                // 🚀 TRUCO: Le pasamos true indicando que viene de un clic forzado, saltando el chequeo del array
                agregarFaltaLogica(seleccion, listaMaestra.toTypedArray(), esClicDirecto = true)
            }

            binding.autoCompleteFaltas.setOnEditorActionListener { _, _, _ ->
                val texto = binding.autoCompleteFaltas.text.toString()
                if (texto.isNotEmpty()) {
                    agregarFaltaLogica(texto, listaMaestra.toTypedArray(), esClicDirecto = false)
                }
                true
            }
        }

        //------------------------------------------------
        // Ocultar teclado al tocar fuera de los campos
        binding.scrollFaltas.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        vincularBotones()
        recuperarDatosDeCaja()

        // --- MEJORA 3: SCROLLS DINÁMICOS (Como los anteriores) ---
        configurarAutoScroll(binding.autoCompleteFaltas)
        configurarAutoScroll(binding.ftObservaciones)
    }

    private fun dibujarFaltaEnPantalla(falta: Infraccion) {
        // Creamos el item visual (tu lógica de antes pero más limpia)
        val layoutFalta = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(30, 20, 30, 20)
            background = resources.getDrawable(android.R.drawable.editbox_dropdown_light_frame)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 15)
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

                val idReal = actaViewModel.todasLasFaltas.value?.find { it.codigo == codigoExtraido }?.id ?: 0
                val nuevaInfraccion = Infraccion(id = idReal, codigo = codigoExtraido, nombre = nombreExtraido)

                actaViewModel.listaFaltasSeleccionadas.add(nuevaInfraccion)
                dibujarFaltaEnPantalla(nuevaInfraccion)

                binding.autoCompleteFaltas.setText("")
            } else {
                binding.autoCompleteFaltas.setText("")
                Toast.makeText(context, "⚠️ Esta falta ya fue agregada al acta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configurarAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollFaltas.postDelayed({
                    // En el 3nStar, mandamos el campo al tope del scroll
                    binding.scrollFaltas.smoothScrollTo(0, v.top)
                }, 300)
            }
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
        //val faltasArray = resources.getStringArray(R.array.lista_infracciones)

        // Botón manual de agregar (por si no quiere usar el Enter)
        /*binding.btnAgregarFalta.setOnClickListener {
            val faltaTexto = binding.autoCompleteFaltas.text.toString()
            agregarFaltaLogica(faltaTexto, faltasArray)
        }*/

        binding.btnVolverFaltas.setOnClickListener {
            if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
                // En inspección, atrás de Faltas está el Infractor
                findNavController().navigate(R.id.action_faltas_to_infractor_VUELTA)
            } else {
                // En tránsito, atrás de Faltas está Ubicación
                findNavController().navigateUp()
            }
        }

        binding.btnSiguienteFaltas.setOnClickListener {

            // Validación: Al menos una falta
            if (actaViewModel.listaFaltasSeleccionadas.isEmpty()) {
                Toast.makeText(context, "Debe agregar al menos una falta", Toast.LENGTH_SHORT).show()
            } else {

                if (binding.ftObservaciones.text.toString().isEmpty()) {
                    binding.ftObservaciones.error = "Campo Obligatorio"
                    Toast.makeText(requireContext(), "Descripcion de la falta", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Guardar datos en el ViewModel
                actaViewModel.ftObservaciones = binding.ftObservaciones.text.toString()


                if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
                    findNavController().navigate(R.id.action_faltas_to_testigos)
                } else {
                    findNavController().navigate(R.id.action_faltas_to_infractor)
                }
            }
        }
    }

    /*private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val currentFocusedView = activity?.currentFocus
        currentFocusedView?.let {
            inputMethodManager.hideSoftInputFromWindow(it.windowToken, android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }*/

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}