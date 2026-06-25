package com.chivilcoyactas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.chivilcoyactas.databinding.FragmentLoginBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlin.getValue

class LoginFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        val datosPrueba = listOf(
            CategoriaEntity(nombre = "Comercio / Local"),
            CategoriaEntity(nombre = "Vereda / Terreno"),
            CategoriaEntity(nombre = "Obra en Construcción"),
            CategoriaEntity(nombre = "Baldío"),
            CategoriaEntity(nombre = "Vehículo / Carga")
        )

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.categoriaDao().borrarTodo() // Limpiamos para no duplicar
            db.categoriaDao().insertarTodas(datosPrueba)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val dni = binding.etLoginDni.text.toString().trim()
            val pass = binding.etLoginPass.text.toString().trim()

            if (binding.etLoginDni.text.toString().isEmpty()) {
                binding.etLoginDni.error = "Campo Obligatorio"
                binding.etLoginDni.requestFocus() // 🔥 Manda el cursor acá
                return@setOnClickListener
            }

            if (binding.etLoginPass.text.toString().isEmpty()) {
                binding.etLoginPass.error = "Campo Obligatorio"
                binding.etLoginPass.requestFocus() // 🔥 Manda el cursor acá
                return@setOnClickListener
            }

            // 1. Validación de identidad (Simulada)
            if ((dni == "123" && pass == "123") || (dni == "1234" && pass == "qwe")) {

                // Seteamos el nombre según el DNI para la sesión
                val nombreInspector = if (dni == "123") "Inspector García" else "Inspector General"
                (activity as? MainActivity)?.configurarSesionInspector(nombreInspector)

                // 2. Navegamos SIEMPRE al selector de repartición
                findNavController().navigate(R.id.action_login_to_seleccionReparticion)

            } else {
                Toast.makeText(requireContext(), "DNI o Clave incorrectos", Toast.LENGTH_SHORT).show()
            }
        }

        /*val etLoginPass = view.findViewById<TextInputEditText>(R.id.etLoginPass)
        val scrollView = view.findViewById<ScrollView>(R.id.scrollLogin)

        etLoginPass.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollView?.postDelayed({
                    // En el login, lo más seguro es ir al fondo (donde está el Space)
                    // para que el botón "Ingresar" suba sí o sí.
                    scrollView.smoothScrollTo(0, etLoginPass.bottom + 500)
                }, 200)
            }
        }*/

        val etLoginPass = view.findViewById<TextInputEditText>(R.id.etLoginPass)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)

        etLoginPass.setOnEditorActionListener { _, actionId, _ ->
            // Capturamos tanto el "Done" (tilde) como el Enter físico
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED) {

                // Ejecutamos la misma acción que el botón Ingresar
                btnLogin.performClick()
                true
            } else {
                false
            }
        }
    }

    // Simulación en el LoginFragment o LoginViewModel
    /*fun iniciarSesion(usuario: String, clave: String) {
        // 1. Validar credenciales contra Laravel
        // 2. Si es exitoso, descargar las tablas maestras:
        val categoriasDesdeServidor = api.getCategoriasInspeccion() // Ej: Veredas, Comercios, etc.

        // 3. Guardar en Room para que funcione OFFLINE
        db.categoriaDao().insertarTodas(categoriasDesdeServidor)
    }*/

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}