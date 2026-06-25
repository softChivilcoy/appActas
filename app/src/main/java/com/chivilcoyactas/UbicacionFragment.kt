package com.chivilcoyactas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentUbicacionBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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

        // 1. 🛠️ PASO PRIMARIO: Recuperamos lo que haya en el ViewModel de entrada
        latitudActual = actaViewModel.latitud ?: 0.0
        longitudActual = actaViewModel.longitud ?: 0.0

        // 2. Inicializamos el cliente de GPS y disparamos la búsqueda en segundo plano
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        obtenerUbicacion()

        // 3. Forzamos el casteo de la vista del binding al MapView de OSMDroid
        openStreetMap = binding.mapView as? org.osmdroid.views.MapView

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

        // 4. Evaluamos el centrado del mapa inicial
        if (latitudActual != 0.0 && longitudActual != 0.0) {
            // Si el inspector volvió atrás, el mapa clava la ubicación guardada al instante
            actualizarMarcadorMapa()
        } else {
            // Si es un acta nueva de paquete, se para en la plaza un milisegundo de base
            // mientras 'obtenerUbicacion()' (que ya se disparó arriba) encuentra el punto real
            // y lo mueve automáticamente.
            val plazaChivilcoy = GeoPoint(-34.8981, -60.0183)
            openStreetMap?.controller?.setCenter(plazaChivilcoy)
            openStreetMap?.controller?.setZoom(16.0)
            openStreetMap?.post { openStreetMap?.invalidate() }
        }



        // --- ESCUCHA DE MOVIMIENTO DEL MAPA ---
        openStreetMap?.addMapListener(object : org.osmdroid.events.MapListener {
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
        })

        // --- 1. CONFIGURACIÓN DEL BUSCADOR DE CALLES OFFLINE ---

        //val callesChivilcoy = arrayOf("Av. Ceballos", "Av. Soarez", "Pellegrini", "25 de Mayo", "Moreno", "Belgrano", "Sarmiento")

        // Cargamos el listado completo desde el XML de recursos de la App
        val callesChivilcoy = resources.getStringArray(R.array.calles_chivilcoy_list)
        //val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, callesChivilcoy)
        //binding.acCalle.setAdapter(adapter)

        // 🚀 Pasamos una copia MUTABLE (ArrayList) al constructor para evitar el crash
        val listaSugerenciasMutables = ArrayList(callesChivilcoy.toList())

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            listaSugerenciasMutables
        ) {

            // Mantenemos una referencia fija de todas las calles de la muni para filtrar
            private val todasLasCalles = callesChivilcoy.toList()

            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()

                        if (constraint.isNullOrEmpty()) {
                            results.values = todasLasCalles
                            results.count = todasLasCalles.size
                        } else {
                            val busqueda = constraint.toString().lowercase(Locale.getDefault()).trim()

                            // Buscamos cualquier coincidencia parcial (el "77" o el "SESSION")
                            val sugerenciasFiltradas = todasLasCalles.filter { calle ->
                                calle.lowercase(Locale.getDefault()).contains(busqueda)
                            }

                            results.values = sugerenciasFiltradas
                            results.count = sugerenciasFiltradas.size
                        }
                        return results
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        // 🎯 Ahora que la lista interna es un ArrayList, no va a explotar acá:
                        clear()
                        if (results != null && results.count > 0) {
                            addAll(results.values as List<String>)
                        }
                        notifyDataSetChanged()
                    }
                }
            }
        }

        // Vinculamos el nuevo adapter blindado
        binding.acCalle.setAdapter(adapter)


        // 🚀 BLOQUE DE VALIDACIÓN ESTRICTA:
        binding.acCalle.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            // Nos interesa cuando PIERDE el foco (hasFocus == false)
            if (!hasFocus) {
                val textoIngresado = binding.acCalle.text.toString().trim()

                // Si el campo está vacío, no hacemos nada (que actúe el validador normal al guardar)
                if (textoIngresado.isNotEmpty()) {

                    // Verificamos si el texto exacto existe en la lista de calles oficiales
                    val existeCalle = callesChivilcoy.any { calle ->
                        calle.equals(textoIngresado, ignoreCase = true)
                    }

                    if (!existeCalle) {
                        // Opción A: Le borrás el texto para obligarlo a elegir bien
                        binding.acCalle.setText("")

                        // Opción B: Le mostrás un error visual en el campo
                        binding.acCalle.error = "Seleccioná una calle válida de la lista"

                        // Opcional: Podés mandarle un Toast rápido para que sepa qué pasó en la madrugada
                        Toast.makeText(requireContext(), "Calle no oficial. Seleccioná una de la lista sugerida.", Toast.LENGTH_SHORT).show()
                    } else {
                        // Si existe pero lo escribió con diferencias de mayúsculas,
                        // se lo corregimos al formato oficial de la lista
                        val calleOficial = callesChivilcoy.first { it.equals(textoIngresado, ignoreCase = true) }
                        binding.acCalle.setText(calleOficial)
                    }
                }
            }
        }

        // --- 2. LÓGICA DE OPERATIVO (SOLO TRÁNSITO) ---
        if (actaViewModel.tipoActa == TipoActa.TRANSITO) {
            binding.layoutOperativoContainer.visibility = View.VISIBLE
            binding.cvEjidoUrbano.visibility = View.VISIBLE
        } else {
            binding.layoutOperativoContainer.visibility = View.GONE
            binding.cvEjidoUrbano.visibility = View.GONE
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
        binding.swDetalleUbicacion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvEstadoDetalle.text = "Sí"
                binding.tvEstadoDetalle.setTextColor(Color.parseColor("#33BB66"))
                binding.layoutCamposDetalleUbicacion.visibility = View.VISIBLE
                binding.scrollUbicacion.postDelayed({
                    binding.scrollUbicacion.smoothScrollTo(0, binding.swDetalleUbicacion.top)
                }, 200)
            } else {
                binding.tvEstadoDetalle.text = "No"
                binding.tvEstadoDetalle.setTextColor(Color.parseColor("#777777"))
                binding.layoutCamposDetalleUbicacion.visibility = View.GONE
                binding.etDeptoUbicacion.text?.clear()
                binding.etReferenciaUbicacion.text?.clear()
            }
        }

        // --- 4. RECUPERAR DATOS SI EL INSPECTOR VOLVIÓ ATRÁS ---
        recuperarDatosUbicacion()

        // --- 5. CONFIGURACIÓN DE AUTO-SCROLLS ---
        configurarAutoScroll(binding.etAlturaUbicacion)
        configurarAutoScroll(binding.etDeptoUbicacion)
        configurarAutoScroll(binding.etReferenciaUbicacion)

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
            (activity as? MainActivity)?.hideKeyboard()
            binding.etAlturaUbicacion.requestFocus()
        }

        // --- 7. BOTÓN SIGUIENTE (CON VALIDACIÓN Y GUARDADO) ---
        binding.btnSiguienteUbicacion.setOnClickListener {
            if (validarCamposUbicacion()) {
                guardarDatosUbicacion()
                if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
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
    }

    // --- FUNCIONES AUXILIARES DE FLUJO ---

    private fun validarCamposUbicacion(): Boolean {
        val calle = binding.acCalle.text.toString().trim()
        val altura = binding.etAlturaUbicacion.text.toString().trim()

        if (actaViewModel.tipoActa == TipoActa.TRANSITO) {
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

    private fun actualizarMarcadorMapa() {
        val mapa = openStreetMap ?: return

        if (latitudActual != 0.0) {
            val puntoReal = GeoPoint(latitudActual, longitudActual)

            // 🛠️ Forzamos el zoom exacto justo antes de mover la cámara
            mapa.controller.setZoom(16.5)
            mapa.controller.setCenter(puntoReal)

            // Refrescamos el dibujo por las dudas
            mapa.post { mapa.invalidate() }
            /*
            // Limpiamos todas las capas viejas para no duplicar nada
            mapa.overlays.clear()

            markerActual?.let { mapa.overlays.remove(it) }

            markerActual = Marker(mapa).apply {
                position = puntoChivilcoy
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Ubicación del Acta"

                // 🛠️ AGREGA ESTA LÍNEA: Usa el icono de mapa nativo de Android
                icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_mylocation)
            }

            mapa.overlays.add(markerActual)
            mapa.invalidate()*/
        }
    }

    override fun onResume() {
        super.onResume()
        openStreetMap?.onResume()

        // 🔄 Cada vez que el inspector vuelve a la pantalla, re-chequeamos el estado del GPS
        obtenerUbicacion()
    }

    override fun onPause() {
        // Apaga el buscador de GPS para cuidar la batería del dispositivo
        fusedLocationClient.removeLocationUpdates(object : com.google.android.gms.location.LocationCallback() {})

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

        // 3. Si el GPS está prendido, ponemos un estado de "Buscando..." para dar feedback visual
        if (latitudActual == 0.0) {
            binding.tvGpsStatus.text = "🔄 BUSCANDO SEÑAL DE GPS..."
            binding.tvGpsStatus.setTextColor(Color.parseColor("#FF9800")) // Color naranja
        }

        // 4. Intentamos primero con la última ubicación conocida
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && (System.currentTimeMillis() - location.time) < 30000) {
                // Si la última ubicación existe y es fresca (menos de 30 segundos), la usamos de una
                latitudActual = location.latitude
                longitudActual = location.longitude
                actaViewModel.latitud = location.latitude
                actaViewModel.longitud = location.longitude

                binding.tvGpsStatus.text = "📍 UBICACIÓN VERIFICADA ✅"
                binding.tvGpsStatus.setTextColor(Color.parseColor("#33BB66"))
                actualizarMarcadorMapa()
            } else {
                // 🚀 SI NO HAY UBICACIÓN FRESCA, DISPARAMOS EL BUSCADOR CONTINUO ASTUTO
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2000 // Chequea cada 2 segundos
                ).setMinUpdateIntervalMillis(1000).build()

                // Removemos cualquier petición previa por las dudas antes de arrancar la nueva
                fusedLocationClient.removeLocationUpdates(object : com.google.android.gms.location.LocationCallback() {})

                fusedLocationClient.requestLocationUpdates(locationRequest, object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                        val lastLoc = locationResult.lastLocation ?: return

                        // ¡ENGANCHÓ! Guardamos los datos reales
                        latitudActual = lastLoc.latitude
                        longitudActual = lastLoc.longitude
                        actaViewModel.latitud = lastLoc.latitude
                        actaViewModel.longitud = lastLoc.longitude

                        // Actualizamos la interfaz limpiando el error viejo
                        binding.tvGpsStatus.text = "📍 UBICACIÓN EN TIEMPO REAL ✅"
                        binding.tvGpsStatus.setTextColor(Color.parseColor("#33BB66"))
                        actualizarMarcadorMapa()

                        // 🔥 CLAVE: Como ya encontramos la ubicación, apagamos el buscador para no gastar batería
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }, android.os.Looper.getMainLooper())
            }
        }
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
}