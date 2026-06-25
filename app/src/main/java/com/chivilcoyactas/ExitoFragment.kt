package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentExitoBinding

class ExitoFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentExitoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // CORRECCIÓN: Usar el binding para inflar la vista
        _binding = FragmentExitoBinding.inflate(inflater, container, false)

        // Ocultar progreso
        (activity as? MainActivity)?.findViewById<View>(R.id.progressBar)?.visibility = View.GONE
        (activity as? MainActivity)?.findViewById<View>(R.id.tvProgreso)?.visibility = View.GONE

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bloquear el botón "Atrás" del sistema (Gesto o botón físico)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Al dejarlo vacío, el botón de atrás no hace NADA.
            // Opcionalmente podés mostrar un Toast:
            // Toast.makeText(context, "El acta ya fue finalizada", Toast.LENGTH_SHORT).show()
        }

        // 1. Simulación de datos
        if (actaViewModel.nroActa.isEmpty()) {
            val randomNro = (1000..9999).random()
            actaViewModel.nroActa = "0001-0000$randomNro"
            actaViewModel.idSistema = "#${(1000..5000).random()}"

            // Generamos el código de 5 dígitos solo si no existe
            actaViewModel.codigoValidacion = (10000..99999).random().toString()
        }

        // 2. Mostrar datos en pantalla (Ahora binding ya no es nulo)
        binding.tvNroActaFinal.text = actaViewModel.nroActa
        binding.tvIdSistema.text = "ID Sistema: ${actaViewModel.idSistema}"

        // 3. Botón Nueva Acta
        binding.btnNuevaActa.setOnClickListener {
            actaViewModel.resetearActa() // O limpiarDatos(), según como lo llamaste

            if (actaViewModel.tipoActa == TipoActa.INSPECCION) {
                // --- FLUJO INSPECCIÓN ---
                // Ocultamos la barra de progreso porque la Hoja de Ruta es un paso "0"
                (activity as? MainActivity)?.findViewById<View>(R.id.progressBar)?.visibility = View.GONE
                (activity as? MainActivity)?.findViewById<View>(R.id.tvProgreso)?.visibility = View.GONE

                findNavController().navigate(R.id.action_exito_to_hojaRuta)
            } else {
                // --- FLUJO TRÁNSITO ---
                // Aseguramos que la barra sea visible y resetee al paso 1
                (activity as? MainActivity)?.findViewById<View>(R.id.progressBar)?.visibility = View.VISIBLE
                (activity as? MainActivity)?.findViewById<View>(R.id.tvProgreso)?.visibility = View.VISIBLE
                (activity as? MainActivity)?.actualizarProgreso(1)

                findNavController().navigate(R.id.action_exito_to_step1)
            }
        }

        // 4. Botón Imprimir
        binding.btnImprimir.setOnClickListener {
            // Verificamos permisos antes de hacer NADA
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.BLUETOOTH_CONNECT)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                    requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 101)
                    return@setOnClickListener
                }
            }

            // Si llega acá, tiene permisos
            Toast.makeText(context, "Iniciando impresión...", Toast.LENGTH_SHORT).show()

            val impresora = ImpresoraTermica()
            impresora.imprimirActaReal(actaViewModel) { mensaje ->
                activity?.runOnUiThread {
                    Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // El usuario dio permiso, ejecutamos la impresión automáticamente
            binding.btnImprimir.performClick()
        } else {
            Toast.makeText(context, "Se necesita permiso de Bluetooth para imprimir", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}