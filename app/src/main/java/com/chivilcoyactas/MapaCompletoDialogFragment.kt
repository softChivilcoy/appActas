package com.chivilcoyactas.ui.acta

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.chivilcoyactas.databinding.DialogMapaCompletoBinding
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class MapaCompletoDialogFragment(
    private val latitud: Double,
    private val longitud: Double,
    private val puntosEjido: List<GeoPoint>
) : DialogFragment() {

    private var _binding: DialogMapaCompletoBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Estilo pantalla completa
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMapaCompletoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapa = binding.mapViewFull
        mapa.setMultiTouchControls(true) // En la vista grande PERMITIMOS explorar

        val puntoGps = GeoPoint(latitud, longitud)
        mapa.controller.setZoom(17.0)
        mapa.controller.setCenter(puntoGps)

        // Dibujar Polígono en la vista grande
        if (puntosEjido.size >= 3) {
            val poligono = Polygon(mapa).apply {
                points = puntosEjido
                fillPaint.color = Color.argb(50, 46, 125, 50)
                outlinePaint.color = Color.parseColor("#2E7D32")
                outlinePaint.strokeWidth = 4f
            }
            mapa.overlays.add(poligono)
        }

        // Agregar Marcador de la posición GPS
        val marcador = Marker(mapa).apply {
            position = puntoGps
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Posición GPS actual"
        }
        mapa.overlays.add(marcador)

        binding.btnCerrarMapa.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}