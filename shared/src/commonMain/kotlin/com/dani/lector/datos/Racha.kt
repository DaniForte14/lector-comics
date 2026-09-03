package com.dani.lector.datos

import kotlinx.datetime.Instant
import kotlinx.datetime.offsetAt

/**
 * Dias seguidos leyendo.
 *
 * Se cuenta por DIA LOCAL, no por bloques de 24 horas desde la epoca: leer a
 * las 23:50 y otra vez a las 00:10 son dos dias, y con la division cruda de
 * milisegundos serian el mismo o dos, segun tu huso. Por eso se suma el
 * desfase horario antes de dividir.
 *
 * La racha sigue viva si has leido HOY o AYER. Exigir que sea hoy la romperia
 * cada mañana antes de que abras la app, que es absurdo.
 */
object Racha {

    private const val DIA_MS = 86_400_000L

    /**
     * LA ZONA ES LA MISMA QUE LA DEL RESTO DE LA APP (ver [Novedades.ZONA]) y no
     * la del movil. Si no, la racha y el calendario —que salen en la MISMA
     * pantalla— podrian discrepar en un dia en cuanto Dani cruce un huso, y dos
     * cifras de la misma pantalla que no cuadran se leen como un fallo.
     */
    fun dia(t: Long): Long =
        (t + Novedades.ZONA.offsetAt(Instant.fromEpochMilliseconds(t))
            .totalSeconds * 1000L) / DIA_MS

    fun de(tiempos: Collection<Long>, ahora: Long): Int {
        if (tiempos.isEmpty()) return 0
        val dias = tiempos.mapTo(HashSet()) { dia(it) }
        val hoy = dia(ahora)

        var d = when {
            dias.contains(hoy) -> hoy
            dias.contains(hoy - 1) -> hoy - 1
            else -> return 0
        }
        var n = 0
        while (dias.contains(d)) { n++; d-- }
        return n
    }

    /** Cuantos dias distintos has leido en total. */
    fun diasTotales(tiempos: Collection<Long>): Int =
        tiempos.mapTo(HashSet()) { dia(it) }.size
}
