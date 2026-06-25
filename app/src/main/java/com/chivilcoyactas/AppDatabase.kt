package com.chivilcoyactas

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room

@Database(
    entities = [
        ActaEntity::class,
        ActaInfractorEntity::class,
        ActaVehiculoEntity::class,
        ActaFaltasEntity::class,
        ActaMediaEntity::class,
        ActaAlcoholemiaEntity::class,
        ActaSecuestroEntity::class,
        ActaMedidaPreventivaEntity::class,
        ActaTestigoEntity::class,
        ActaProcedimientoEntity::class,
        ActaComercioEntity::class,
        ActaCatastroEntity::class,
        CategoriaEntity::class,
        TipoFaltaEntity::class,
        TipoVehiculoEntity::class,
        TipoMarcaEntity::class,
        TipoModeloEntity::class,
        TipoProvinciaEntity::class,
        TipoLocalidadEntity::class,
        TipoActaEntity::class,
        QuincenaEntity::class
    ],
    version = 12,
    exportSchema = false // Recomendado para proyectos simples
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun actaDao(): ActaDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun catalogoDao(): CatalogoDao // 👈 Agregamos el acceso al DAO de catálogos

    companion object {
        // @Volatile asegura que el valor de INSTANCE sea siempre actual para todos los hilos
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Si la INSTANCE no es nula, la devolvemos; si es nula, creamos la base de datos
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chivilcoy_actas_db" // Nombre del archivo .db en el dispositivo
                )
                    .fallbackToDestructiveMigration() // Útil en desarrollo: si cambias las entities, borra y recrea la DB
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}