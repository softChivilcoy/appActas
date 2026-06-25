package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentSeleccionReparticionBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlin.getValue

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

        // Simulamos las reparticiones que vienen del login/ViewModel
        val reparticiones = listOf("TRANSITO", "INSPECCION GENERAL")

        val container = binding.containerReparticiones

        reparticiones.forEach { nombre ->
            val button = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonStyle
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }

                text = nombre
                textSize = 18f
                setPadding(16, 40, 16, 40)
                cornerRadius = 12

                setOnClickListener {
                    // 1. Guardamos la elección
                    actaViewModel.reparticionActual = nombre

                    // 2. Decidimos a dónde ir según la repartición
                    if (nombre == "TRANSITO") {
                        actaViewModel.tipoActa = TipoActa.TRANSITO
                        //findNavController().navigate(R.id.action_login_to_seleccionReparticion)
                        // Nota: Asegurate que el ID coincida con el Action del nav_graph
                        // Si seguiste mi consejo anterior, los IDs son estos:
                        findNavController().navigate(R.id.action_seleccion_to_configAlco)
                    } else {
                        actaViewModel.tipoActa = TipoActa.INSPECCION
                        findNavController().navigate(R.id.action_seleccion_to_hojaRuta)
                    }
                }
            }
            container.addView(button)
        }
    }
}