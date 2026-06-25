package com.chivilcoyactas

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

// 👇 AGREGÁ ESTOS IMPORTS ESPECÍFICOS DE LIFECYCLE 👇
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class TipoActa { TRANSITO, INSPECCION }

data class Infraccion(val id: Int = 0,val codigo: String, val nombre: String)

// 🕵️ El cambio clave: heredamos de AndroidViewModel pasándole 'application'
class ActaViewModel(application: Application) : AndroidViewModel(application) {

    // Lista maestra cargada desde la DB local al inicio

    // Instanciamos Room usando el 'application' que ahora sí tenemos disponible
    private val db = AppDatabase.getDatabase(application)
    private val catalogoDao = db.catalogoDao()

    // Este bloque ahora va a compilar en verde perfecto 🚀
    val todasLasFaltas = liveData(viewModelScope.coroutineContext) {
        emit(catalogoDao.obtenerListaFaltasDirecta())
    }

    // 1. Cargamos de Room los catálogos para vehículos
    val todosLosTiposVehiculo = androidx.lifecycle.liveData(viewModelScope.coroutineContext) {
        emit(catalogoDao.obtenerTiposVehiculos()) // Asegurate de que devuelva List<TipoVehiculoEntity>
    }

    val todasLasMarcas = androidx.lifecycle.liveData(viewModelScope.coroutineContext) {
        emit(db.catalogoDao().obtenerTodasLasMarcas()) // Agregá este método a tu DAO si falta
    }

    // 2. Variables para guardar los IDs reales elegidos para mandar a Laravel
    var idTipoVehiculoSeleccionado: Int = 0
    var idMarcaSeleccionada: Int = 0
    var idModeloSeleccionado: Int = 0

    // 1. Exponemos las provincias para el primer combo
    val todasLasProvincias = androidx.lifecycle.liveData(viewModelScope.coroutineContext) {
        emit(catalogoDao.obtenerProvincias())
    }

    var Testigo1Provincia: String = "BUENOS AIRES"
    var Testigo2Provincia: String = "BUENOS AIRES"

    var fecha: String = ""
    var hora: String = ""
    var catalogoCategorias: List<CategoriaEntity> = emptyList()

    // Lo que el inspector selecciona en el acta actual
    var categoriasSeleccionadas = mutableListOf<String>()

    // Agregá esta línea:
    var reparticionActual: String = ""

    var tipoActa: TipoActa = TipoActa.TRANSITO // Por defecto
    var nroActa: String = ""
    var idSistema: String = ""

    var codigoValidacion: String = ""

    var idHojaRuta: Int? = null
    var nro: String = ""
    var nombreInfractor: String = ""
    var esNuevaActa: Boolean = false

    // --- PASO 1: UBICACIÓN ---
    var esOperativo: Boolean = false
    var ejidoUrbano: Int = -1 // -1 = Sin seleccionar, 0 = DENTRO, 1 = FUERA

    var calle: String = ""
    var altura: Int? = null
    var ubTieneDetalleAdicional: Boolean = false
    var ubDepto: String = ""
    var ubReferencia: String = ""
    var latitud: Double = 0.0
    var longitud: Double = 0.0

    // --- PASO 2: FALTAS --

    //var listaFaltasSeleccionadas: MutableList<String> = mutableListOf()
    var listaFaltasSeleccionadas: MutableList<Infraccion> = mutableListOf()
    var ftRetencion: Boolean = false
    var ftObservaciones: String = ""

    //-----------------------------------
    //PROCEDIMIENTO

    var procAccion: String = ""
    var procCheckComercio: Boolean = false
    var procCheckInmueble: Boolean = false
    var procCheckVehiculo: Boolean = false
    var procRefActa: String = ""
    var procSeProcedeA: String = ""

    var comNombreFantasia: String = ""
    var comHabNumero: String = ""
    var comRubro: String = ""

    var catCirc: String = ""
    var catSeccion: String = ""
    var catChacraNro: String = ""
    var catChacraLet: String = ""
    var catQuintaNro: String = ""
    var catQuintaLet: String = ""
    var catFraccionNro: String = ""
    var catFraccionLet: String = ""
    var catManzanaNro: String = ""
    var catManzanaLet: String = ""
    var catParcelaNro: String = ""
    var catParcelaLet: String = ""
    var catSubparcela: String = ""
    var catUF: String = ""

    // --- PASO 3: INFRACTOR ---
    var niegaDatos: Int = -1 // -1 = Sin seleccionar, 0 = Aporta, 1 = Niega, 2 = No se encuentra
    var motivoDatos: String = ""
    var ApellidoNombreInfractor: String = ""
    var dniInfractor: String = ""
    var tieneLicencia: Boolean = false
    var nroLicencia: String = ""
    var calleInfractor: String = ""
    var alturaInfractor: String = ""
    var provinciaInfractor: String = "BUENOS AIRES"
    var localidadInfractor: String = "CHIVILCOY"
    var cpInfractor: String = "6620"

    var vinculoLugar: String = "Propietario"

    var nombreResponsable: String = ""
    var dniResponsable: String = ""
    var calleResponsable: String = ""
    var alturaResponsable: String = ""

    // --- PASO 4: VEHÍCULO ---
    var tipoVehiculo: String = ""
    var dominio: String = ""
    var marca: String = ""
    var modelo: String = ""
    var esPropietario: Boolean = true

    var procedimientoVeh: String = ""

    // ---- PASO 5 : ALCOHOLEMIA --

    var hacerTestAlcoholemia: Boolean = false
    var alcoMarca: String = ""
    var alcoModelo: String = ""
    var alcoSerie: String = ""
    var alcoAprobacion: String = ""
    var ftResultado: Double = 0.0
    var seAdjuntaPlanillaMedica: Boolean = false

    var retencionVehiculo: Boolean = false

    var retencionLicencia: Boolean = false
    var retencionAnimal: Boolean = false

    // ---- PASO 6 : ALCOHOLEMIA --
    var inventarioSecuestro = mutableMapOf<String, String>()
    // Guardará algo como: "Batería" -> "S-B" (Presente y Bueno)
    var incluyoInterior: Boolean = false
    var numeroMotor: String = ""
    var numeroChasis: String = ""
    var estadoCentral: String = ""


    //  --- PASO 7 : TESTIGOS --
    var Testigo1Dni: String = ""
    var Testigo1Nombre: String = ""
    var Testigo1Domicilio: String = ""
    var Testigo1Localidad: String = ""
    var Testigo1Cp: String = ""

    var Testigo2Dni: String = ""
    var Testigo2Nombre: String = ""
    var Testigo2Domicilio: String = ""
    var Testigo2Localidad: String = ""
    var Testigo2Cp: String = ""


    // ---- PASO 8 : FIRMA --

    var firmaTestigo: Bitmap? = null

    var nombreTestigo: String? = null

    var procedimiento: String = ""

    var seNiegaAFirmar: Boolean = false

    var firmaInfractor: Bitmap? = null

    // ---- PASO 9 : FIRMA --
    var observaciones: String = ""
    //var fotosRutas: MutableList<String> = mutableListOf() // Rutas de las fotos en el celu

    // Lista persistente de fotos
    private val _listaFotos = MutableLiveData<MutableList<Bitmap>>(mutableListOf())
    val listaFotos: LiveData<MutableList<Bitmap>> get() = _listaFotos

    fun agregarFoto(bitmap: Bitmap) {
        // Redimensionamos a un tamaño razonable para un acta (ej: 1024px el lado más largo)
        val width = 1024
        val height = (bitmap.height * (width.toDouble() / bitmap.width)).toInt()
        val bitmapReducido = Bitmap.createScaledBitmap(bitmap, width, height, true)

        val listaActual = _listaFotos.value ?: mutableListOf()
        listaActual.add(bitmapReducido)
        _listaFotos.value = listaActual
    }

    fun eliminarFoto(index: Int) {
        val listaActual = _listaFotos.value ?: mutableListOf()
        if (index in listaActual.indices) {
            listaActual.removeAt(index)
            _listaFotos.value = listaActual
        }
    }

    fun agregarFotoDesdeUri(context: Context, uri: android.net.Uri) {
        try {
            // Leemos el archivo real que guardó la cámara
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmapReal = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmapReal != null) {
                val listaActual = _listaFotos.value ?: mutableListOf()

                // 🚀 NO REDUCIMOS A 1024 ACÁ. Dejamos que mantenga la resolución nativa alta.
                listaActual.add(bitmapReal)
                _listaFotos.value = listaActual
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * Limpia los datos para empezar un acta de cero
     */
    fun resetearActa() {
        nroActa = ""
        idSistema = ""
        codigoValidacion = ""
        fecha = ""
        hora = ""

        // MODIFICACIÓN ACÁ:
        // Si NO es un operativo, limpiamos la calle.
        // Si ES un operativo, la dejamos para la próxima acta.
        if (!esOperativo) {
            calle = ""
            altura = 0
            ubTieneDetalleAdicional = false
            ubDepto = ""
            ubReferencia = ""
            // Si querés que también guarde la lat/long del operativo:
            latitud = 0.0
            longitud = 0.0
        }



        listaFaltasSeleccionadas.clear()
        ftRetencion = false
        ftObservaciones = ""
        procAccion = ""
        procCheckComercio = false
        procCheckInmueble = false
        procCheckVehiculo = false
        comNombreFantasia = ""
        comHabNumero = ""
        comRubro = ""
        catCirc = ""
        catSeccion = ""
        catChacraNro= ""
        catChacraLet = ""
        catQuintaNro = ""
        catQuintaLet = ""
        catFraccionNro = ""
        catFraccionLet = ""
        catManzanaNro = ""
        catManzanaLet = ""
        catParcelaNro = ""
        catParcelaLet = ""
        catSubparcela = ""
        catUF = ""
        procRefActa = ""
        procSeProcedeA = ""
        niegaDatos = -1
        motivoDatos = ""
        ApellidoNombreInfractor = ""
        dniInfractor = ""
        tieneLicencia = false
        nroLicencia = ""
        provinciaInfractor = "BUENOS AIRES"
        localidadInfractor = "CHIVILCOY"
        cpInfractor = "6620"
        calleInfractor = ""
        alturaInfractor = ""
        vinculoLugar = "Propietario"
        nombreResponsable = ""
        dniResponsable = ""
        calleResponsable = ""
        alturaResponsable = ""
        tipoVehiculo = ""
        dominio = ""
        marca = ""
        modelo = ""
        esPropietario = true
        procedimientoVeh = ""
        procedimiento = ""
        hacerTestAlcoholemia = false
        alcoMarca = ""
        alcoModelo = ""
        alcoSerie = ""
        alcoAprobacion = ""
        ftResultado = 0.0
        seAdjuntaPlanillaMedica = false
        retencionVehiculo = false
        retencionLicencia = false
        retencionAnimal = false
        inventarioSecuestro.clear()
        incluyoInterior = false
        numeroMotor = ""
        numeroChasis = ""
        estadoCentral = ""
        Testigo1Dni = ""
        Testigo1Nombre = ""
        Testigo1Domicilio = ""
        Testigo1Localidad = ""
        Testigo1Cp = ""
        Testigo2Dni = ""
        Testigo2Nombre = ""
        Testigo2Domicilio = ""
        Testigo2Localidad = ""
        Testigo2Cp = ""
        seNiegaAFirmar = false
        firmaTestigo = null
        nombreTestigo = ""
        firmaInfractor = null
        observaciones = ""
        // Para limpiar un LiveData de tipo MutableList:
        _listaFotos.value = mutableListOf()
        //fotosRutas.clear()
    }
}