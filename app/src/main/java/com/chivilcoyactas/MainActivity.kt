package com.chivilcoyactas

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.chivilcoyactas.databinding.ActivityMainBinding
import android.view.WindowManager
import kotlin.getValue
import androidx.activity.viewModels
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder


// 👇 ESTOS SON LOS QUE NECESITÁS PARA EL WORKER 👇
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.BackoffPolicy
import androidx.work.WorkRequest
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private val actaViewModel: ActaViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantiene la pantalla encendida siempre que la app esté en primer plano
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ocultamos la ActionBar por defecto de Android para usar nuestro Header personalizado
        supportActionBar?.hide()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        // Lógica de visibilidad del Header y Progreso
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.loginFragment) {
                // En el login ocultamos todo
                binding.headerInspector.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.tvProgreso.visibility = View.GONE
            } else {
                // En los pasos del acta mostramos todo
                binding.headerInspector.visibility = View.VISIBLE
                binding.progressBar.visibility = View.VISIBLE
                binding.tvProgreso.visibility = View.VISIBLE
            }
        }

        // Configuración del botón de salida (Logout)
        binding.btnCerrarSesion.setOnClickListener {
            // Aquí podrías limpiar datos de sesión si fuera necesario
            navController.navigate(R.id.loginFragment)
        }

        // 👇 AGREGÁ ESTE BLOQUE ACÁ AL FINAL DEL ONCREATE 👇
        configurarSincronizacionCatalogos()
    }

    private fun configurarSincronizacionCatalogos() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val catalogoWorkRequest = PeriodicWorkRequestBuilder<CatalogoUpdateWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ActualizacionCatalogosDiarios",
            ExistingPeriodicWorkPolicy.KEEP,
            catalogoWorkRequest
        )
    }

    // Esta función la vas a llamar desde el LoginFragment cuando el login sea exitoso
    fun configurarSesionInspector(nombre: String) {
        binding.tvNombreInspector.text = "Inspector: $nombre"
    }

    fun actualizarProgreso(pasoActual: Int) {
        // Al usar Binding, no hace falta el findViewById
        binding.progressBar.visibility = View.VISIBLE
        binding.tvProgreso.visibility = View.VISIBLE

        val totalPasos = if (actaViewModel.tipoActa == TipoActa.INSPECCION) 7 else 9
        binding.tvProgreso.text = "Paso $pasoActual de $totalPasos"
        binding.progressBar.progress = pasoActual
        binding.progressBar.max = totalPasos
    }

    fun hideKeyboard() {
        val inputMethodManager = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        currentFocus?.let {
            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}