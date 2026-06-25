package com.chivilcoyactas

import androidx.room.*
import com.chivilcoyactas.db.ActaCompletaDb

@Dao
interface ActaDao {

    // --- INSERCIONES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCabecera(acta: ActaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarInfractor(infractor: ActaInfractorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarFaltas(faltas: List<ActaFaltasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarVehiculo(vehiculo: ActaVehiculoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarAlcoholemia(alcoholemia: ActaAlcoholemiaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMedidasPreventivas(medidas: ActaMedidaPreventivaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarSecuestro(secuestro: ActaSecuestroEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTestigos(testigos: List<ActaTestigoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMedia(media: List<ActaMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarProcedimiento(procedimiento: ActaProcedimientoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarComercio(comercio: ActaComercioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCatastro(catastro: ActaCatastroEntity)

    // --- CONSULTAS ---

    @Transaction
    @Query("SELECT * FROM actas ORDER BY idLocal DESC")
    suspend fun obtenerTodasLasActas(): List<ActaCompleta>

    @Transaction
    @Query("SELECT * FROM actas WHERE idLocal = :id")
    suspend fun obtenerActaPorId(id: Long): ActaCompleta?

    @Transaction
    @Query("SELECT * FROM actas WHERE estadoEnvio = 'PENDIENTE'")
    suspend fun obtenerActasPendientes(): List<ActaCompleta>

    @Transaction
    @Query("SELECT * FROM actas WHERE idLocal = :actaId")
    suspend fun obtenerActaCompleta(actaId: Long): ActaCompletaDb?

    @Query("SELECT * FROM actas WHERE estadoEnvio = :estado")
    suspend fun obtenerActasPorEstado(estado: String): List<ActaEntity>

    // --- ACTUALIZACIONES ---

    @Query("UPDATE actas SET estadoEnvio = :nuevoEstado, idServer = :idServer WHERE idLocal = :idLocal")
    suspend fun actualizarEstadoEnvio(idLocal: Long, nuevoEstado: String, idServer: Long?)

    // --- ELIMINACIÓN ---

    @Query("DELETE FROM actas WHERE idLocal = :idLocal")
    suspend fun eliminarActaLocalCompleta(idLocal: Long)

    @Delete
    suspend fun eliminarActa(acta: ActaEntity)
}