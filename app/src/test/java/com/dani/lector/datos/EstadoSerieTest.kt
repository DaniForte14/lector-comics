package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoSerieTest {

    /** Una serie de [n] numeros, uno al mes desde enero de [anio]. */
    private fun serie(n: Int, anio: Int = 2005) = (1..n).map {
        val mes = (it - 1) % 12 + 1
        NumeroRemoto("$it", it, "%04d-%02d-01".format(anio + (it - 1) / 12, mes))
    }

    private val CORTE = "2026-04-26"

    @Test fun `tenerlo todo es estar completa`() {
        val r = EstadoSerie.de((1..12).toList(), serie(12), CORTE)
        assertTrue(r.completa)
        assertEquals(12, r.tienes)
        assertEquals(12, r.total)
    }

    // Lo que Huecos NO puede saber: que la serie sigue despues de tu ultimo.
    @Test fun `la cola que falta si aparece`() {
        val r = EstadoSerie.de((1..58).toList(), serie(62), CORTE)
        assertFalse(r.completa)
        assertEquals(listOf(59, 60, 61, 62), r.faltan)
        assertEquals("del 59 al 62", EstadoSerie.texto(r))
    }

    // Y lo de antes del primero, que Huecos tampoco se atreve a decir.
    @Test fun `lo de antes del primero tambien`() {
        val r = EstadoSerie.de((5..10).toList(), serie(10), CORTE)
        assertEquals(listOf(1, 2, 3, 4), r.faltan)
    }

    @Test fun `agrupa los tramos sueltos`() {
        val r = EstadoSerie.de(listOf(1, 2, 5, 8, 9, 10), serie(10), CORTE)
        assertEquals(listOf(3, 4, 6, 7), r.faltan)
        assertEquals("del 3 al 4 y del 6 al 7", EstadoSerie.texto(r))
    }

    // Un numero tuyo que la fuente no tiene no rompe nada ni cuenta de mas.
    @Test fun `tener de mas no descuadra el total`() {
        val r = EstadoSerie.de(listOf(1, 2, 3, 99), serie(3), CORTE)
        assertEquals(3, r.total)
        assertEquals(3, r.tienes)
        assertTrue(r.completa)
    }

    // Los que no son numeros no se pueden cruzar con un fichero tuyo, asi que
    // no cuentan como que faltan: seria decirte que falta algo sin saber que.
    @Test fun `los numeros raros no cuentan como que faltan`() {
        val remotos = listOf(
            NumeroRemoto("1", 1, "2005-01-01"),
            NumeroRemoto("1.MU", null, "2005-02-01"),
            NumeroRemoto("Annual 1", null, "2005-03-01")
        )
        val r = EstadoSerie.de(listOf(1), remotos, CORTE)
        assertEquals(1, r.total)
        assertTrue(r.completa)
    }

    @Test fun `en emision si el ultimo es reciente`() {
        val remotos = listOf(NumeroRemoto("1", 1, "2026-06-01"))
        assertTrue(EstadoSerie.de(listOf(1), remotos, CORTE).enEmision)
    }

    @Test fun `terminada si el ultimo es viejo`() {
        assertFalse(EstadoSerie.de((1..12).toList(), serie(12, 2005), CORTE).enEmision)
    }

    // Sin fechas no se puede afirmar que siga viva. Ante la duda, no.
    @Test fun `sin fechas no se inventa que este en emision`() {
        val remotos = listOf(NumeroRemoto("1", 1, null), NumeroRemoto("2", 2, null))
        val r = EstadoSerie.de(listOf(1), remotos, CORTE)
        assertFalse(r.enEmision)
        assertEquals(null, r.ultima)
    }

    @Test fun `sin datos de la fuente no dice nada`() {
        val r = EstadoSerie.de(listOf(1, 2, 3), emptyList(), CORTE)
        assertEquals(0, r.total)
        assertFalse(r.completa)
        assertEquals("", EstadoSerie.texto(r))
    }
}
