package com.chivilcoyactas

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PuntoSecuenciaDao {

    // Obtiene el registro de secuencia para un dispositivo y año específico
    @Query("SELECT * FROM punto_secuencia WHERE androidId = :androidId AND anio = :anio LIMIT 1")
    suspend fun obtenerSecuenciaActual(androidId: String, anio: Int): PuntoSecuenciaEntity?

    // Inserta o reemplaza si ya existe
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarOActualizar(secuencia: PuntoSecuenciaEntity)

    // Actualiza únicamente la secuencia para un dispositivo y año
    @Query("UPDATE punto_secuencia SET ultimaSecuencia = :nuevaSecuencia WHERE androidId = :androidId AND anio = :anio")
    suspend fun actualizarSecuencia(androidId: String, anio: Int, nuevaSecuencia: Int)
}