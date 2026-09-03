package com.dani.lector.datos

import com.dani.lector.red.SerieWiki

/**
 * Una era que se te ofrece al crear una lista.
 *
 * Puede venir de dos sitios muy distintos y la diferencia importa:
 *  - [verificada] = true: es una etiqueta de la wiki ("The New 52"). Es un dato.
 *  - [verificada] = false: es una edad derivada del año de debut ("Edad de
 *    Plata"). Es una convencion de aficionados. Ver [Edades].
 *
 * En la pantalla se distinguen, para no vender como dato lo que es un apaño.
 */
data class OpcionEra(
    val nombre: String,
    val series: List<SerieWiki>,
    val verificada: Boolean
) {
    val desde: Int? get() = series.mapNotNull { it.anio }.minOrNull()
    val hasta: Int? get() = series.mapNotNull { it.anioFin }.maxOrNull()
    val enCurso: Boolean get() = series.any { it.enCurso }

    /**
     * Si todas las series de la era son un volumen 1.
     *
     * Esto es lo que se puede decir sobre "es buen momento para entrar" SIN
     * pedirle opinion a nadie: un relanzamiento en el que todo arranca en el
     * numero 1 es un punto de entrada limpio, y eso se cuenta, no se juzga.
     * El veredicto del modelo va aparte y etiquetado como opinion.
     */
    val todasDesdeElUno: Boolean get() = series.isNotEmpty() && series.all { it.volumen == 1 }

    /** "desde 2024 · 8 series · en curso · todas desde el #1" */
    fun resumen(): String = buildList {
        add(when {
            desde == null -> "sin fecha"
            hasta == null || enCurso -> "desde $desde"
            desde == hasta -> "$desde"
            else -> "$desde-$hasta"
        })
        add(if (series.size == 1) "1 serie" else "${series.size} series")
        if (enCurso) add("en curso")
        if (todasDesdeElUno) add("todas desde el #1")
    }.joinToString(" · ")
}

object Eras {

    /**
     * Reparte las series de un personaje en eras.
     *
     * Una serie con etiqueta de la wiki va SOLO a su era; las que no tienen
     * ninguna caen en su edad por año, para que no se pierda nada. Asi un
     * personaje de DC sale por iniciativas y uno de Marvel, que no las etiqueta,
     * sale por edades.
     */
    fun de(series: List<SerieWiki>): List<OpcionEra> {
        val out = mutableListOf<OpcionEra>()

        series.flatMap { s -> s.eras.map { it to s } }
            .groupBy({ it.first }, { it.second })
            .forEach { (nombre, ss) -> out.add(OpcionEra(nombre, ss, true)) }

        series.filter { it.eras.isEmpty() }
            .groupBy { Edades.de(it.anio) }
            .forEach { (edad, ss) -> out.add(OpcionEra(edad ?: "Sin fecha", ss, false)) }

        return out.sortedBy { it.desde ?: 9999 }
    }
}
