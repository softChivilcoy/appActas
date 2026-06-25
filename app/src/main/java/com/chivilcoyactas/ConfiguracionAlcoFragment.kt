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

        // Ocultamos la barra de progreso en el MainActivity
        (activity as? MainActivity)?.setProgressBarVisibility(false)

        // 🧹 Limpieza de seguridad: nos aseguramos de que no haya firmas viejas en memoria
        SessionManager.limpiaFirma(requireContext())

        binding.switchHabilitarAlco.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvEstadoAlco.text = "Sí"
                binding.tvEstadoAlco.setTextColor(Color.parseColor("#33BB66")) // Verde cuando es Sí

                binding.cardDatosEquipo.visibility = View.VISIBLE
                binding.cardDatosEquipo.alpha = 0f
                binding.cardDatosEquipo.animate().alpha(1f).setDuration(300).start()
            } else {
                binding.tvEstadoAlco.text = "No"
                binding.tvEstadoAlco.setTextColor(Color.parseColor("#777777")) // Gris cuando es No

                binding.cardDatosEquipo.visibility = View.GONE
            }
        }


        // 🛠️ CONFIGURACIÓN DEL LIENZO DE FIRMA
        /*binding.signaturePadInspector.post {
            // Inicializamos el bitmap del tamaño exacto que tomó el View en la pantalla del 3nStar
            val ancho = binding.signaturePadInspector.width
            val alto = binding.signaturePadInspector.height
            if (ancho > 0 && alto > 0) {
                bitmapFirma = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
                canvasFirma = Canvas(bitmapFirma)
                canvasFirma.drawColor(Color.WHITE) // Fondo blanco de base
            }
        }

        // Detectar el arrastre del dedo por la pantalla
        binding.signaturePadInspector.setOnTouchListener { v, event ->
            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pathFirma.moveTo(x, y)
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    pathFirma.lineTo(x, y)
                    haFirmado = true // El usuario ya interactuó y dibujó algo
                    canvasFirma.drawPath(pathFirma, paintFirma)

                    // Forzar al View a redibujarse mostrando el trazo
                    val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmapFirma)
                    binding.signaturePadInspector.background = drawable
                }
                MotionEvent.ACTION_UP -> {
                    pathFirma.reset()
                }
            }
            v.performClick()
            true
        }

        // Botón para borrar el lienzo entero
        binding.btnLimpiarFirma.setOnClickListener {
            if (::canvasFirma.isInitialized) {
                canvasFirma.drawColor(Color.WHITE) // Pintamos todo de blanco encima
                binding.signaturePadInspector.background = null
                haFirmado = false
                pathFirma.reset()
            }
        }*/

        binding.btnGuardarConfig.setOnClickListener {
            if (binding.switchHabilitarAlco.isChecked) {
                val marca = binding.etConfigMarca.text.toString().trim()
                val modelo = binding.etConfigModelo.text.toString().trim()
                val serie = binding.etConfigSerie.text.toString().trim()


                if (marca.isEmpty() || modelo.isEmpty() || serie.isEmpty()) {
                    Toast.makeText(requireContext(), "Debe cargar todos los campos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                SessionManager.guardarAlcoholimetro(
                    requireContext(),
                    marca,
                    binding.etConfigModelo.text.toString().trim(),
                    serie
                )
            } else {
                // Si el switch está apagado, nos aseguramos de que no queden datos viejos
                SessionManager.limpiarDatos(requireContext())
            }

            /*if (!haFirmado) {
                Toast.makeText(requireContext(), "Por favor, registre su firma para poder iniciar el turno.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Guardar la firma en Base64 para usarla en todas las actas del viaje
            val firmaBase64 = convertirBitmapABase64(bitmapFirma)

            // Guardamos en tu SessionManager (asumiendo que tenés una función para persistir la sesión)
            SessionManager.guardarFirmaInspector(requireContext(), firmaBase64)*/

            actaViewModel.resetearActa()
            findNavController().navigate(R.id.action_configAlco_to_step1)
        }

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollConfiguracion.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    // Función auxiliar para transformar el dibujo en un String liviano de texto
    private fun convertirBitmapABase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        // Comprimimos un poco en PNG para mantener la transparencia u opacidad del trazo
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    }

    private fun cargarDatosPrevios() {
        val datos = SessionManager.obtenerAlcoholimetro(requireContext())
        binding.etConfigMarca.setText(datos["marca"])
        binding.etConfigModelo.setText(datos["modelo"])
        binding.etConfigSerie.setText(datos["serie"])
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}