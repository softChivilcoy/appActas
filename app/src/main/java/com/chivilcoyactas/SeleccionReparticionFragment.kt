package com.chivilcoyactas

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentSeleccionReparticionBinding
import com.google.android.material.button.MaterialButton

class SeleccionReparticionFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentSeleccionReparticionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeleccionReparticionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Obtener las SharedPreferences
        val prefs = requireContext().getSharedPreferences("SesionInspector", Context.MODE_PRIVATE)

        // 2. Leer el nombre guardado en el login
        val nombreInspector = prefs.getString("INSPECTOR_NOMBRE", "Inspector")


        // 3. Setear el texto en el TextView
        binding.tvBienvenida.text = "Bienvenido, ${nombreInspector?.lowercase()?.split(" ")?.joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }}"

        val reparticiones = actaViewModel.listaReparticiones
        val container = binding.containerReparticiones
        container.removeAllViews()

        if (reparticiones.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No hay reparticiones asignadas", Toast.LENGTH_LONG).show()
            return
        }


        reparticiones.forEach { opcion ->
            // Creación y formateo de cada botón de repartición con estilo estandarizado
            val button = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonStyle
            ).apply {
                text = opcion.nombre
                textSize = 18f
                isAllCaps = false
                cornerRadius = 12.dpToPx(context)
                
                // Estilo visual verde institucional
                setBackgroundColor(android.graphics.Color.parseColor("#33BB66"))
                setTextColor(android.graphics.Color.WHITE)

                // Alineación: Texto a la izquierda, Flecha a la derecha
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setPadding(24.dpToPx(context), 0, 24.dpToPx(context), 0)

                // Icono de flecha a la derecha alineado al extremo (far right)
                setIconResource(R.drawable.ic_arrow_forward)
                setIconTintResource(android.R.color.white)
                iconGravity = MaterialButton.ICON_GRAVITY_END
                iconSize = 28.dpToPx(context)

                // Parámetros de layout con margen inferior para separar botones
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    80.dpToPx(context) // Altura fija para botones más robustos
                ).apply {
                    bottomMargin = 16.dpToPx(context)
                }
                layoutParams = params

                setOnClickListener {
                    val tiposActaDisponibles = opcion.tiposActa
                    val tiposBienesDisponibles = opcion.tiposBienes ?: emptyList() // 👈 Capturar catálogo de bienes

                    if (tiposActaDisponibles.isNullOrEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "La repartición \"${opcion.nombre}\" no tiene tipos de actas asignadas.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    // 1. Guardar Repartición activa en SharedPreferences
                    prefs.edit().apply {
                        putInt("REPARTICION_ID", opcion.id)
                        putString("REPARTICION_NOMBRE", opcion.nombre)
                        apply()
                    }

                    // 2. Guardar en ViewModel
                    actaViewModel.idReparticionSeleccionada = opcion.id
                    actaViewModel.reparticionActual = opcion.nombre
                    actaViewModel.listaTiposActaDisponibles = tiposActaDisponibles
                    actaViewModel.listaTiposBienesDisponibles = tiposBienesDisponibles

                    // 3. Evaluar el tipo de formulario de la repartición y enrutar según el módulo
                    val moduloUI = opcion.formulario?.uppercase() ?: "TRANSITO"
                    val primerTipoActa = tiposActaDisponibles.first()

                    if (moduloUI == "TRANSITO") {
                        actaViewModel.tipoFormulario = TipoFormulario.TRANSITO
                        actaViewModel.idTipoActaSeleccionada = primerTipoActa.id

                        findNavController().navigate(R.id.action_seleccion_to_configAlco)
                    } else {
                        actaViewModel.tipoFormulario = TipoFormulario.INSPECCION
                        actaViewModel.idTipoActaSeleccionada = primerTipoActa.id

                        findNavController().navigate(R.id.action_seleccion_to_hojaRuta)
                    }
                }
            }

            container.addView(button)
        }
    }

    // Extension function para convertir dp a px fácilmente
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}