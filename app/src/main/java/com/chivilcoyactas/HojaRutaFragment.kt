package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chivilcoyactas.databinding.FragmentHojaRutaBinding

class HojaRutaFragment : Fragment() {

    private var _binding: FragmentHojaRutaBinding? = null
    private val binding get() = _binding!!
    private val actaViewModel: ActaViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHojaRutaBinding.inflate(inflater, container, false)

        (activity as? MainActivity)?.findViewById<View>(R.id.progressBar)?.visibility = View.GONE
        (activity as? MainActivity)?.findViewById<View>(R.id.tvProgreso)?.visibility = View.GONE
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configurar RecyclerView
        binding.rvHojaRuta.layoutManager = LinearLayoutManager(requireContext())

        // Aquí llamarías a tu API. Por ahora simulamos datos:
        val listaSimulada = listOf(
            ActaPrevia(1, "ORDEN #1024", "Av. Ceballos", "123", "Ruidos Molestos", "Juan Pérez"),
            ActaPrevia(2, "ORDEN #1025", "Pellegrini", "540", "Obra sin permiso", "Constructora S.A."),
            ActaPrevia(3, "ORDEN #1028", "Soarez", "20", "Vereda Obstruida", "")
        )

        val adapter = HojaRutaAdapter(listaSimulada) { actaSeleccionada ->
            // AL TOCAR UN ITEM: Cargamos el ViewModel con datos precargados
            actaViewModel.resetearActa()
            actaViewModel.idHojaRuta = actaSeleccionada.id
            actaViewModel.calle = actaSeleccionada.calle
            actaViewModel.nro = actaSeleccionada.altura
            actaViewModel.nombreInfractor = actaSeleccionada.infractorNombre
            // Marcar que no es una acta "de cero"
            actaViewModel.esNuevaActa = false

            findNavController().navigate(R.id.action_hojaRuta_to_step1_ubicacion)
        }

        binding.rvHojaRuta.adapter = adapter

        // 2. Botón NUEVA ACTA (Arriba a la derecha)
        binding.btnNuevaActa.setOnClickListener {
            actaViewModel.resetearActa()
            actaViewModel.esNuevaActa = true
            findNavController().navigate(R.id.action_hojaRuta_to_step1_ubicacion)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class HojaRutaAdapter(
        private val items: List<ActaPrevia>,
        private val onItemClick: (ActaPrevia) -> Unit
    ) : RecyclerView.Adapter<HojaRutaAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // Vinculá los IDs de tu item_hoja_ruta.xml
            val tvOrden: android.widget.TextView = view.findViewById(R.id.tvIdActaPrevia)
            val tvDireccion: android.widget.TextView = view.findViewById(R.id.tvDireccionHoja)
            val tvMotivo: android.widget.TextView = view.findViewById(R.id.tvMotivoHoja)
            val card: com.google.android.material.card.MaterialCardView = view as com.google.android.material.card.MaterialCardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hoja_ruta, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvOrden.text = item.ordenNumero
            holder.tvDireccion.text = "${item.calle} ${item.altura}"
            holder.tvMotivo.text = item.motivo

            holder.card.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }
}