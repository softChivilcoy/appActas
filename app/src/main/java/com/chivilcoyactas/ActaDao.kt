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
    suspend fun insertarInfractores(infractores: List<ActaInfractorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarFaltas(faltas: List<ActaFaltasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarVehiculo(vehiculo: ActaVehiculoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarAlcoholemia(alcoholemia: ActaAlcoholemiaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMedidasPreventivas(medidas: ActaMedidaPreventivaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarBienesRetenidos(bienes: List<ActaBienRetenidoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarSecuestro(secuestro: List<ActaSecuestroEntity>)

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
    @Query("SELECT * FROM actas ORDER BY fecha DESC, hora DESC")
    suspend fun obtenerTodasLasActas(): List<ActaCompleta>

    @Transaction
    @Query("SELECT * FROM actas WHERE idLocal = :id")
    suspend fun obtenerActaPorId(id: Long): ActaCompleta?

    @Transaction
    @Query("SELECT * FROM actas WHERE estadoEnvio IN ('PENDIENTE', 'ENVIADA', 'BORRADOR')")
    suspend fun obtenerActasPendientes(): List<ActaCompleta>

    @Transaction
    @Query("SELECT * FROM actas WHERE idLocal = :actaId")
    suspend fun obtenerActaCompleta(actaId: Long): ActaCompletaDb?

    @Query("SELECT * FROM actas WHERE estadoEnvio = :estado")
    suspend fun obtenerActasPorEstado(estado: String): List<ActaEntity>

    @Query("SELECT tipoBienId FROM acta_bienes_retenidos WHERE actaId = :actaId")
    suspend fun obtenerBienesPorActa(actaId: Long): List<Int>

    // --- ACTUALIZACIONES ---

    @Query("UPDATE actas SET estadoEnvio = :nuevoEstado, idServer = :idServer WHERE idLocal = :idLocal")
    suspend fun actualizarEstadoEnvio(idLocal: Long, nuevoEstado: String, idServer: Long?)

    @Query("SELECT idLocal FROM actas WHERE idServer = :idServer LIMIT 1")
    suspend fun buscarIdLocalPorServer(idServer: Int): Long?

    @Query("DELETE FROM actas WHERE idServer = :idServer")
    suspend fun eliminarPorIdServer(idServer: Int)

    // --- ELIMINACIÓN ---

    @Query("DELETE FROM acta_infractor WHERE actaId = :actaId")
    suspend fun eliminarInfractoresPorActa(actaId: Long)

    @Query("DELETE FROM acta_faltas WHERE actaId = :actaId")
    suspend fun eliminarFaltasPorActa(actaId: Long)

    @Query("DELETE FROM acta_vehiculo WHERE actaId = :actaId")
    suspend fun eliminarVehiculoPorActa(actaId: Long)

    @Query("DELETE FROM acta_alcoholemia WHERE actaId = :actaId")
    suspend fun eliminarAlcoholemiaPorActa(actaId: Long)

    @Query("DELETE FROM acta_medidas_preventivas WHERE actaId = :actaId")
    suspend fun eliminarMedidasPreventivasPorActa(actaId: Long)

    @Query("DELETE FROM acta_bienes_retenidos WHERE actaId = :actaId")
    suspend fun eliminarBienesRetenidosPorActa(actaId: Long)

    @Query("DELETE FROM acta_secuestros WHERE actaId = :actaId")
    suspend fun eliminarSecuestrosPorActa(actaId: Long)

    @Query("DELETE FROM acta_testigos WHERE actaId = :actaId")
    suspend fun eliminarTestigosPorActa(actaId: Long)

    @Query("DELETE FROM acta_procedimiento WHERE actaId = :actaId")
    suspend fun eliminarProcedimientoPorActa(actaId: Long)

    @Query("DELETE FROM acta_comercio WHERE actaId = :actaId")
    suspend fun eliminarComercioPorActa(actaId: Long)

    @Query("DELETE FROM acta_catastro WHERE actaId = :actaId")
    suspend fun eliminarCatastroPorActa(actaId: Long)

    @Query("DELETE FROM acta_media WHERE actaId = :actaId")
    suspend fun eliminarMediaPorActaId(actaId: Long)

    @Query("DELETE FROM actas WHERE idLocal = :idLocal")
    suspend fun eliminarActaLocalCompleta(idLocal: Long)

    // 🚀 ACTUALIZACIONES PARA LAZY LOADING
    @Query("UPDATE acta_media SET rutaArchivo = :rutaLocal WHERE id = :idMedia")
    suspend fun actualizarRutaLocalMedia(idMedia: Long, rutaLocal: String)

    @Query("UPDATE acta_infractor SET firmaPath = :rutaLocal WHERE id = :idInfractor")
    suspend fun actualizarRutaFirmaInfractor(idInfractor: Long, rutaLocal: String)

    @Query("UPDATE acta_testigos SET firmaPath = :rutaLocal WHERE id = :idTestigo")
    suspend fun actualizarRutaFirmaTestigo(idTestigo: Long, rutaLocal: String)

    // 🚀 Transacción para guardar/actualizar la sección de inspección completa
    @Transaction
    suspend fun guardarModuloInspeccion(
        cabecera: ActaEntity,
        procedimiento: ActaProcedimientoEntity?,
        comercio: ActaComercioEntity?,
        catastro: ActaCatastroEntity?
    ) {
        val idActa = insertarCabecera(cabecera)

        procedimiento?.let {
            insertarProcedimiento(it.copy(actaId = idActa))
        }

        if (comercio != null && !comercio.esVacio()) {
            insertarComercio(comercio.copy(actaId = idActa))
        }

        if (catastro != null && !catastro.esVacio()) {
            insertarCatastro(catastro.copy(actaId = idActa))
        }
    }
}