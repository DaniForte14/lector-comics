package com.dani.lector.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class CalendarioTest {

    /** Los milisegundos del mediodia de ese dia, en la zona de la app. */
    private fun aLas12(anio: Int, mes: Int, dia: Int): Long =
        ZonedDateTime.of(LocalDate.of(anio, mes, dia).atTime(12, 0), Novedades.ZONA)
            .toInstant().toEpochMilli()

    private fun comic(carpeta: String, n: Int) =
        Comic(uri = "$carpeta/$n", nombre = "$n.cbz", carpeta = carpeta,
              numero = n, esEspecial = false)

    // ─────────────────────────── LAS SEMANAS ───────────────────────────

    /**
     * Septiembre de 2026 empieza en MARTES, asi que la primera fila lleva un
     * hueco delante. Es el caso normal.
     */
    @Test fun `un mes que empieza entre semana lleva huecos delante`() {
        val s = Calendario.semanas(2026, 9)
        assertNull(s[0][0])              // lunes 31 de agosto: hueco
        assertEquals(1, s[0][1])         // martes 1
        assertEquals(6, s[0][6])         // domingo 6
    }

    /**
     * El caso de borde que mas se rompe: un mes que EMPIEZA EN DOMINGO lleva
     * seis huecos delante, y con 31 dias necesita SEIS filas.
     * Noviembre de 2026 empieza en domingo.
     */
    @Test fun `un mes que empieza en domingo llena seis huecos`() {
        val s = Calendario.semanas(2026, 11)
        assertEquals(List(6) { null }, s[0].take(6))
        assertEquals(1, s[0][6])
        assertEquals(30, s.flatten().filterNotNull().size)
    }

    /** Todas las filas tienen siete casillas, tambien la ultima. */
    @Test fun `las filas siempre son de siete`() {
        for (mes in 1..12) {
            val s = Calendario.semanas(2026, mes)
            s.forEach { assertEquals(7, it.size) }
            assertEquals(LocalDate.of(2026, mes, 1).lengthOfMonth(),
                s.flatten().filterNotNull().size)
        }
    }

    /** Febrero de 2028 es bisiesto: 29 días. */
    @Test fun `un febrero bisiesto trae veintinueve`() {
        assertEquals(29, Calendario.semanas(2028, 2).flatten().filterNotNull().size)
    }

    // ─────────────────────────── LO LEIDO ───────────────────────────

    private fun sesion(uri: String, dia: String, paginas: Int, cuando: Long) =
        Sesion(uri, dia, 0, paginas - 1, paginas, cuando)

    @Test fun `agrupa por dia del mes`() {
        val a = comic("GL", 1); val b = comic("GL", 2); val c = comic("GL", 3)
        val ses = listOf(
            sesion(a.uri, "2026-09-02", 3, aLas12(2026, 9, 2)),
            sesion(b.uri, "2026-09-02", 5, aLas12(2026, 9, 2)),
            sesion(c.uri, "2026-09-07", 4, aLas12(2026, 9, 7))
        )
        val r = Calendario.porDia(ses, listOf(a, b, c), 2026, 9)
        assertEquals(setOf(2, 7), r.keys)
        assertEquals(2, r[2]?.size)
        assertEquals(listOf(Calendario.Leido(c, 4, 0, 3)), r[7])
    }

    /**
     * EL CASO QUE PIDIÓ DANI: el mismo cómic en tres días sale en los tres, con
     * lo que leíste de él cada uno. Con la fecha de la marca salía solo el
     * último, porque solo se guardaba una.
     */
    @Test fun `el mismo comic en tres dias sale en los tres`() {
        val a = comic("GL", 1)
        val ses = listOf(
            sesion(a.uri, "2026-09-01", 6, aLas12(2026, 9, 1)),
            sesion(a.uri, "2026-09-03", 4, aLas12(2026, 9, 3)),
            sesion(a.uri, "2026-09-05", 9, aLas12(2026, 9, 5))
        )
        val r = Calendario.porDia(ses, listOf(a), 2026, 9)
        assertEquals(setOf(1, 3, 5), r.keys)
        assertEquals(6, r[1]?.first()?.paginas)
        assertEquals(4, r[3]?.first()?.paginas)
        assertEquals(9, r[5]?.first()?.paginas)
    }

    /** Otro mes no entra, aunque sea del mismo año. */
    @Test fun `los de otro mes se quedan fuera`() {
        val a = comic("GL", 1)
        val ses = listOf(sesion(a.uri, "2026-08-30", 3, aLas12(2026, 8, 30)))
        assertEquals(emptyMap<Int, List<Calendario.Leido>>(),
            Calendario.porDia(ses, listOf(a), 2026, 9))
    }

    /**
     * Un comic borrado del disco no se pinta: seria una casilla con un hueco
     * gris y sin explicacion. Misma regla que en las estadisticas.
     */
    @Test fun `lo que ya no tienes no se pinta`() {
        val a = comic("GL", 1)
        val ses = listOf(
            sesion(a.uri, "2026-09-02", 3, aLas12(2026, 9, 2)),
            sesion("GL/99", "2026-09-02", 3, aLas12(2026, 9, 2))
        )
        assertEquals(listOf(Calendario.Leido(a, 3, 0, 2)),
            Calendario.porDia(ses, listOf(a), 2026, 9)[2])
    }

    /** Dentro de un dia, lo ultimo que leiste va primero. */
    @Test fun `el mas reciente del dia va delante`() {
        val pronto = comic("GL", 1); val tarde = comic("GL", 2)
        val ses = listOf(
            sesion(pronto.uri, "2026-09-02", 3, aLas12(2026, 9, 2)),
            sesion(tarde.uri, "2026-09-02", 3, aLas12(2026, 9, 2) + 3_600_000)
        )
        assertEquals(listOf(tarde, pronto),
            Calendario.porDia(ses, listOf(pronto, tarde), 2026, 9)?.get(2)?.map { it.comic })
    }

    /** El tramo en cristiano: en base 1, como lo ve el lector. */
    @Test fun `el tramo de paginas`() {
        val c = comic("GL", 1)
        assertEquals("págs. 4-16", Calendario.Leido(c, 13, 3, 15).tramo)
        assertEquals("pág. 4", Calendario.Leido(c, 1, 3, 3).tramo)
    }

    @Test fun `el nombre del mes`() {
        assertEquals("Septiembre", Calendario.nombreMes(9))
        assertEquals("Enero", Calendario.nombreMes(1))
    }
}
