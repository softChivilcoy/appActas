package com.chivilcoyactas

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "acta_catastro",
    foreignKeys = [ForeignKey(
        entity = ActaEntity::class,
        parentColumns = ["idLocal"],
        childColumns = ["actaId"],
        onDelete = ForeignKey.CASCADE // 🚀 Limpieza automática
    )],
    indices = [Index("actaId")]
)
data class ActaCatastroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val ctCirc: String?,
    val ctSecc: String?,
    val ctChaqNro: String?,
    val ctChaqLet: String?,
    val ctQuinNro: String?,
    val ctQuinLet: String?,
    val ctFracNro: String?,
    val ctFracLetra: String?,
    val ctMzNro: String?,
    val ctMzLet: String?,
    val ctParcNro: String?,
    val ctParcLet: String?,
    val ctSubParc: String?,
    val ctUf: String?
)