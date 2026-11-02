package com.chivilcoyactas

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("dni") val dni: String,
    @SerializedName("password") val pass: String,
    @SerializedName("dispositivo_id") val dispositivoId: String // 👈 Agregado para el ANDROID_ID
)


data class ReparticionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("formulario") val formulario: String?, // "TRANSITO" o "INSPECCION"
    @SerializedName("tipos_acta") val tiposActa: List<TipoActaDto>? = emptyList(),
    @SerializedName("tipos_bienes") val tiposBienes: List<TipoBienDto>? = emptyList() // 👈 NUEVO CAMPO
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("idInspector") val idInspector: Int?,
    @SerializedName("nombreInspector") val nombreInspector: String?,
    @SerializedName("legajo") val legajo: String?,
    @SerializedName("firmaBase64") val firmaBase64: String?, // 🚀 Firma del perfil del inspector
    @SerializedName("puntoEmisionId") val puntoEmisionId: Int,
    @SerializedName("puntoEmisionCodigo") val puntoEmisionCodigo: Int,
    @SerializedName("ultimaSecuencia") val ultimaSecuencia: Int,
    @SerializedName("reparticiones") val reparticiones: List<ReparticionDto>?,
    @SerializedName("actasBorrador") val actasBorrador: List<ActaBorradorDto>? = emptyList()
)

data class ActaBorradorDto(
    @SerializedName("idServer") val idServer: Int,
    @SerializedName("nroActa") val nroActa: String,
    @SerializedName("idReparticion") val idReparticion: Int,
    @SerializedName("tipoActa") val tipoActa: Int,
    @SerializedName("fecha") val fecha: String,
    @SerializedName("hora") val hora: String,
    @SerializedName("latitud") val latitud: String?,
    @SerializedName("longitud") val longitud: String?,
    @SerializedName("nombreCalle") val nombreCalle: String?,
    @SerializedName("alturaCalle") val alturaCalle: String?,
    @SerializedName("detalleReferencia") val detalleReferencia: String?,
    @SerializedName("observaciones") val observaciones: String?,
    @SerializedName("procTipoInmueble") val procTipoInmueble: Int?,
    @SerializedName("serie") val serie: String?,
    @SerializedName("anio") val anio: Int?,
    @SerializedName("secuencia") val secuencia: Int?,
    @SerializedName("faltas") val faltas: List<String>, // Lista de códigos de infracción
    @SerializedName("vinculados") val vinculados: List<VinculadoBorradorDto>,
    @SerializedName("testigos") val testigos: List<TestigoBorradorDto>? = emptyList(), // ✍️ Testigos con firma
    @SerializedName("fotos") val fotos: List<FotoBorradorDto>? = emptyList(), // 📸 Metadatos para Lazy Loading
    @SerializedName("comercio") val comercio: ComercioBorradorDto? = null,
    @SerializedName("catastro") val catastro: CatastroBorradorDto? = null
)

data class ComercioBorradorDto(
    @SerializedName("nombreComercio") val nombreComercio: String?,
    @SerializedName("nroHabilitacion") val nroHabilitacion: String?,
    @SerializedName("rubro") val rubro: String?
)

data class CatastroBorradorDto(
    @SerializedName("circ") val circ: String?,
    @SerializedName("secc") val secc: String?,
    @SerializedName("chNro") val chNro: String?,
    @SerializedName("chLet") val chLet: String?,
    @SerializedName("quinNro") val quinNro: String?,
    @SerializedName("quinLet") val quinLet: String?,
    @SerializedName("fracNro") val fracNro: String?,
    @SerializedName("fracLetra") val fracLetra: String?,
    @SerializedName("mzNro") val mzNro: String?,
    @SerializedName("mzLet") val mzLet: String?,
    @SerializedName("parcNro") val parcNro: String?,
    @SerializedName("parcLet") val parcLet: String?,
    @SerializedName("subParc") val subParc: String?,
    @SerializedName("uf") val uf: String?,
    @SerializedName("partidaMunicipal") val partidaMunicipal: String?
)

data class TestigoBorradorDto(
    @SerializedName("nombre") val nombre: String?,
    @SerializedName("apellido") val apellido: String?,
    @SerializedName("dni") val dni: String?,
    @SerializedName("ruta_firma") val rutaFirma: String?
)

data class FotoBorradorDto(
    @SerializedName("idServer") val idServer: Int,
    @SerializedName("tipo") val tipo: String, // "FOTO", "VIDEO", etc.
    @SerializedName("ruta") val ruta: String // Ruta en el servidor (ej: storage/actas/archivo.png)
)

data class VinculadoBorradorDto(
    @SerializedName("nombre") val nombre: String?,
    @SerializedName("dni") val dni: String?,
    @SerializedName("rol_codigo") val rol_codigo: String?,
    @SerializedName("calle") val calle: String?,
    @SerializedName("altura") val altura: String?,
    @SerializedName("provincia") val provincia: String?,
    @SerializedName("localidad") val localidad: String?,
    @SerializedName("cp") val cp: String?,
    @SerializedName("nro_licencia") val nro_licencia: String?,
    @SerializedName("ruta_firma") val rutaFirma: String? // ✍️ Referencia para Lazy Loading
)
