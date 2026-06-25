package com.chivilcoyactas

import androidx.room.*

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias_inspeccion ORDER BY nombre ASC")
    suspend fun obtenerTodas(): List<CategoriaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(categorias: List<CategoriaEntity>)

    @Query("DELETE FROM categorias_inspeccion")
    suspend fun borrarTodo()
}