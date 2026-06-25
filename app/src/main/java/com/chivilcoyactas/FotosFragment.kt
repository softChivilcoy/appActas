package com.chivilcoyactas

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chivilcoyactas.databinding.FragmentFotosBinding
import java.io.File
import kotlin.getValue

class FotosFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentFotosBinding? = null
    private val binding get() = _binding!!

    private var uriFotoActual: android.net.Uri? = null

    // 1. Lanzador para la CÁMARA (Guarda directamente en el ViewModel)
    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoActual != null) {
            // Pasamos la URI del archivo real al ViewModel para que lo procese
            actaViewModel.agregarFotoDesdeUri(requireContext(), uriFotoActual!!)
        }
    }

    // 2. Lanzador para GALERÍA
    private val seleccionarFotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // TIP: Aquí podrías implementar una lógica para convertir URI a Bitmap e insertar en ViewModel
            Toast.makeText(context, "Imagen seleccionada de galería", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ajustar el progreso en el MainActivity
        val paso = if (actaViewModel.tipoActa == TipoActa.INSPECCION) 6 else 8
        (activity as? MainActivity)?.actualizarProgreso(paso)

        // --- INICIALIZACIÓN DE DATOS ---

        // Recuperar observaciones si ya existen
        binding.etObservaciones.setText(actaViewModel.observaciones)

        // Observar la lista de fotos del ViewModel (Motor de la grilla)
        actaViewModel.listaFotos.observe(viewLifecycleOwner) { fotos ->
            dibujarFotos(fotos)
        }

        // --- LISTENERS DE BOTONES ---

        binding.btnCamara.setOnClickListener {
            val permisoCaja = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)

            if (permisoCaja == PackageManager.PERMISSION_GRANTED) {
                abrirCamaraConArchivoReal()
            } else {
                requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        binding.btnGaleria.setOnClickListener {
            seleccionarFotoLauncher.launch("image/*")
        }

        binding.btnVolverFotos.setOnClickListener {
            actaViewModel.observaciones = binding.etObservaciones.text.toString()
            findNavController().navigateUp()
        }

        binding.btnFinalizarActa.setOnClickListener {
            actaViewModel.observaciones = binding.etObservaciones.text.toString()
            findNavController().navigate(R.id.action_fotos_to_firma)
        }

        // Ocultar teclado al tocar la grilla de fotos
        binding.gridFotos.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollFotos.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    // Función auxiliar para crear el archivo temporal que usará la cámara
    private fun abrirCamaraConArchivoReal() {
        try {
            // Creamos un archivo vacío en la caché temporal
            val archivoTemporal =
                File(requireContext().cacheDir, "camara_temp_${System.currentTimeMillis()}.jpg")

            // Lo convertimos a una URI segura usando tu FileProvider (revisá que coincida el "authority")
            uriFotoActual = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                archivoTemporal
            )

            // Lanzamos la cámara pasándole el archivo físico listo para ser llenado con megapíxeles reales
            tomarFotoLauncher.launch(uriFotoActual)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al preparar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Lanzador para solicitar el permiso de la cámara
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 🚀 SOLUCIÓN: Si dio el permiso, llamamos a la función que prepara el archivo y lanza la cámara
            abrirCamaraConArchivoReal()
        } else {
            Toast.makeText(requireContext(), "El permiso de la cámara es necesario para adjuntar evidencias fotográficas.", Toast.LENGTH_LONG).show()
        }
    }

    // --- LÓGICA DE INTERFAZ ---

    private fun dibujarFotos(fotos: List<Bitmap>) {
        binding.gridFotos.removeAllViews()

        binding.tvContadorFotos.text = "Fotos adjuntas (${fotos.size}):"
        binding.tvSinFotos.visibility = if (fotos.isEmpty()) View.VISIBLE else View.GONE

        fotos.forEachIndexed { index, bitmap ->
            val frame = crearFrameFoto(bitmap, index)
            binding.gridFotos.addView(frame)
        }
    }

    private fun crearFrameFoto(bitmap: Bitmap, index: Int): FrameLayout {
        val frame = FrameLayout(requireContext()).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 300 // Un poco más grande para mejor visualización
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(10, 10, 10, 10)
            }
        }

        val img = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }

        val btnDel = ImageButton(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(75, 75).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            // Fondo rojo semi-transparente para que se vea la X
            setBackgroundColor(android.graphics.Color.parseColor("#88FF0000"))
            setOnClickListener {
                actaViewModel.eliminarFoto(index)
            }
        }

        frame.addView(img)
        frame.addView(btnDel)
        return frame
    }

    override fun onPause() {
        super.onPause()
        // Resguardo de seguridad: guardar texto al salir
        actaViewModel.observaciones = binding.etObservaciones.text.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}