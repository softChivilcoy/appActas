package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import com.chivilcoyactas.databinding.FragmentSecuestroBinding

class SecuestroFragment : Fragment() {

    private var _binding: FragmentSecuestroBinding? = null
    private val binding get() = _binding!!
    private val actaViewModel: ActaViewModel by activityViewModels()

    // Definimos los arrays (esto luego vendrá de la BD)
    private val itemsMotor = arrayOf("Bocina", "Freno de Emergencia", "Bujías", "Alternador", "Distribuidor", "Radiador", "Batería", "Tapa de Aceite", "Varilla de Aceite")
    private val itemsExterior = arrayOf("Capot", "Guardabarros Del. Der.", "Guardabarros Del. Izq.", "Luneta", "Tapa Baúl", "Puerta Delantera", "Parabrisas", "Rueda de Auxilio")
    private val itemsInterior = arrayOf("Volante", "Estéreo", "Alfombra", "Cinturones", "Espejo Int.", "Tablero", "Guantera")

    // Nuevos arrays para Motos/Cuatris
    private val itemsMoto = arrayOf(
        "Llave de contacto", "Guard. Trasero", "Guard. Delantero",
        "Espejo Retrov. Der.", "Espejo Retrov. Izq.", "Luz Trasera",
        "Luz Delantera", "Pedalin Delant. Der.", "Pedalin Delant. Izq.",
        "Pedalin Trasero Der.", "Pedalin Trasero Izq.", "Tapa Apoya Pies Der.",
        "Tapa Apoya Pies Izq.", "Giro Delant. Der.", "Giro Delant. Izq.",
        "Giro Trasero Der.", "Giro Trasero Izq.", "Asiento Individual",
        "Asiento Enterizo", "Pie de sostén", "Escape c/ Silenciador",
        "Escape s/ Silenciador", "Tapa de Combustible"
    )
    private val estadosOp = arrayOf("B (Bueno)", "M (Malo)", "S/D (Sin Datos)")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecuestroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.actualizarProgreso(6)

        val tipo = actaViewModel.tipoVehiculo

        if (tipo.contains("MOTO") || tipo.contains("CICLO") || tipo.contains("CUATRI")) {
            // --- MODO MOTO ---
            binding.tvTituloSecuestro.text = "SECUESTRO: MOTOCICLETA / OTROS"

            // Ocultamos secciones de autos que no sirven
            binding.cardInterior.visibility = View.GONE
            binding.checkIncluirInterior.visibility = View.GONE

            // Poblamos con los datos de moto en el contenedor que prefieras
            // Por ejemplo, usamos el de 'Motor' para todo el inventario de moto
            poblarSeccion(binding.containerMotor, itemsMoto)

            // Ocultamos el contenedor de exterior de autos para que no se duplique
            binding.containerExterior.removeAllViews()
            binding.cardExterior.visibility = View.GONE

        } else {
            // --- MODO AUTO (Lo que ya tenías) ---
            binding.tvTituloSecuestro.text = "SECUESTRO: VEHÍCULO"
            binding.cardExterior.visibility = View.VISIBLE

            poblarSeccion(binding.containerMotor, itemsMotor)
            poblarSeccion(binding.containerExterior, itemsExterior)
            poblarSeccion(binding.containerInterior, itemsInterior)
        }
       // recuperarCamposEspeciales()


        // Recuperar estado del check interior
        binding.checkIncluirInterior.isChecked = actaViewModel.incluyoInterior
        binding.cardInterior.visibility = if (actaViewModel.incluyoInterior) View.VISIBLE else View.GONE

        binding.checkIncluirInterior.setOnCheckedChangeListener { _, isChecked ->
            binding.cardInterior.visibility = if (isChecked) View.VISIBLE else View.GONE
            actaViewModel.incluyoInterior = isChecked
        }

        binding.btnSiguienteSecuestro.setOnClickListener {
            guardarInventario()
            findNavController().navigate(R.id.action_secuestro_to_testigos)
        }

        binding.btnVolverSecuestro.setOnClickListener {
            guardarInventario() // Guardamos antes de volver por las dudas
            findNavController().navigateUp()
        }

        // Ocultar teclado al tocar fuera de los campos
        binding.scrollSecuestro.setOnTouchListener { _, _ ->
            (activity as? MainActivity)?.hideKeyboard()
            false
        }
    }

    private fun poblarSeccion(container: LinearLayout, items: Array<String>) {
        container.removeAllViews()

        items.forEach { nombre ->
            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_inventario, container, false)
            itemView.tag = nombre

            val tvNombre = itemView.findViewById<TextView>(R.id.tvNombreItem)
            val check = itemView.findViewById<CheckBox>(R.id.checkPresente)
            val rgEstado = itemView.findViewById<RadioGroup>(R.id.rgEstado)

            tvNombre.text = nombre

            // Recuperar datos previos
            val datosPrevios = actaViewModel.inventarioSecuestro[nombre]
            if (datosPrevios != null && datosPrevios.contains("|")) {
                val partes = datosPrevios.split("|")
                check.isChecked = (partes[0] == "S")

                // Mapeamos el string guardado al RadioButton correspondiente
                when (partes[1]) {
                    "B (Bueno)" -> rgEstado.check(R.id.rbBueno)
                    "M (Malo)" -> rgEstado.check(R.id.rbMalo)
                    else -> rgEstado.check(R.id.rbSD)
                }
            } else {
                check.isChecked = true
                rgEstado.check(R.id.rbBueno) // Default: Bueno
            }

            // Si el ítem no está presente, desactivamos los RadioButtons
            rgEstado.isEnabled = check.isChecked
            for (i in 0 until rgEstado.childCount) {
                rgEstado.getChildAt(i).isEnabled = check.isChecked
            }

            check.setOnCheckedChangeListener { _, isChecked ->
                for (i in 0 until rgEstado.childCount) {
                    rgEstado.getChildAt(i).isEnabled = isChecked
                }
            }

            container.addView(itemView)
        }
    }
    private fun guardarInventario() {
        // 1. Limpiamos el mapa para no duplicar datos viejos
        actaViewModel.inventarioSecuestro.clear()

        actaViewModel.numeroMotor = binding.etNumeroMotor.text.toString().trim()
        actaViewModel.numeroChasis = binding.etNumeroChasis.text.toString().trim()
        actaViewModel.estadoCentral = binding.etEstadoCentral.text.toString().trim()

        // 2. Procesamos cada contenedor usando la función de abajo
        extraerDeContenedor(binding.containerMotor)
        extraerDeContenedor(binding.containerExterior)

        // 3. Condicional para el interior
        if (binding.checkIncluirInterior.isChecked) {
            extraerDeContenedor(binding.containerInterior)
        }

        actaViewModel.incluyoInterior = binding.checkIncluirInterior.isChecked
    }

    private fun extraerDeContenedor(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            val nombre = view.tag as? String ?: ""

            if (nombre.isNotEmpty()) {
                val check = view.findViewById<CheckBox>(R.id.checkPresente)
                val rgEstado = view.findViewById<RadioGroup>(R.id.rgEstado)

                val estaPresente = if (check.isChecked) "S" else "N"

                // IMPORTANTE: Mantenemos el formato exacto para tu backend PHP en Chivilcoy
                val estado = when (rgEstado.checkedRadioButtonId) {
                    R.id.rbBueno -> "B (Bueno)"
                    R.id.rbMalo -> "M (Malo)"
                    else -> "S/D (Sin Datos)"
                }

                actaViewModel.inventarioSecuestro[nombre] = "$estaPresente|$estado"
            }
        }
    }
}