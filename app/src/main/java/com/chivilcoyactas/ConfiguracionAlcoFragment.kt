package com.chivilcoyactas

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentConfiguracionAlcoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfiguracionAlcoFragment : Fragment(R.layout.fragment_configuracion_alco) {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentConfiguracionAlcoBinding? = null
    private val binding get() = _binding!!

    private var listaEquipos: List<AlcoholimetrosEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfiguracionAlcoBinding.inflate(inflater, container, false)

        // Ocultar barra de progreso global
        (activity as? MainActivity)?.setProgressBarVisibility(false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        SessionManager.limpiaFirma(requireContext())

        // 1. Configurar Visibilidad y Animación del Card con el Switch
        binding.switchHabilitarAlco.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvEstadoAlco.text = "Sí"
                binding.tvEstadoAlco.setTextColor(Color.parseColor("#33BB66"))
                binding.cardDatosEquipo.visibility = View.VISIBLE
                binding.cardDatosEquipo.alpha = 0f
                binding.cardDatosEquipo.animate().alpha(1f).setDuration(300).start()
            } else {
                binding.tvEstadoAlco.text = "No"
                binding.tvEstadoAlco.setTextColor(Color.parseColor("#777777"))
                binding.cardDatosEquipo.visibility = View.GONE
                limpiarCampos()
            }
        }

        // 2. Cargar el listado de alcoholímetros desde Room
        cargarListaAlcoholimetros()

        // 3. Listener de selección del desplegable
        binding.etConfigMarca.setOnItemClickListener { _, _, position, _ ->
            if (position in listaEquipos.indices) {
                val equipoSeleccionado = listaEquipos[position]
                completarCamposEquipo(equipoSeleccionado)
            }
        }

        // 4. Confirmar y guardar configuración de turno
        binding.btnGuardarConfig.setOnClickListener {
            if (binding.switchHabilitarAlco.isChecked) {
                val marca = binding.etConfigMarca.text.toString().trim()
                val modelo = binding.etConfigModelo.text.toString().trim()
                val serie = binding.etConfigSerie.text.toString().trim()
                val homologacion = binding.etConfigAprobacion.text.toString().trim()

                if (marca.isEmpty() || modelo.isEmpty() || serie.isEmpty()) {
                    Toast.makeText(requireContext(), "Debe seleccionar un equipo válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                SessionManager.guardarAlcoholimetro(
                    requireContext(),
                    marca,
                    modelo,
                    serie,
                    homologacion
                )
            } else {
                SessionManager.limpiarDatos(requireContext())
            }

            actaViewModel.resetearActa()
            findNavController().navigate(R.id.action_configAlco_to_step1)
        }

        // Ocultar teclado al tocar fuera de los campos de texto
        binding.scrollConfiguracion.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    private fun cargarListaAlcoholimetros() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val equiposDb = db.catalogoDao().obtenerAlcoholimetros()

            withContext(Dispatchers.Main) {
                listaEquipos = equiposDb

                if (listaEquipos.isNotEmpty()) {
                    val nombresEquipos = listaEquipos.map { "${it.marca ?: "Sin Marca"} (Serie: ${it.nroSerie ?: "S/C"})" }

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        nombresEquipos
                    )
                    binding.etConfigMarca.setAdapter(adapter)

                    // Verificar si ya existía un equipo activo en SessionManager
                    restaurarEstadoPrevio()
                } else {
                    Toast.makeText(requireContext(), "No hay alcoholímetros cargados en el sistema", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun restaurarEstadoPrevio() {
        val equipoGuardado = SessionManager.obtenerAlcoholimetro(requireContext())
        val serie = equipoGuardado["serie"]

        if (!serie.isNullOrEmpty()) {
            binding.switchHabilitarAlco.isChecked = true

            val marca = equipoGuardado["marca"] ?: ""
            val modelo = equipoGuardado["modelo"] ?: ""
            val codHomologacion = equipoGuardado["codHomologacion"] ?: ""

            binding.etConfigMarca.setText("$marca (Serie: $serie)", false)
            binding.etConfigModelo.setText(modelo)
            binding.etConfigSerie.setText(serie)
            binding.etConfigAprobacion.setText(codHomologacion)
        }
    }

    private fun completarCamposEquipo(equipo: AlcoholimetrosEntity) {
        binding.etConfigMarca.setText("${equipo.marca ?: ""} (Serie: ${equipo.nroSerie ?: "S/C"})", false)
        binding.etConfigModelo.setText(equipo.modelo ?: "")
        binding.etConfigSerie.setText(equipo.nroSerie ?: "")
        binding.etConfigAprobacion.setText(equipo.codHomologacion ?: "")
    }

    private fun limpiarCampos() {
        binding.etConfigMarca.setText("", false)
        binding.etConfigModelo.setText("")
        binding.etConfigSerie.setText("")
        binding.etConfigAprobacion.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}