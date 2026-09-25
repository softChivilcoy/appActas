package com.chivilcoyactas

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentSecuestroBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecuestroFragment : Fragment() {

    private var _binding: FragmentSecuestroBinding? = null
    private val binding get() = _binding!!
    private val actaViewModel: ActaViewModel by activityViewModels()

    // Mapa para rastrear las Views dinámicas vinculadas a su codigo_clave
    private val mapaViewsCampos = mutableMapOf<String, View>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecuestroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.actualizarProgreso(6)

        binding.tvTituloSecuestro.text = "📋 ACTA DE SECUESTRO: INVENTARIO"

        // Restablecer campos estáticos


        // Toggle para la sección de Interior (si aplica)
        binding.checkIncluirInterior.setOnCheckedChangeListener { _, isChecked ->
            binding.cardInterior.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Cargar el inventario desde Room
        cargarChecklistDinamico()

        binding.btnSiguienteSecuestro.setOnClickListener {
            guardarInventarioDinamico()
            findNavController().navigate(R.id.action_secuestro_to_testigos)
        }

        binding.btnVolverSecuestro.setOnClickListener {
            guardarInventarioDinamico()
            findNavController().navigateUp()
        }

        binding.scrollSecuestro.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    private fun cargarChecklistDinamico() {
        val tipoVehiculoId = actaViewModel.idTipoVehiculoSeleccionado ?: 0

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            var itemsChecklist = db.catalogoDao().obtenerChecklistPorTipoVehiculo(tipoVehiculoId)

            // FALLBACK DE SEGURIDAD:
            if (itemsChecklist.isEmpty()) {
                itemsChecklist = db.catalogoDao().obtenerTodosLosChecklist()
            }

            withContext(Dispatchers.Main) {
                renderizarInventario(itemsChecklist)
            }
        }
    }

    private fun renderizarInventario(items: List<CheckVehicularEntity>) {
        mapaViewsCampos.clear()

        binding.containerMotor.removeAllViews()
        binding.containerExterior.removeAllViews()
        binding.containerInterior.removeAllViews()

        // Agrupamos por sector ordenado por orden_ui
        val agrupadosPorSector = items
            .sortedBy { it.ordenUi }
            .groupBy { it.sectorVehiculo?.uppercase() ?: "EXTERIOR" }

        // 1. MOTOR / BAÚL MOTOR
        val itemsMotor = (agrupadosPorSector["BAUL_MOTOR"] ?: emptyList()) + (agrupadosPorSector["MOTOR"] ?: emptyList())
        if (itemsMotor.isNotEmpty()) {
            binding.cardMotor.visibility = View.VISIBLE
            itemsMotor.forEach { item ->
                val viewCampo = crearControlDinamico(item)
                binding.containerMotor.addView(viewCampo)
                mapaViewsCampos[item.codigoClave] = viewCampo
            }
        } else {
            binding.cardMotor.visibility = View.GONE
        }

        // 2. EXTERIOR
        val itemsExterior = agrupadosPorSector["EXTERIOR"] ?: emptyList()
        if (itemsExterior.isNotEmpty()) {
            binding.cardExterior.visibility = View.VISIBLE
            itemsExterior.forEach { item ->
                val viewCampo = crearControlDinamico(item)
                binding.containerExterior.addView(viewCampo)
                mapaViewsCampos[item.codigoClave] = viewCampo
            }
        } else {
            binding.cardExterior.visibility = View.GONE
        }

        // 3. INTERIOR (Automóviles u otros con habitáculo)
        val itemsInterior = agrupadosPorSector["INTERIOR"] ?: emptyList()
        if (itemsInterior.isNotEmpty()) {
            binding.checkIncluirInterior.visibility = View.VISIBLE
            binding.cardInterior.visibility = if (binding.checkIncluirInterior.isChecked) View.VISIBLE else View.GONE

            itemsInterior.forEach { item ->
                val viewCampo = crearControlDinamico(item)
                binding.containerInterior.addView(viewCampo)
                mapaViewsCampos[item.codigoClave] = viewCampo
            }
        } else {
            // Si es Moto o no tiene ítems de interior, se oculta completamente esta sección
            binding.checkIncluirInterior.visibility = View.GONE
            binding.cardInterior.visibility = View.GONE
        }
    }

    private fun crearControlDinamico(item: CheckVehicularEntity): View {
        val valorPrevio = actaViewModel.inventarioDinamico[item.codigoClave]

        return when (item.tipoDato.uppercase()) {

            "LISTA", "OPCIONES" -> {
                val itemLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 16)
                    }
                }

                val tvTitulo = TextView(requireContext()).apply {
                    text = item.etiquetaVisible
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                    setPadding(0, 4, 0, 8)
                }
                itemLayout.addView(tvTitulo)

                val radioGroup = RadioGroup(requireContext()).apply {
                    orientation = RadioGroup.HORIZONTAL
                    weightSum = 3f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Limpieza de cadena de opciones enviada por la BD
                val opcionesLimpia = item.opcionesLista
                    ?.replace("[", "")
                    ?.replace("]", "")
                    ?.replace("\"", "")
                    ?.trim()

                val opciones = if (!opcionesLimpia.isNullOrEmpty()) {
                    opcionesLimpia.split(",").map { it.trim() }
                } else {
                    listOf("Bueno", "Malo", "Sin datos")
                }

                opciones.forEachIndexed { index, opcion ->
                    val radioButton = RadioButton(requireContext()).apply {
                        id = View.generateViewId()
                        text = opcion
                        textSize = 13f
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = RadioGroup.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )

                        // Mantenimiento de estado previo
                        if (valorPrevio != null) {
                            if (valorPrevio.equals(opcion, ignoreCase = true)) {
                                isChecked = true
                            }
                        } else if (index == 0) { // Por defecto el primero (ej: Bueno)
                            isChecked = true
                        }
                    }
                    radioGroup.addView(radioButton)
                }

                itemLayout.addView(radioGroup)

                // Separador tenue
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                    ).apply { setMargins(0, 8, 0, 0) }
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    alpha = 0.3f
                }
                itemLayout.addView(divider)

                itemLayout
            }

            "BOOLEAN" -> {
                SwitchMaterial(requireContext()).apply {
                    text = item.etiquetaVisible
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 8) }
                    isChecked = valorPrevio == "S" || valorPrevio == "true"
                }
            }

            "INTEGER", "NUMBER" -> {
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 8) }

                    val tvLabel = TextView(requireContext()).apply {
                        text = item.etiquetaVisible
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val etInput = EditText(requireContext()).apply {
                        hint = "0"
                        inputType = InputType.TYPE_CLASS_NUMBER
                        setText(valorPrevio ?: "")
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }

                    addView(tvLabel)
                    addView(etInput)
                }
            }

            else -> { // TEXT LIBRE
                EditText(requireContext()).apply {
                    hint = item.etiquetaVisible
                    textSize = 14f
                    setText(valorPrevio ?: "")
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 8) }
                }
            }
        }
    }

    private fun guardarInventarioDinamico() {
        actaViewModel.inventarioDinamico.clear()

        mapaViewsCampos.forEach { (codigoClave, view) ->
            val valor: String = when (view) {
                is LinearLayout -> {
                    val radioGroup = view.children.filterIsInstance<RadioGroup>().firstOrNull()
                    val editText = view.children.filterIsInstance<EditText>().firstOrNull()

                    if (radioGroup != null) {
                        val selectedId = radioGroup.checkedRadioButtonId
                        if (selectedId != -1) {
                            radioGroup.findViewById<RadioButton>(selectedId)?.text?.toString() ?: ""
                        } else ""
                    } else if (editText != null) {
                        editText.text.toString().trim()
                    } else ""
                }
                is SwitchMaterial -> if (view.isChecked) "S" else "N"
                is EditText -> view.text.toString().trim()
                else -> ""
            }

            if (valor.isNotEmpty()) {
                actaViewModel.inventarioDinamico[codigoClave] = valor
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}