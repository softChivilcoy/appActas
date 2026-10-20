package com.chivilcoyactas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chivilcoyactas.databinding.FragmentLoginBinding
import com.chivilcoyactas.net.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class LoginFragment : Fragment() {

    private val actaViewModel: ActaViewModel by activityViewModels()

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🚀 Habilitamos selección total de texto y autoscroll al enfocar DNI o Pass
     //   setupAutoscrollYSeleccion()

        binding.etLoginDni.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.scrollLogin.post {
                    binding.scrollLogin.smoothScrollTo(0, binding.etLoginDni.top)
                }
            }
        }

        binding.etLoginPass.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.scrollLogin.post {
                    binding.scrollLogin.smoothScrollTo(0, binding.etLoginPass.top)
                }
            }
        }

        binding.etLoginPass.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                (activity as? MainActivity)?.hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.btnLogin.setOnClickListener {
            val dni = binding.etLoginDni.text.toString().trim()
            val pass = binding.etLoginPass.text.toString().trim()

            if (dni.isEmpty()) {
                binding.etLoginDni.error = "Campo Obligatorio"
                binding.etLoginDni.requestFocus()
                return@setOnClickListener
            }

            if (pass.isEmpty()) {
                binding.etLoginPass.error = "Campo Obligatorio"
                binding.etLoginPass.requestFocus()
                return@setOnClickListener
            }

            // Hacemos el Login Real contra Laravel
            ejecutarLoginHttp(dni, pass)
        }

        binding.etLoginPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED) {
                binding.btnLogin.performClick()
                true
            } else {
                false
            }
        }
    }


    // 📌 Función que maneja la selección total al pisar el campo y el desplazamiento suave
    private fun setupAutoscrollYSeleccion() {
        val inputs = listOf(binding.etLoginDni, binding.etLoginPass)

        inputs.forEach { editText ->
            val previousListener = editText.onFocusChangeListener

            editText.setOnFocusChangeListener { view, hasFocus ->
                previousListener?.onFocusChange(view, hasFocus)

                if (hasFocus) {
                    // 1. Si hay texto escrito (ej. error en legajo o pass), se selecciona todo de inmediato para escribir encima
                    if (!editText.text.isNullOrEmpty()) {
                        editText.post { editText.selectAll() }
                    }

                    // 2. Scroll suave sobre el ScrollView por si el teclado de pantalla lo solapa
                    view.postDelayed({
                        try {
                            val parentInput = (view.parent.parent as? com.google.android.material.textfield.TextInputLayout)
                                ?: (view.parent as? com.google.android.material.textfield.TextInputLayout)
                                ?: view

                            val rect = Rect()
                            parentInput.getDrawingRect(rect)
                            binding.scrollLogin.offsetDescendantRectToMyCoords(parentInput, rect)

                            val targetY = (rect.top).coerceAtLeast(0)
                            binding.scrollLogin.smoothScrollTo(0, targetY)
                        } catch (e: Exception) {
                            Log.e("LOGIN_SCROLL", "Error calculando scroll: ${e.message}")
                        }
                    }, 150)
                }
            }
        }
    }

    private fun obtenerAndroidId(): String {
        return Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "DESCONOCIDO"
    }

    private fun ejecutarLoginHttp(dni: String, pass: String) {
        binding.btnLogin.isEnabled = false
        val androidId = obtenerAndroidId()

        lifecycleScope.launch {
            try {
                // Pasamos el androidId al LoginRequest
                val response = RetrofitClient.apiService.login(LoginRequest(dni, pass, androidId))

                if (response.isSuccessful && response.body()?.success == true) {
                    val loginData = response.body()!!

                    val prefs = requireContext().getSharedPreferences("SesionInspector", Context.MODE_PRIVATE)

                    val puntoId = loginData.puntoEmisionId ?: 0
                    val puntoCodigo = loginData.puntoEmisionCodigo ?: 0
                    val ultimaSecuenciaServidor = loginData.ultimaSecuencia ?: 0

                    prefs.edit().apply {
                        putInt("INSPECTOR_ID", loginData.idInspector ?: -1)
                        putString("INSPECTOR_NOMBRE", loginData.nombreInspector ?: "Inspector")
                        putString("INSPECTOR_LEGAJO", loginData.legajo ?: "")
                        putString("DEVICE_ID", androidId)
                        putInt("PUNTO_EMISION_ID", puntoId)
                        putInt("PUNTO_EMISION_CODIGO", puntoCodigo)
                        apply()
                    }

                    val nombreInspector = loginData.nombreInspector ?: "Inspector"
                    (activity as? MainActivity)?.configurarSesionInspector(nombreInspector)

                    actaViewModel.listaReparticiones = loginData.reparticiones ?: emptyList()
                    actaViewModel.androidId = androidId
                    actaViewModel.puntoEmisionId = puntoId

                    // 🚀 SINCRONIZACIÓN DE SECUENCIA LOCAL EN ROOM
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(requireContext())
                        val puntoSecuenciaDao = db.puntoSecuenciaDao()
                        val anioActual = Calendar.getInstance().get(Calendar.YEAR)

                        // Consultar la secuencia actual guardada en el teléfono
                        val secuenciaLocal = puntoSecuenciaDao.obtenerSecuenciaActual(
                            androidId = androidId,
                            anio = anioActual
                        )

                        if (secuenciaLocal == null) {
                            // Caso 1: Celular nuevo / app reinstalada -> Se inicializa con el servidor
                            puntoSecuenciaDao.insertarOActualizar(
                                PuntoSecuenciaEntity(
                                    androidId = androidId,
                                    serie = "E",
                                    puntoCodigo = puntoCodigo,
                                    anio = anioActual,
                                    ultimaSecuencia = ultimaSecuenciaServidor
                                )
                            )
                        } else if (ultimaSecuenciaServidor > secuenciaLocal.ultimaSecuencia) {
                            // Caso 2: El servidor está más adelantado -> Se actualiza el celular
                            puntoSecuenciaDao.actualizarSecuencia(
                                androidId = androidId,
                                anio = anioActual,
                                nuevaSecuencia = ultimaSecuenciaServidor
                            )
                        }
                        // Caso 3: Si la secuencia local es mayor o igual, se conserva intacta
                        // para no perder actas tomadas offline aún no subidas.
                    }

                    dispararSincronizacionCatalogos()
                    findNavController().navigate(R.id.action_login_to_seleccionReparticion)

                } else {
                    // 🔍 Extraemos el mensaje de error del errorBody() en respuestas 401/403
                    var errorMsg = "Credenciales inválidas"
                    var errorCode = ""

                    try {
                        val errorJsonString = response.errorBody()?.string()
                        if (!errorJsonString.isNullOrEmpty()) {
                            val jsonObject = org.json.JSONObject(errorJsonString)
                            if (jsonObject.has("message")) errorMsg = jsonObject.getString("message")
                            if (jsonObject.has("error_code")) errorCode = jsonObject.getString("error_code")
                        }
                    } catch (e: Exception) {
                        errorMsg = "Error en la autenticación"
                    }

                    if (response.code() == 403 || errorMsg.contains("dispositivo", ignoreCase = true) || errorMsg.contains("serie", ignoreCase = true)) {
                        mostrarDialogoDispositivoNoRegistrado(androidId, errorMsg)
                    } else {
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()

                        // 🎯 LÓGICA DE FOCO SEGÚN EL ERROR REAL
                        when {
                            // Caso 1: El legajo/DNI no existe
                            errorCode == "LEGAJO_NOT_FOUND" || errorMsg.contains("legajo", ignoreCase = true) || errorMsg.contains("usuario", ignoreCase = true) -> {
                                binding.etLoginDni.error = errorMsg
                                focarYSeleccionarCampo(binding.etLoginDni)
                            }
                            // Caso 2: La contraseña es incorrecta (o por defecto ante 401)
                            else -> {
                                binding.etLoginPass.error = errorMsg
                                focarYSeleccionarCampo(binding.etLoginPass)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión con el servidor local", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnLogin.isEnabled = true
            }
        }
    }
    private fun mostrarDialogoDispositivoNoRegistrado(androidId: String, mensajeServidor: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Dispositivo No Autorizado")
            .setMessage("$mensajeServidor\n\nIdentificador de este equipo:\n$androidId")
            .setPositiveButton("Copiar ID") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Android ID", androidId)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "ID copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun dispararSincronizacionCatalogos() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<CatalogoUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
            "SincronizarCatalogosMaestros",
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }

    private fun focarYSeleccionarCampo(editText: com.google.android.material.textfield.TextInputEditText) {
        editText.requestFocus()
        editText.post {
            editText.selectAll()
            // Aseguramos que el teclado blando se muestre si estaba cerrado
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}