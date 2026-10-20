package com.chivilcoyactas

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.chivilcoyactas.databinding.FragmentFotosBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.getValue

class FotosFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentFotosBinding? = null
    private val binding get() = _binding!!

    private var uriFotoActual: Uri? = null

    // 1. Lanzador para la CÁMARA
    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoActual != null) {
            actaViewModel.agregarFotoDesdeUri(requireContext(), uriFotoActual!!)
        }
    }

    // 2. Lanzador para GALERÍA (🚀 Procesa e inserta la imagen de la galería)
    private val seleccionarFotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            actaViewModel.agregarFotoDesdeUri(requireContext(), it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Forzar scroll al inicio al entrar o volver
        binding.scrollFotos.post {
            binding.scrollFotos.scrollTo(0, 0)
        }

        // Aplicar padding dinámico según el teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollFotos) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val density = resources.displayMetrics.density
            val extraPadding = (32 * density).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeHeight + extraPadding)
            insets
        }

        // Ajustar el progreso en el MainActivity
        val paso = if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) 6 else 8
        (activity as? MainActivity)?.actualizarProgreso(paso)

        // Recuperar observaciones si ya existen
        binding.etObservaciones.setText(actaViewModel.observaciones)

        // Observar la lista de fotos del ViewModel
        actaViewModel.listaFotos.observe(viewLifecycleOwner) { fotos ->
            dibujarFotos(fotos)
        }

        // LISTENERS DE BOTONES
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

        binding.inputObservaciones.setEndIconOnClickListener {
            val textoLimpio = binding.etObservaciones.text.toString().trimEnd()
            binding.etObservaciones.setText(textoLimpio)

            binding.etObservaciones.clearFocus()
            binding.contenedorPrincipal.requestFocus()
            (activity as? MainActivity)?.hideKeyboard()

            binding.scrollFotos.post {
                binding.scrollFotos.smoothScrollTo(0, 0)
            }
        }

        binding.etObservaciones.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                ejecutarAutoScroll(binding.inputObservaciones)
                view.setBackgroundResource(R.drawable.bg_edittext_focused)
            } else {
                view.setBackgroundResource(0)
            }
        }

        binding.btnVolverFotos.setOnClickListener {
            actaViewModel.observaciones = binding.etObservaciones.text.toString()
            findNavController().navigateUp()
        }

        binding.btnFinalizarActa.setOnClickListener {
            actaViewModel.observaciones = binding.etObservaciones.text.toString()
            findNavController().navigate(R.id.action_fotos_to_firma)
        }

        binding.gridFotos.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        binding.scrollFotos.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    private fun ejecutarAutoScroll(view: View) {
        binding.scrollFotos.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            val rect = android.graphics.Rect()
            view.getDrawingRect(rect)
            binding.scrollFotos.offsetDescendantRectToMyCoords(view, rect)

            val density = resources.displayMetrics.density
            val offsetTop = (90 * density).toInt()

            val targetY = rect.top - offsetTop
            binding.scrollFotos.smoothScrollTo(0, targetY.coerceAtLeast(0))
        }, 500)
    }

    private fun abrirCamaraConArchivoReal() {
        try {
            val archivoTemporal = File(requireContext().cacheDir, "camara_temp_${System.currentTimeMillis()}.jpg")

            uriFotoActual = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                archivoTemporal
            )

            tomarFotoLauncher.launch(uriFotoActual)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al preparar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            abrirCamaraConArchivoReal()
        } else {
            Toast.makeText(requireContext(), "El permiso de la cámara es necesario para adjuntar evidencias fotográficas.", Toast.LENGTH_LONG).show()
        }
    }

    // --- INTERFAZ & GRILLA DE FOTOS ---

    private fun dibujarFotos(fotos: List<Bitmap>) {
        binding.gridFotos.removeAllViews()

        binding.tvContadorFotos.text = "Fotos adjuntas (${fotos.size}):"
        binding.tvSinFotos.visibility = if (fotos.isEmpty()) View.VISIBLE else View.GONE

        fotos.forEachIndexed { index, bitmap ->
            val card = crearFrameFoto(bitmap, index)
            binding.gridFotos.addView(card)
        }
    }

    private fun crearFrameFoto(bitmap: Bitmap, index: Int): com.google.android.material.card.MaterialCardView {
        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()
        val btnSizePx = (32 * density).toInt()

        // 1. Contenedor CardView con esquinas redondeadas
        val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            radius = 12 * density
            strokeWidth = (1 * density).toInt()
            strokeColor = Color.LTGRAY
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = (100 * density).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
        }

        val frameLayout = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 2. Imagen recortada prolija
        val img = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }

        // 3. Botón flotante para eliminar
        val btnDel = ImageButton(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, (4 * density).toInt(), (4 * density).toInt(), 0)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.parseColor("#CCFF0000")) // Rojo visible
            setOnClickListener {
                actaViewModel.eliminarFoto(index)
            }
        }

        frameLayout.addView(img)
        frameLayout.addView(btnDel)
        card.addView(frameLayout)

        return card
    }

    override fun onPause() {
        super.onPause()
        actaViewModel.observaciones = binding.etObservaciones.text.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}