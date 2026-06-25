package com.chivilcoyactas // Asegurate que coincida con tu paquete

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREFS_NAME = "MuniPrefs"

    // Nombres de las llaves para no errarle al escribir
    private const val KEY_MARCA = "alco_marca"
    private const val KEY_MODELO = "alco_modelo"
    private const val KEY_SERIE = "alco_serie"

    private const val KEY_FIRMA_INSPECTOR = "firma_inspector_base64"

    /**
     * Guarda los datos del alcoholímetro en el disco (SharedPreferences)
     */
    fun guardarAlcoholimetro(context: Context, marca: String, modelo: String, serie: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_MARCA, marca)
        editor.putString(KEY_MODELO, modelo)
        editor.putString(KEY_SERIE, serie)
        editor.apply() // .apply() guarda en segundo plano, no traba la app
    }

    /**
     * Recupera los datos guardados. Si no hay nada, devuelve textos vacíos.
     */
    fun obtenerAlcoholimetro(context: Context): Map<String, String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return mapOf(
            "marca" to (prefs.getString(KEY_MARCA, "") ?: ""),
            "modelo" to (prefs.getString(KEY_MODELO, "") ?: ""),
            "serie" to (prefs.getString(KEY_SERIE, "") ?: "")
        )
    }

    /**
     * Por si necesitan limpiar los datos al desloguearse
     */
    fun limpiarDatos(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // Función para guardar la firma en Base64
    fun guardarFirmaInspector(context: Context, firmaBase64: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_FIRMA_INSPECTOR, firmaBase64)
        editor.apply()
    }

    // Función para recuperar la firma cuando estés armando el acta final o el envío a Laravel
    fun obtenerFirmaInspector(context: Context): String? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FIRMA_INSPECTOR, null)
    }

    fun limpiaFirma(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Limpiamos los datos del turno actual
        editor.remove(KEY_FIRMA_INSPECTOR)
        editor.apply()
    }
}