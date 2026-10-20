package com.chivilcoyactas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.chivilcoyactas.databinding.FragmentUbicacionBinding
import com.chivilcoyactas.ui.acta.MapaCompletoDialogFragment
import com.chivilcoyactas.utils.GeoUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class UbicacionFragment : Fragment(R.layout.fragment_ubicacion) {

    private val actaViewModel: ActaViewModel by activityViewModels()
    private var _binding: FragmentUbicacionBinding? = null
    private val binding get() = _binding!!

    // Referencia limpia al MapView nativo de OpenStreetMap
    private var openStreetMap: org.osmdroid.views.MapView? = null
    private var markerActual: Marker? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var latitudActual: Double = 0.0
    private var longitudActual: Double = 0.0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            obtenerUbicacion()
        } else {
            binding.tvGpsStatus.text = "El GPS es obligatorio para emitir actas."
            binding.tvGpsStatus.setTextColor(Color.RED)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUbicacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? MainActivity)?.actualizarProgreso(1)


        // Solución moderna para el UserAgent sin PreferenceManager viejos
        Configuration.getInstance().userAgentValue = requireContext().packageName
        super.onViewCreated(view, savedInstanceState)

        // 1. Ajuste automático del ScrollView según la altura REAL del teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollUbicacion) { view, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // Le damos de padding inferior la altura del teclado + 32dp extra de respiro
            val extraPadding = if (imeHeight > 0) imeHeight + (32 * resources.displayMetrics.density).toInt() else 0

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                extraPadding
            )
            insets
        }

        // Se Inicia SIEMPRE la instancia para evitar el crash si se accede luego
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // 1. Asignamos e inicializamos el Mapa PRIMERO que nada
        openStreetMap = binding.mapView

        // --- CONFIGURACIÓN INICIAL DEL MAPA ---
        openStreetMap?.let { mapa ->
            mapa.setTileSource(TileSourceFactory.MAPNIK)

            mapa.setMultiTouchControls(true)
            mapa.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            val mapController = mapa.controller
            mapController.setZoom(16.0)

            mapa.setDestroyMode(false)

            mapa.post { mapa.invalidate() }

            mapa.setOnTouchListener { _, event ->
                binding.scrollUbicacion.requestDisallowInterceptTouchEvent(true)
                false
            }
        }

        // 2. Cargar datos preexistentes si es una edición de borrador
        cargarDatosUbicacionDesdeViewModel()

        //configurarMapa()          // 1. Bloquea el mapa e inhabilita el arrastre táctil

        // 1. 🛠️ PASO PRIMARIO: Recuperamos lo que haya en el ViewModel de entrada
        latitudActual = actaViewModel.latitud
        longitudActual = actaViewModel.longitud

        // 3. Evaluamos si es un borrador o un acta nueva
        // 🚀 Esperamos a que la vista esté completamente inflada para operar sobre el mapa
        binding.root.post {
            if (latitudActual != 0.0 && longitudActual != 0.0) {
                actualizarMarcadorMapa()
                evaluarEjidoUrbanoAutomatico(latitudActual, longitudActual)

                binding.tvGpsStatus.text = "📍 UBICACIÓN CONSERVADA DE LA INSPECCIÓN ✅"
                binding.tvGpsStatus.setTextColor(Color.parseColor("#33BB66"))
            } else {
                val plazaChivilcoy = GeoPoint(-34.8981, -60.0183)
                openStreetMap?.controller?.setCenter(plazaChivilcoy)


                obtenerUbicacion()
            }
        }

        // --- ESCUCHA DE MOVIMIENTO DEL MAPA ---
       /* openStreetMap?.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                // Cada vez que el inspector arrastra el mapa, el centro cambia
                openStreetMap?.mapCenter?.let { centro ->
                    // Guardamos la posición exacta donde quedó apuntando el pin del medio
                    latitudActual = centro.latitude
                    longitudActual = centro.longitude

                    // 🛠️ Y lo respaldamos en el ViewModel para que no se pierda al cambiar de pantalla
                    actaViewModel.latitud = centro.latitude
                    actaViewModel.longitud = centro.longitude
                }
                return true
            }

            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                return true
            }
        })*/

        // --- 1. CONFIGURACIÓN DEL BUSCADOR DE CALLES OFFLINE ---

        // Cargamos el listado completo desde el XML de recursos de la App
        val callesChivilcoy = resources.getStringArray(R.array.calles_chivilcoy_list)

        // 🚀 Pasamos una copia MUTABLE (ArrayList) al constructor para evitar el crash
        val listaSugerenciasMutables = ArrayList(callesChivilcoy.toList())

        // Límite máximo de sugerencias a desplegar
        val MAX_SUGERENCIAS = 5

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            listaSugerenciasMutables
        ) {
            // Mantenemos una referencia fija de todas las calles para filtrar
            private val todasLasCalles = callesChivilcoy.toList()

            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()

                        if (constraint.isNullOrEmpty()) {
                            // Si el campo está vacío, no mostramos nada hasta que escriba 1 letra
                            results.values = emptyList<String>()
                            results.count = 0
                        } else {
                            val busqueda = constraint.toString().lowercase(Locale.getDefault()).trim()

                            // Buscamos coincidencias parciales y aplicamos el acotamiento con .take()
                            val sugerenciasFiltradas = todasLasCalles.filter { calle ->
                                calle.lowercase(Locale.getDefault()).contains(busqueda)
                            }.take(MAX_SUGERENCIAS) // 👈 ACÁ LIMITAMOS EL LISTADO A 6

                            results.values = sugerenciasFiltradas
                            results.count = sugerenciasFiltradas.size
                        }
                        return results
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        clear()
                        if (results != null && results.count > 0) {
                            addAll(results.values as List<String>)
                            notifyDataSetChanged()
                        } else {
                            notifyDataSetInvalidated()
                        }
                    }
                }
            }
        }

        // Vinculamos el nuevo adapter blindado
        binding.acCalle.setAdapter(adapter)


        // 🚀 BLOQUE DE VALIDACIÓN ESTRICTA Y MANEJO DE FOCO:
        binding.acCalle.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.acCalle.post {
                    binding.acCalle.selectAll()
                }
                ejecutarAutoScroll(binding.acCalle)
            } else {
                val textoIngresado = binding.acCalle.text.toString().trim()

                if (textoIngresado.isNotEmpty()) {
                    // Verificamos si el texto exacto existe en la lista de calles oficiales
                    val existeCalle = callesChivilcoy.any { calle ->
                        calle.equals(textoIngresado, ignoreCase = true)
                    }

                    if (!existeCalle) {
                        binding.acCalle.setText("")
                        binding.acCalle.error = "Seleccioná una calle válida de la lista"
                        Toast.makeText(
                            requireContext(),
                            "Calle no oficial. Seleccioná una de la lista sugerida.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Si existe pero lo escribió con diferencias de mayúsculas, se corrige
                        val calleOficial = callesChivilcoy.first { it.equals(textoIngresado, ignoreCase = true) }
                        binding.acCalle.setText(calleOficial)
                        binding.acCalle.error = null
                    }
                }
            }
        }

        // --- 2. LÓGICA DE OPERATIVO (SOLO TRÁNSITO) ---
        if (actaViewModel.tipoFormulario == TipoFormulario.TRANSITO) {
            binding.layoutOperativoContainer.visibility = View.VISIBLE
            //binding.cvEjidoUrbano.visibility = View.VISIBLE
        } else {
            binding.layoutOperativoContainer.visibility = View.GONE
           // binding.cvEjidoUrbano.visibility = View.GONE
        }


        binding.swOperativo.setOnCheckedChangeListener { _, isChecked ->
            actaViewModel.esOperativo = isChecked
            if (!isChecked) {
                binding.acCalle.setText("")
                binding.tvEstadOperativo.text = "No"
                binding.tvEstadOperativo.setTextColor(Color.parseColor("#777777"))
            } else {
                binding.tvEstadOperativo.text = "Sí"
                binding.tvEstadOperativo.setTextColor(Color.parseColor("#33BB66"))
            }
        }

        // --- 3. LÓGICA DEL SWITCH: DETALLES ADICIONALES ---
        // 1. Manejo del Switch con layout pass garantizado
        binding.swDetalleUbicacion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvEstadoDetalle.text = "Sí"
                binding.tvEstadoDetalle.setTextColor(Color.parseColor("#33BB66"))

                binding.layoutCamposDetalleUbicacion.visibility = View.VISIBLE

                binding.layoutCamposDetalleUbicacion.doOnLayout {
                    // Hacemos scroll directo al contenedor de los detalles
                    binding.scrollUbicacion.post {
                        binding.scrollUbicacion.smoothScrollTo(0, binding.layoutCamposDetalleUbicacion.top)
                    }
                }
            } else {
                binding.tvEstadoDetalle.text = "No"
                binding.tvEstadoDetalle.setTextColor(Color.parseColor("#777777"))
                binding.layoutCamposDetalleUbicacion.visibility = View.GONE
                binding.etDeptoUbicacion.setText("")
                binding.etReferenciaUbicacion.setText("")
            }
        }

        binding.etDeptoUbicacion.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                ejecutarAutoScroll(view)
            }
        }

        binding.etReferenciaUbicacion.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                ejecutarAutoScroll(view)
            }
        }
        
        binding.etAlturaUbicacion.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                ejecutarAutoScroll(view)
            }
        }
        //configurarAutoScrollEnCamposInfractor()


        // --- 4. RECUPERAR DATOS SI EL INSPECTOR VOLVIÓ ATRÁS ---
        recuperarDatosUbicacion()

        // --- 5. CONFIGURACIÓN DE AUTO-SCROLLS ---
       /* configurarAutoScroll(binding.etAlturaUbicacion)
        configurarAutoScroll(binding.etDeptoUbicacion)
        configurarAutoScroll(binding.etReferenciaUbicacion)*/

        // --- 6. GESTIÓN DEL TECLADO Y FOCOS ---
        binding.acCalle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                binding.etAlturaUbicacion.requestFocus()
                true
            } else false
        }

        //------------------------------------------------
        // Ocultar teclado al tocar fuera de los campos
        binding.scrollUbicacion.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }

        binding.containerUbicacion.setOnClickListener {
            (activity as? MainActivity)?.hideKeyboard()
        }

        binding.acCalle.setOnItemClickListener { _, _, _, _ ->
            //(activity as? MainActivity)?.hideKeyboard()
            binding.etAlturaUbicacion.requestFocus()
        }


        // 1. Asignar icono directamente desde sistema/recurso
        binding.tilReferenciaUbicacion.endIconDrawable = androidx.core.content.ContextCompat.getDrawable(
            requireContext(),
            android.R.drawable.ic_menu_close_clear_cancel
        )

        // 2. Tintar el icono para garantizar visibilidad
        binding.tilReferenciaUbicacion.setEndIconTintList(
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#777777"))
        )

        // 3. Forzar visibilidad
        binding.tilReferenciaUbicacion.isEndIconVisible = true

        binding.tilReferenciaUbicacion.setEndIconOnClickListener {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etReferenciaUbicacion.windowToken, 0)
            binding.etReferenciaUbicacion.clearFocus()
        }

        //-------------------------------------
        // ABRE MAPA
        binding.btnExpandirMapa.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                val ejidoEntity = db.catalogoDao().obtenerEjidoUrbano()
                val coordenadas = ejidoEntity?.coordenadas?.map { GeoPoint(it.lat, it.lng) } ?: emptyList()

                val dialog = MapaCompletoDialogFragment(
                    latitud = latitudActual,
                    longitud = longitudActual,
                    puntosEjido = coordenadas
                )
                dialog.show(parentFragmentManager, "MapaCompletoDialog")
            }
        }

        // --- 7. BOTÓN SIGUIENTE (CON VALIDACIÓN Y GUARDADO) ---
        binding.btnSiguienteUbicacion.setOnClickListener {
            if (validarCamposUbicacion()) {
                guardarDatosUbicacion()
                if (actaViewModel.tipoFormulario == TipoFormulario.INSPECCION) {
                    findNavController().navigate(R.id.action_ubicacion_to_procedimiento)
                } else {
                    findNavController().navigate(R.id.action_ubicacion_to_faltas)
                }
            }
        }

        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Impide volver al login accidentalmente
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        // --- BOTÓN PARA REINTENTAR CONEXIÓN DE GPS ---
        binding.btnRefrescarGps.setOnClickListener {
            // Al tocarlo, obligamos a la app a chequear el sensor de nuevo
            obtenerUbicacion()
        }

        dibujarEjidoEnMapa()
    }

    // --- FUNCIONES AUXILIARES DE FLUJO ---

    private fun ejecutarAutoScroll(view: View) {
        view.postDelayed({
            if (_binding == null || !isAdded) return@postDelayed

            // Intentar encontrar el TextInputLayout para incluir el label en el scroll
            val parentInput = (view.parent?.parent as? TextInputLayout) ?: (view.parent as? TextInputLayout) ?: view
            val rect = Rect()
            parentInput.getDrawingRect(rect)

            try {
                binding.scrollUbicacion.offsetDescendantRectToMyCoords(parentInput, rect)
                // Scrolleamos para que el campo quede en la parte superior con un margen generoso (80dp)
                val margin = (80 * resources.displayMetrics.density).toInt()
                val scrollY = (rect.top - margin).coerceAtLeast(0)
                binding.scrollUbicacion.smoothScrollTo(0, scrollY)
            } catch (e: Exception) {
                // Fallback si falla la conversión de coordenadas
            }
        }, 450)
    }

    private fun validarCamposUbicacion(): Boolean {
        val calle = binding.acCalle.text.toString().trim()
        val altura = binding.etAlturaUbicacion.text.toString().trim()

        if (actaViewModel.tipoFormulario == TipoFormulario.TRANSITO) {
            val ejidoSeleccionado = binding.rgEjidoUrbano.checkedRadioButtonId
            if (ejidoSeleccionado == -1) {
                Toast.makeText(context, "Por favor, especifique si está DENTRO o FUERA del ejido urbano", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        if (calle.isEmpty()) {
            Toast.makeText(context, "Falta ingresar la calle", Toast.LENGTH_SHORT).show()
            binding.acCalle.requestFocus()
            return false
        }
        if (altura.isEmpty()) {
            Toast.makeText(context, "Falta ingresar la altura / Nro", Toast.LENGTH_SHORT).show()
            binding.etAlturaUbicacion.requestFocus()
            return false
        }
        return true
    }

    private fun guardarDatosUbicacion() {
        actaViewModel.calle = binding.acCalle.text.toString().trim()
        actaViewModel.altura = binding.etAlturaUbicacion.text.toString().trim().toIntOrNull()
        actaViewModel.latitud = latitudActual
        actaViewModel.longitud = longitudActual
        actaViewModel.ubTieneDetalleAdicional = binding.swDetalleUbicacion.isChecked
        actaViewModel.ubDepto = binding.etDeptoUbicacion.text.toString().trim()
        actaViewModel.ubReferencia = binding.etReferenciaUbicacion.text.toString().trim()
        actaViewModel.ejidoUrbano = when (binding.rgEjidoUrbano.checkedRadioButtonId) {
            R.id.rbEjidoDentro -> 0
            R.id.rbEjidoFuera -> 1
            else -> -1
        }
    }

    private fun recuperarDatosUbicacion() {
        binding.acCalle.setText(actaViewModel.calle)
        //binding.etAlturaUbicacion.setText(actaViewModel.altura)

        val esOperativo = actaViewModel.esOperativo
        binding.swOperativo.isChecked = esOperativo
        if (esOperativo) {
            binding.tvEstadOperativo.text = "Sí"
            binding.tvEstadOperativo.setTextColor(Color.parseColor("#33BB66"))
        } else {
            binding.tvEstadOperativo.text = "No"
            binding.tvEstadOperativo.setTextColor(Color.parseColor("#777777"))
        }

        val tieneDetalle = actaViewModel.ubTieneDetalleAdicional ?: false
        binding.swDetalleUbicacion.isChecked = tieneDetalle
        binding.layoutCamposDetalleUbicacion.visibility = if (tieneDetalle) View.VISIBLE else View.GONE

        if (tieneDetalle) {
            binding.tvEstadoDetalle.text = "Sí"
            binding.tvEstadoDetalle.setTextColor(Color.parseColor("#33BB66"))
        } else {
            binding.tvEstadoDetalle.text = "No"
            binding.tvEstadoDetalle.setTextColor(Color.parseColor("#777777"))
        }

        binding.etDeptoUbicacion.setText(actaViewModel.ubDepto)
        binding.etReferenciaUbicacion.setText(actaViewModel.ubReferencia)

        when (actaViewModel.ejidoUrbano) {
            0 -> binding.rgEjidoUrbano.check(R.id.rbEjidoDentro)
            1 -> binding.rgEjidoUrbano.check(R.id.rbEjidoFuera)
            else -> binding.rgEjidoUrbano.clearCheck()
        }
    }

    private fun configurarAutoScroll(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.scrollUbicacion.postDelayed({
                    binding.scrollUbicacion.smoothScrollTo(0, v.top - 50)
                }, 300)
            }
        }
    }

    // --- LÓGICA DEL MAPA Y GPS (Limpiado y Adaptado a OpenStreetMap) ---
    private var marcadorUbicacionGps: org.osmdroid.views.overlay.Marker? = null

    private fun actualizarMarcadorMapa() {
        val mapa = openStreetMap ?: return

        if (latitudActual != 0.0 && longitudActual != 0.0) {
            val puntoGps = GeoPoint(latitudActual, longitudActual)

            // 1. Si ya existe el marcador, lo eliminamos antes de re-crearlo
            marcadorUbicacionGps?.let { mapa.overlays.remove(it) }

            // 2. Creamos el pin nativo fijado en las coordenadas del GPS
            marcadorUbicacionGps = org.osmdroid.views.overlay.Marker(mapa).apply {
                position = puntoGps
                //title = "Posición GPS verificada" // 1. Quitamos el 'title' para que no tenga texto que mostrar
                setAnchor(
                    org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                    org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                )
                // 2. Consumimos el evento de clic y devolvemos 'true' para evitar que abra la ventana flotante
                setOnMarkerClickListener { _, _ -> true }
            }

            mapa.overlays.add(marcadorUbicacionGps)

            // 3. Centramos el mapa en el punto GPS
            mapa.controller.setZoom(16.5)
            mapa.controller.setCenter(puntoGps)

            mapa.post { mapa.invalidate() }
        }
    }

    override fun onResume() {
        super.onResume()
        openStreetMap?.onResume()

        // 🚀 Solo si es un acta NUEVA (sin coordenadas cargadas aún), re-chequeamos el GPS
        if (latitudActual == 0.0 && longitudActual == 0.0) {
            if (::fusedLocationClient.isInitialized) {
                obtenerUbicacion()
            }
        }
    }

    override fun onPause() {
        // 🚀 Apagamos el GPS de forma segura verificando si fue inicializado previamente
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(object : com.google.android.gms.location.LocationCallback() {})
        }

        openStreetMap?.onPause()
        super.onPause()
    }

    private fun obtenerUbicacion() {
        // 1. Chequeo de permisos
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        // 2. Chequeo de interruptor físico
        if (!isGpsEncendido()) {
            binding.tvGpsStatus.text = "⚠️ EL GPS DEL DISPOSITIVO ESTÁ APAGADO"
            binding.tvGpsStatus.setTextColor(Color.parseColor("#FF3333"))
            return
        }

        // 3. Feedback visual de búsqueda
        if (latitudActual == 0.0) {
            binding.tvGpsStatus.text = "🔄 BUSCANDO SEÑAL DE GPS..."
            binding.tvGpsStatus.setTextColor(Color.parseColor("#FF9800"))
        }

        // 4. Intento con la última ubicación conocida
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && (System.currentTimeMillis() - location.time) < 30000) {
                // Ubicación fresca encontrada de inmediato
                aplicarUbicacionDetectada(location.latitude, location.longitude, "📍 UBICACIÓN VERIFICADA ✅")
            } else {
                // No hay ubicación fresca: iniciamos búsqueda activa en tiempo real
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2000
                ).setMinUpdateIntervalMillis(1000).build()

                fusedLocationClient.requestLocationUpdates(locationRequest, object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                        val lastLoc = locationResult.lastLocation ?: return

                        // Ubicación encontrada tras la búsqueda activa
                        aplicarUbicacionDetectada(lastLoc.latitude, lastLoc.longitude, "📍 UBICACIÓN EN TIEMPO REAL ✅")

                        // Apagamos el buscador para cuidar la batería
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }, android.os.Looper.getMainLooper())
            }
        }
    }

    /**
     * Función auxiliar interna para no repetir las mismas 5 líneas de asignación
     */
    private fun aplicarUbicacionDetectada(lat: Double, lng: Double, mensajeEstado: String) {
        latitudActual = lat
        longitudActual = lng

        actaViewModel.latitud = lat
        actaViewModel.longitud = lng

        binding.tvGpsStatus.text = mensajeEstado
        binding.tvGpsStatus.setTextColor(Color.parseColor("#33BB66"))

        actualizarMarcadorMapa()
        evaluarEjidoUrbanoAutomatico(lat, lng)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isGpsEncendido(): Boolean {
        val locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        // Verifica si el GPS satelital o la ubicación por red/redes móviles están activos
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun dibujarEjidoEnMapa() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            // 1. Traemos la entidad desde Room
            val ejidoEntity = db.catalogoDao().obtenerEjidoUrbano()
            val coordenadas = ejidoEntity?.coordenadas ?: return@launch

            if (coordenadas.size >= 3) {
                openStreetMap?.let { mapa ->
                    // 2. Mapeamos la lista de Coordenada a GeoPoint de OSMDroid
                    val puntosGeo = coordenadas.map { GeoPoint(it.lat, it.lng) }

                    // 3. Creamos la figura del polígono
                    val poligonoEjido = org.osmdroid.views.overlay.Polygon(mapa).apply {
                        points = puntosGeo
                        // Relleno: Verde claro con 20% de opacidad (#3300FF00)
                        fillPaint.color = android.graphics.Color.argb(50, 46, 125, 50)
                        // Borde: Verde oscuro (#2E7D32) de 3dp de grosor
                        outlinePaint.color = android.graphics.Color.parseColor("#2E7D32")
                        outlinePaint.strokeWidth = 4f

                        // 🚀 CLAVE: Desactivamos el click listener para que no interfiera con el arrastre del mapa
                        setOnClickListener { _, _, _ -> false }
                    }

                    // 4. Limpiamos capas previas de polígonos y agregamos la nueva
                    mapa.overlays.removeAll { it is org.osmdroid.views.overlay.Polygon }
                    mapa.overlays.add(0, poligonoEjido) // Se agrega en la capa 0 (al fondo)
                    mapa.invalidate() // Refrescamos el lienzo del mapa
                }
            }
        }
    }

    private fun evaluarEjidoUrbanoAutomatico(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return

        viewLifecycleOwner.lifecycleScope.launch {
            // Obtenemos el ejido almacenado en la DB local
            val db = AppDatabase.getDatabase(requireContext())
            val ejidoEntity = db.catalogoDao().obtenerEjidoUrbano() // O desde catalogoDao si lo tenés inyectado
            val coordenadas = ejidoEntity?.coordenadas ?: emptyList()

            if (coordenadas.isNotEmpty()) {
                val estaDentro = GeoUtils.estaDentroDelEjido(lat, lng, coordenadas)

                if (estaDentro) {
                    binding.rgEjidoUrbano.check(R.id.rbEjidoDentro)
                    actaViewModel.ejidoUrbano = 0
                } else {
                    binding.rgEjidoUrbano.check(R.id.rbEjidoFuera)
                    actaViewModel.ejidoUrbano = 1
                }
            }
        }
    }

    private fun configurarMapa() {
        // 🚀 Inhabilitamos gestos táctiles (zoom y arrastre)
        binding.mapView.setMultiTouchControls(false)
        binding.mapView.setOnTouchListener { _, _ -> true } // Consume los toques para que no se mueva

        // Fijamos el nivel de zoom predeterminado (ej: 16f)
        binding.mapView.controller.setZoom(16.0)
    }


    private fun cargarDatosUbicacionDesdeViewModel() {
        // 🛡️ Si es una acta nueva y no hay datos de calle, salimos limpios
        if (actaViewModel.esNuevaActa && actaViewModel.calle.isEmpty()) return

        try {
            // 1. Calle segura (Evita crash si calle viene nula)
            val calleSegura = actaViewModel.calle ?: ""
            if (calleSegura.isNotEmpty()) {
                binding.acCalle.setText(calleSegura, false) // 'false' evita que se abra el desplegable de sugerencias
            }

            // 2. Altura segura (Conversión limpia a String)
            val alturaTexto = when (val alt = actaViewModel.altura) {
                null, 0 -> ""
                else -> alt.toString()
            }
            binding.etAlturaUbicacion.setText(alturaTexto)

            // 3. Ejido Urbano (Validación segura de los radio buttons)
            when (actaViewModel.ejidoUrbano) {
                0 -> binding.rgEjidoUrbano.check(R.id.rbEjidoDentro)
                1 -> binding.rgEjidoUrbano.check(R.id.rbEjidoFuera)
                else -> binding.rgEjidoUrbano.clearCheck()
            }

            // 4. Detalles adicionales
            val deptoSeguro = actaViewModel.ubDepto ?: ""
            val refSegura = actaViewModel.ubReferencia ?: ""

            binding.etDeptoUbicacion.setText(deptoSeguro)
            binding.etReferenciaUbicacion.setText(refSegura)

            // 5. Switch de detalles
            val tieneDetalles = actaViewModel.ubTieneDetalleAdicional ||
                    deptoSeguro.isNotEmpty() ||
                    refSegura.isNotEmpty()

            binding.swDetalleUbicacion.isChecked = tieneDetalles

        } catch (e: Exception) {
            android.util.Log.e("UBICACION_FRAGMENT", "Error al cargar datos desde ViewModel: ${e.message}")
        }
    }
}