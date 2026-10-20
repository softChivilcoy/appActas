package com.chivilcoyactas

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromCoordenadasList(value: List<Coordenada>?): String? {
        if (value == null) return null
        return gson.toJson(value)
    }

    @TypeConverter
    fun toCoordenadasList(value: String?): List<Coordenada>? {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<Coordenada>>() {}.type
        return gson.fromJson(value, listType)
    }
}