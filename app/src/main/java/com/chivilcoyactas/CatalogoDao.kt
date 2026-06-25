package com.chivilcoyactas

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CatalogoDao {

    // --- 1. MÉTODOS DE LIMPIEZA (VACIAR TABLAS) ---
    @Query("DELETE FROM local_tipos_faltas")
    suspend fun vaciarFaltas()

    @Query("DELETE FROM local_tipos_vehiculos")
    suspend fun vaciarVehiculos()

    @Query("DELETE FROM local_tipo_marcas")
    suspend fun vaciarMarcas()

    @Query("DELETE FROM local_tipo_modelos")
    suspend fun vaciarModelos()

    @Query("DELETE FROM local_tipo_provincias")
    suspend fun vaciarProvincias()

    @Query("DELETE FROM local_tipo_localidades")
    suspend fun vaciarLocalidades()

    @Query("DELETE FROM local_tipos_actas")
    suspend fun vaciarActas()

    @Query("DELETE FROM configuracion_quincenas")
    suspend fun vaciarQuincenas()

    // --- 2. MÉTODOS DE INSERCIÓN MASIVA ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarFaltas(lista: List<TipoFaltaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarVehiculos(lista: List<TipoVehiculoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMarcas(lista: List<TipoMarcaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarModelos(lista: List<TipoModeloEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarProvincias(lista: List<TipoProvinciaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarLocalidades(lista: List<TipoLocalidadEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarActas(lista: List<TipoActaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarQuincenas(lista: List<QuincenaEntity>)

    // --- 3. TRANSACCIÓN MAESTRA QUE VA A LLAMAR EL WORKER ---
    @Transaction
    suspend fun actualizarCatalogoCompleto(
        faltas: List<TipoFaltaEntity>,
        vehiculos: List<TipoVehiculoEntity>,
        marcas: List<TipoMarcaEntity>,
        modelos: List<TipoModeloEntity>,
        provincias: List<TipoProvinciaEntity>,
        localidades: List<TipoLocalidadEntity>,
        actas: List<TipoActaEntity>,
        quincenas: List<QuincenaEntity>
    ) {
        // Vaciamos todo primero
        vaciarFaltas()
        vaciarVehiculos()
        vaciarMarcas()
        vaciarModelos()
        vaciarProvincias()
        vaciarLocalidades()
        vaciarActas()
        vaciarQuincenas()

        // Insertamos el catálogo nuevo que bajó de Laravel
        insertarFaltas(faltas)
        insertarVehiculos(vehiculos)
        insertarMarcas(marcas)
        insertarModelos(modelos)
        insertarProvincias(provincias)
        insertarLocalidades(localidades)
        insertarActas(actas)
        insertarQuincenas(quincenas)
    }

    // --- 4. CONSULTAS DE LECTURA PARA LA UI ---

    // Trae todas las faltas completas
    @Query("SELECT * FROM local_tipos_faltas ORDER BY codigo ASC")
    suspend fun obtenerListaFaltasDirecta(): List<TipoFaltaEntity>

    @Query("SELECT * FROM local_tipos_vehiculos ORDER BY nombre ASC")
    suspend fun obtenerTiposVehiculos(): List<TipoVehiculoEntity>


    @Query("SELECT * FROM local_tipo_marcas ORDER BY nombre ASC")
    suspend fun obtenerTodasLasMarcas(): List<TipoMarcaEntity>

    @Query("SELECT * FROM local_tipo_modelos WHERE marcaid = :marcaId ORDER BY nombre ASC")
    suspend fun obtenerModelosPorMarca(marcaId: Int): List<TipoModeloEntity>

    @Query("SELECT * FROM local_tipo_provincias ORDER BY nombre ASC")
    suspend fun obtenerProvincias(): List<TipoProvinciaEntity> // O como se llame tu entidad de provincias

    @Query("SELECT * FROM local_tipo_localidades WHERE provinciaid = :provinciaId ORDER BY nombre ASC")
    suspend fun obtenerLocalidadesPorProvincia(provinciaId: Int): List<TipoLocalidadEntity>

    // 🕵️‍♂️ Corregido: SQLite no lleva el "INT" ahí. Busca directo en el rango.
    @Query("SELECT idJuzgado FROM configuracion_quincenas WHERE :dia BETWEEN diaInicio AND diaFin LIMIT 1")
    suspend fun obtenerJuzgadoPorDia(dia: Int): Int?
}