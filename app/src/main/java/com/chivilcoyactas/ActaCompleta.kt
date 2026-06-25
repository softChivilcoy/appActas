package com.chivilcoyactas

import androidx.room.Embedded
import androidx.room.Relation

data class ActaCompleta(
    @Embedded val acta: ActaEntity,

    @Relation(
        parentColumn = "idLocal",
        entityColumn = "actaId"
    )
    val infractor: ActaInfractorEntity,

    @Relation(
        parentColumn = "idLocal",
        entityColumn = "actaId"
    )
    val vehiculo: ActaVehiculoEntity?, // Puede ser nulo en Inspección

    @Relation(
        parentColumn = "idLocal",
        entityColumn = "actaId"
    )
    val faltas: List<ActaFaltasEntity>, // Lista porque puede haber varias

    @Relation(
        parentColumn = "idLocal",
        entityColumn = "actaId"
    )
    val multimedia: List<ActaMediaEntity>
)
