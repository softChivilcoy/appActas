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

        /*reparticiones.forEach { opcion ->
            val button = MaterialButton(requireContext()).apply {

                // Texto claro para el inspector: "DIRECCIÓN DE TRÁNSITO - INFRACCIÓN"
                text = if (!opcion.tipoActaNombre.isNullOrBlank()) {
                    "${opcion.nombre} • ${opcion.tipoActaNombre}"
                } else {
                    opcion.nombre
                }

                setOnClickListener {
                    // 1. Guardamos el ID real de la repartición y el ID REAL del tipo de acta (3 o 6)
                    actaViewModel.idReparticionSeleccionada = opcion.id             // ej: 1 (Tránsito)
                    actaViewModel.idTipoActaSeleccionada = opcion.tipoActaId        // 👈 Asigna 3 o 6 (¡asegurarse de no hardcodear 1 aquí!)

                    actaViewModel.reparticionActual = opcion.nombre

                    // 2. Evaluamos el layout a abrir ("TRANSITO" o "INSPECCION")
                    val moduloUI = opcion.formulario?.uppercase() ?: "TRANSITO"

                    if (moduloUI == "TRANSITO") {
                        actaViewModel.tipoActa = TipoActa.TRANSITO
                        findNavController().navigate(R.id.action_seleccion_to_configAlco)
                    } else {
                        actaViewModel.tipoActa = TipoActa.INSPECCION
                        findNavController().navigate(R.id.action_seleccion_to_hojaRuta)
                    }
                }
            }
            container.addView(button)
        }*/

        reparticiones.forEach { opcion ->
            val button = MaterialButton(requireContext()).apply {
                text = opcion.nombre

                setOnClickListener {
                    val tiposActaDisponibles = opcion.tiposActa

                    // 🛑 VALIDACIÓN CLAVE: Si la repartición no tiene tipos de acta asignados, dar aviso y frenar
                    //"${opcion.nombre}"
                    if (tiposActaDisponibles.isNullOrEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "La repartición no tiene tipos de actas asignadas.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    // 1. GUARDAR REPARTICIÓN EN SHAREDPREFERENCES
                    prefs.edit().apply {
                        putInt("REPARTICION_ID", opcion.id)
                        putString("REPARTICION_NOMBRE", opcion.nombre)
                        apply()
                    }

                    actaViewModel.idReparticionSeleccionada = opcion.id
                    actaViewModel.reparticionActual = opcion.nombre
                    actaViewModel.listaTiposActaDisponibles = tiposActaDisponibles

                    // Evaluamos el formulario del primer tipo de acta disponible
                    val primerTipoActa = tiposActaDisponibles.first()
                    val moduloUI = primerTipoActa.formulario?.uppercase() ?: "TRANSITO"

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}