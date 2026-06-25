package com.chivilcoyactas.db

import androidx.room.Embedded
import androidx.room.Relation
import com.chivilcoyactas.ActaAlcoholemiaEntity
import com.chivilcoyactas.ActaCatastroEntity
import com.chivilcoyactas.ActaComercioEntity
import com.chivilcoyactas.ActaEntity
import com.chivilcoyactas.ActaFaltasEntity
import com.chivilcoyactas.ActaInfractorEntity
import com.chivilcoyactas.ActaMediaEntity
import com.chivilcoyactas.ActaMedidaPreventivaEntity
import com.chivilcoyactas.ActaProcedimientoEntity
import com.chivilcoyactas.ActaSecuestroEntity
import com.chivilcoyactas.ActaTestigoEntity
import com.chivilcoyactas.ActaVehiculoEntity

data class ActaCompletaDb(
    @Embedded val cabecera: ActaEntity,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val infractor: ActaInfractorEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val faltas: List<ActaFaltasEntity>,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val vehiculo: ActaVehiculoEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val alcoholemia: ActaAlcoholemiaEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val medidasPreventivas: ActaMedidaPreventivaEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val secuestro: ActaSecuestroEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val procedimiento: ActaProcedimientoEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val comercio: ActaComercioEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val catastro: ActaCatastroEntity?,

    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val testigos: List<ActaTestigoEntity>,

    // 🚀 ESTO ES LO QUE ESTABA FALTANDO VINCULAR:
    @Relation(parentColumn = "idLocal", entityColumn = "actaId")
    val media: List<ActaMediaEntity> = emptyList()
)