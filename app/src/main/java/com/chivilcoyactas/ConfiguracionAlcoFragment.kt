package com.chivilcoyactas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentConfiguracionAlcoBinding
import com.chivilcoyactas.databinding.FragmentExitoBinding
import kotlin.getValue
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfiguracionAlcoFragment : Fragment(R.layout.fragment_configuracion_alco) {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentConfiguracionAlcoBinding? = null
    private val binding get() = _binding!!

    // Variables para el motor de dibujo manual
    private lateinit var bitmapFirma: Bitmap
    private lateinit var canvasFirma: Canvas
    private val paintFirma = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val pathFirma = Path()
    private var haFirmado = false // Flag para obligar a que dibuje

    private var listaEquipos: List<AlcoholimetrosEntity> = emptyList()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfiguracionAlcoBinding.inflate(inflater, container, false)

        // Ocultar progreso
        (activity as? MainActivity)?.findViewById<View>(R.id.progressBar)?.visibility = View.GONE
        (activity as? MainActivity)?.findViewById<View>(R.id.tvProgreso)?.visibility = View.GONE

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setProgressBarVisibility(false)
        SessionManager.limpiaFirma(requireContext())

        // 1. Configurar Visibilidad del Card con el Switch
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
            }
        }

        // 2. Cargar el listado de alcoholímetros desde el ViewModel
        cargarListaAlcoholimetros()

        // 3. Listener cuando el inspector selecciona un equipo del desplegable
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

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollConfiguracion.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    private fun cargarListaAlcoholimetros() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext()) // Ajustá con tu clase de BD
            val equiposDb = db.catalogoDao().obtenerAlcoholimetros()

            withContext(Dispatchers.Main) {
                listaEquipos = equiposDb

                if (listaEquipos.isNotEmpty()) {
                    // Usamos "nroSerie" en lugar de "serie"
                    val nombresEquipos = listaEquipos.map { "${it.marca ?: "Sin Marca"} (Serie: ${it.nroSerie ?: "S/C"})" }

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        nombresEquipos
                    )
                    binding.etConfigMarca.setAdapter(adapter)
                } else {
                    Toast.makeText(requireContext(), "No hay alcoholímetros cargados", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun completarCamposEquipo(equipo: AlcoholimetrosEntity) {
        binding.etConfigMarca.setText("${equipo.marca ?: ""} (Serie: ${equipo.nroSerie ?: "S/C"})", false)
        binding.etConfigModelo.setText(equipo.modelo ?: "")
        binding.etConfigSerie.setText(equipo.nroSerie ?: "")
        binding.etConfigAprobacion.setText(equipo.codHomologacion ?: "")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}