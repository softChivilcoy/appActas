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
import kotlinx.coroutines.withContext

enum class TipoFormulario { TRANSITO, INSPECCION }

data class Infraccion(val id: Int = 0,val codigo: String, val nombre: String)

// 🕵️ El cambio clave: heredamos de AndroidViewModel pasándole 'application'
class ActaViewModel(application: Application) : AndroidViewModel(application) {

    // Lista maestra cargada desde la DB local al inicio

    var modoSecuestroDerivado: Boolean
        get() {
            TODO()
        }
        set(value) {}
    var firmaInfractorPath: String? = null
    var firmaTestigoPath: String? = null
    var infractorSeNiegaAFirmar: Boolean = false
    var procTipoInmueble: String = ""
    var idActaLocal: Long = 0L

    // Instanciamos Room usando el 'application' que ahora sí tenemos disponible
    private val db = AppDatabase.getDatabase(application)
    private val catalogoDao = db.catalogoDao()

    var tipoFormulario: TipoFormulario = TipoFormulario.TRANSITO

    // Este bloque ahora va a compilar en verde perfecto 🚀
    val todasLasFaltas = liveData(viewModelScope.coroutineContext) {
        emit(catalogoDao.obtenerListaFaltasDirecta())
    }

    var listaReparticiones: List<ReparticionDto> = emptyList()
    //var reparticionActual: String = ""


    // 2. ID de la repartición seleccionada (AGREGAR ESTA LÍNEA)
    var idReparticionSeleccionada: Int? = null
    var idTipoActaSeleccionada: Int? = null // tipoacta / tipo_acta_id real (1, 2, 3...)


    // 1. Cargamos de Room los catálogos para vehículos
    val todosLosTiposVehiculo = androidx.lifecycle.liveData(viewModelScope.coroutineContext) {
        emit(catalogoDao.obtenerTiposVehiculos()) // Asegurate de que devuelva List<TipoVehiculoEntity>
    }

    val todasLasMarcas = androidx.lifecycle.liveData(viewModelScope.coroutineContext) {
        emit(db.catalogoDao().obtenerTodasLasMarcas()) // Agregá este método a tu DAO si falta
    }


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

    var listaTiposActaDisponibles: List<TipoActaDto> = emptyList()


    var tipoActa: Int? = null
    var nroActa: String = ""
    var idSistema: String = ""

    var codigoValidacion: String = ""

    var idHojaRuta: Int? = null
    var nro: String = ""
    var nombreInfractor: String = ""
    var esNuevaActa: Boolean = false

    //-------------------------------------------
    //LOGIN

    var secuenciaActual: Int = 0
    var androidId: String = ""
    var serie: String = "E"
    var anio: Int = java.time.Year.now().value
    var puntoEmisionId: Int = 0 // Si Laravel te devuelve el ID del punto de emisión en el Login


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
    var idTipoVehiculoSeleccionado: Int? = null

    var dominio: String = ""
    var marca: String = ""
    var idMarcaSeleccionada: Int? = null

    var modelo: String = ""
    var idModeloSeleccionado: Int? = null

    var esPropietario: Boolean = true

    var procedimientoVeh: String = ""

    // ---- PASO 5 : ALCOHOLEMIA --

    var hacerTestAlcoholemia: Boolean = false

    var alcoholimetroId: Int? = null
    var alcoMarca: String? = ""
    var alcoModelo: String? = ""
    var alcoSerie: String? = ""
    var alcoAprobacion: String? = ""
    var ftResultado: Double = 0.0
    var seAdjuntaPlanillaMedica: Boolean = false

    var retencionVehiculo: Boolean = false

    var retencionLicencia: Boolean = false
    var retencionAnimal: Boolean = false

    //SECUESTRO

    var tipoVehiculoIdSelected: Int = 0 // El ID numérico del vehículo seleccionado en la pantalla previa (ej. Auto=1, Moto=2)
    val inventarioDinamico = mutableMapOf<String, String>() // Key: codigoClave -> Value: Respuesta ingresada

    // ---- PASO 6 : ALCOHOLEMIA --
    var inventarioSecuestro = mutableMapOf<String, String>()
    // Guardará algo como: "Batería" -> "S-B" (Presente y Bueno)
    var incluyoInterior: Boolean = false


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

    // Mantenemos la lista como LiveData de List<Bitmap> (inmutable hacia afuera)
    private val _listaFotos = MutableLiveData<List<Bitmap>>(emptyList())
    val listaFotos: LiveData<List<Bitmap>> get() = _listaFotos

    // 🚀 1. Para cargar las fotos recuperadas de la BD al abrir un Borrador
    fun setListaFotos(nuevasFotos: List<Bitmap>) {
        _listaFotos.value = nuevasFotos
    }

    // 🚀 2. Eliminar foto creando una nueva copia para notificar a la UI
    fun eliminarFoto(index: Int) {
        val listaActual = _listaFotos.value?.toMutableList() ?: return
        if (index in listaActual.indices) {
            listaActual.removeAt(index)
            _listaFotos.value = listaActual // Emite la nueva lista mutable modificada
        }
    }

    // 🚀 3. Agregar foto desde la cámara
    fun agregarFotoDesdeUri(context: Context, uri: android.net.Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmapReal = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmapReal != null) {
                val listaActual = _listaFotos.value?.toMutableList() ?: mutableListOf()
                listaActual.add(bitmapReal)
                _listaFotos.value = listaActual // Forzamos a LiveData a emitir el cambio
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /*fun agregarFoto(bitmap: Bitmap) {
        // Redimensionamos a un tamaño razonable para un acta (ej: 1024px el lado más largo)
        val width = 1024
        val height = (bitmap.height * (width.toDouble() / bitmap.width)).toInt()
        val bitmapReducido = Bitmap.createScaledBitmap(bitmap, width, height, true)

        val listaActual = _listaFotos.value ?: mutableListOf()
        listaActual.add(bitmapReducido)
        _listaFotos.value = listaActual
    }*/

    fun obtenerAlcoholimetros(context: Context, callback: (List<AlcoholimetrosEntity>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context) // Adaptá según el nombre de tu BD
            val lista = db.catalogoDao().obtenerAlcoholimetros()
            withContext(Dispatchers.Main) {
                callback(lista)
            }
        }
    }

    /**
     * Limpia los datos para empezar un acta de cero
     */
    fun resetearActa() {
        idActaLocal = 0L
        secuenciaActual = 0
        serie = "E"
        anio = 0

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
        tipoVehiculoIdSelected = 0
        inventarioDinamico.clear()
        inventarioSecuestro.clear()
        incluyoInterior = false
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