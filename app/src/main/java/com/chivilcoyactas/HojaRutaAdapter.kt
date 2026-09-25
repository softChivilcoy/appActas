package com.chivilcoyactas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chivilcoyactas.databinding.ItemHojaRutaBinding

class HojaRutaAdapter(
    private val items: List<ItemHojaRuta>,
    private val onItemClick: (ItemHojaRuta) -> Unit
) : RecyclerView.Adapter<HojaRutaAdapter.HojaRutaViewHolder>() {

    inner class HojaRutaViewHolder(private val binding: ItemHojaRutaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemHojaRuta) {
            // Asignación de datos a los campos de item_hoja_ruta.xml
            binding.tvIdActaPrevia.text = item.titulo
            binding.tvEstado.text = item.estadoTexto

            val direccionCompleta = if (item.altura.isNotBlank()) {
                "${item.calle} ${item.altura}"
            } else {
                item.calle
            }
            binding.tvDireccionHoja.text = direccionCompleta

            binding.tvMotivoHoja.text = item.motivo
            binding.tvFechaHoja.text = item.fecha

            // Evento de clic al seleccionar la tarjeta
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HojaRutaViewHolder {
        val binding = ItemHojaRutaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HojaRutaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HojaRutaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}