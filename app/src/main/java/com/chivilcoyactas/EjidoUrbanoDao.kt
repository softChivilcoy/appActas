package com.chivilcoyactas

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EjidoUrbanoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEjidoUrbano(ejido: EjidoUrbanoEntity)

    @Query("SELECT * FROM ejido_urbano WHERE id = 1 LIMIT 1")
    suspend fun obtenerEjidoUrbano(): EjidoUrbanoEntity?

    @Query("SELECT updatedAt FROM ejido_urbano WHERE id = 1 LIMIT 1")
    suspend fun obtenerUltimaFecha(): String?
}