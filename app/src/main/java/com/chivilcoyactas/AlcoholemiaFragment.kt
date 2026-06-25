package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentAlcoholemiaBinding

class AlcoholemiaFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentAlcoholemiaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlcoholemiaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.actualizarProgreso(5)

        // 1. Carga inicial desde SessionManager si es un acta nueva
        if (actaViewModel.alcoMarca.isEmpty()) {
            val config = SessionManager.obtenerAlcoholimetro(requireContext())
            val marcaConfigurada = config["marca"] ?: ""

            if (marcaConfigurada.isNotEmpty()) {
                // Si hay una marca en SessionManager, cargamos el equipo completo...
                actaViewModel.alcoMarca = marcaConfigurada
                actaViewModel.alcoModelo = config["modelo"] ?: ""
                actaViewModel.alcoSerie = config["serie"] ?: ""

                // ...¡Y activamos el test automáticamente en el ViewModel!
                actaViewModel.hacerTestAlcoholemia = true
            }
            /*actaViewModel.alcoMarca = config["marca"] ?: ""
            actaViewModel.alcoModelo = config["modelo"] ?: ""
            actaViewModel.alcoSerie = config["serie"] ?: ""
            actaViewModel.alcoAprobacion = config["aprobacion"] ?: ""*/
        }

        // 2. Recuperar datos (Debe ir antes de configurar listeners para no dispararlos erróneamente)
        recuperarDatos()

        // 3. Listener para el Test de Alcoholemia
        binding.checkHacerTest.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.hacerTestAlcoholemia = isChecked
            binding.cardTest.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                // Scroll automático para que el inspector vea el formulario que apareció
                binding.scrollAlcoholemia.postDelayed({
                    binding.scrollAlcoholemia.smoothScrollTo(0, binding.cardTest.top)
                }, 100)
            }else{

                // 🧽 Limpieza de datos para evitar basura en el acta
                /*binding.etAlcoMarca.text?.clear()
                binding.etAlcoModelo.text?.clear()
                binding.etAlcoSerie.text?.clear()
                binding.etAlcoAprobacion.text?.clear()
                binding.etResultado.text?.clear()*/

                // 🛠️ NUEVO: Destildamos el check en la pantalla y en el ViewModel
                binding.checkPlanillaMedica.isChecked = false
                actaViewModel.seAdjuntaPlanillaMedica = false

                /*actaViewModel.alcoMarca = ""
                actaViewModel.alcoModelo = ""*/
            }
        }

        // 4. Escuchamos cuando el inspector lo tilda o destilda
        binding.checkPlanillaMedica.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.seAdjuntaPlanillaMedica = isChecked
        }

        // Listeners para Medidas Preventivas (persistencia inmediata)
        binding.checkRetencionVehiculo.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.retencionVehiculo = isChecked
        }
        binding.checkRetencionLicencia.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.retencionLicencia = isChecked
        }
        binding.checkAnimal.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.retencionAnimal = isChecked
        }

        // Botón Volver
        binding.btnVolverAlco.setOnClickListener { findNavController().navigateUp() }

        // Botón Siguiente
        binding.btnSiguienteAlco.setOnClickListener {

            if (binding.checkHacerTest.isChecked && binding.etAlcoMarca.text.toString().isEmpty()) {
                binding.etAlcoMarca.error = "Ingrese la marca"
                Toast.makeText(requireContext(), "La marca es obligatorio si realiza el test", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.checkHacerTest.isChecked && binding.etAlcoModelo.text.toString().isEmpty()) {
                binding.etAlcoModelo.error = "Ingrese el modelo"
                Toast.makeText(requireContext(), "El modelo es obligatorio si realiza el test", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.checkHacerTest.isChecked && binding.etAlcoSerie.text.toString().isEmpty()) {
                binding.etAlcoSerie.error = "Ingrese el NºSerie"
                Toast.makeText(requireContext(), "El NºSerie es obligatorio si realiza el test", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.checkHacerTest.isChecked && binding.etAlcoAprobacion.text.toString().isEmpty()) {
                binding.etAlcoAprobacion.error = "Ingrese el cod.aprobacion"
                Toast.makeText(requireContext(), "El cod.aprobacion es obligatorio si realiza el test", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.checkHacerTest.isChecked && binding.etResultado.text.toString().isEmpty()) {
                binding.etResultado.error = "Ingrese el resultado"
                Toast.makeText(requireContext(), "El resultado es obligatorio si realiza el test", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val textoResultado = binding.etResultado.text.toString().trim()

            // Guardar textos en ViewModel antes de navegar
            actaViewModel.alcoMarca = binding.etAlcoMarca.text.toString()
            actaViewModel.alcoModelo = binding.etAlcoModelo.text.toString()
            actaViewModel.alcoSerie = binding.etAlcoSerie.text.toString()
            actaViewModel.alcoAprobacion = binding.etAlcoAprobacion.text.toString()
            actaViewModel.ftResultado = textoResultado.toDoubleOrNull() ?: 0.0
            //actaViewModel.ftNroPipeta = binding.etNroPipeta.text.toString()

            // Lógica de navegación inteligente
            if (binding.checkRetencionVehiculo.isChecked) {
                // Si retiene vehículo, debe cargar los datos del secuestro (Paso 6)
                findNavController().navigate(R.id.action_alcoholemia_to_secuestro)
            } else {
                // Si NO retiene vehículo, salta directo a Testigos/Firma (Paso 7)
                findNavController().navigate(R.id.action_alcoholemia_to_testigos)
            }
        }

        // Manejo de teclado y focus
        configurarTecladoYFocus()
    }

    private fun recuperarDatos() {
        // Usamos el booleano del ViewModel para el estado del check
        binding.checkHacerTest.isChecked = actaViewModel.hacerTestAlcoholemia
        binding.cardTest.visibility = if (actaViewModel.hacerTestAlcoholemia) View.VISIBLE else View.GONE

        // Cargamos los EditText con lo que haya en el ViewModel (ya sea de sesión o previo)
        binding.etAlcoMarca.setText(actaViewModel.alcoMarca)
        binding.etAlcoModelo.setText(actaViewModel.alcoModelo)
        binding.etAlcoSerie.setText(actaViewModel.alcoSerie)
        binding.etAlcoAprobacion.setText(actaViewModel.alcoAprobacion)
        if (actaViewModel.ftResultado > 0.0) {
            binding.etResultado.setText(actaViewModel.ftResultado.toString())
        } else {
            binding.etResultado.setText("") // Queda el hint "Resultado (grs/l)" limpito
        }


        binding.checkPlanillaMedica.isChecked = actaViewModel.seAdjuntaPlanillaMedica
        //binding.etNroPipeta.setText(actaViewModel.ftNroPipeta)

        // Seteamos los checks de medidas preventivas
        binding.checkRetencionVehiculo.isChecked = actaViewModel.retencionVehiculo
        binding.checkRetencionLicencia.isChecked = actaViewModel.retencionLicencia
        binding.checkAnimal.isChecked = actaViewModel.retencionAnimal
    }

    private fun configurarTecladoYFocus() {
        // Ocultar teclado al presionar "Hecho" en el último campo
        /*binding.etNroPipeta.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                (activity as? MainActivity)?.hideKeyboard()
                binding.etNroPipeta.clearFocus()
                true
            } else false
        }*/

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollAlcoholemia.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        // Auto-Scroll para mejorar visibilidad en la 3nStar
        val views = listOf(binding.etAlcoMarca, binding.etAlcoModelo, binding.etAlcoSerie,
            binding.etAlcoAprobacion, binding.etResultado)
        views.forEach { configurarAutoScroll(it) }
    }

    private fun configurarAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollAlcoholemia.postDelayed({
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