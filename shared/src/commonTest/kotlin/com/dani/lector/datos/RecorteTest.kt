package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Las cuatro reglas de [Recorte], que es donde estan las trampas.
 *
 * Merecen prueba porque **cuando se tuercen no dan ningun error**: la pagina
 * simplemente sale recortada de mas o de menos, y eso solo se ve mirando el
 * movil pagina por pagina. Hasta hoy no habia ninguna.
 *
 * Las paginas son de mentira: un IntArray y dos funciones que devuelven filas y
 * columnas, que es justo lo que pide `Recorte.util`.
 */
class RecorteTest {

    private val BLANCO = 0xFFFFFFFF.toInt()
    private val NEGRO = 0xFF000000.toInt()

    private class Pagina(val ancho: Int, val alto: Int, fondo: Int) {
        val px = IntArray(ancho * alto) { fondo }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
            for (y in y0..y1) for (x in x0..x1) px[y * ancho + x] = color
        }
        fun punto(x: Int, y: Int, color: Int) { px[y * ancho + x] = color }
        fun util() = Recorte.util(ancho, alto,
            { y -> IntArray(ancho) { x -> px[y * ancho + x] } },
            { x -> IntArray(alto) { y -> px[y * ancho + x] } })
    }

    /** 100x100 con el dibujo en (20,20)-(79,79) y marco liso alrededor. */
    private fun conMarco(fondo: Int, tinta: Int) =
        Pagina(100, 100, fondo).apply { rect(20, 20, 79, 79, tinta) }

    @Test fun `el marco blanco se recorta y deja dos pixeles de gracia`() {
        val r = conMarco(BLANCO, NEGRO).util()!!
        assertEquals(18, r.izq)
        assertEquals(18, r.arriba)
        assertEquals(82, r.der)
        assertEquals(82, r.abajo)
        assertEquals(64, r.ancho)
        assertEquals(64, r.alto)
    }

    // El fondo se toma de la esquina y no se supone blanco: hay muchos comics
    // escaneados con el marco negro.
    @Test fun `el marco negro se recorta igual que el blanco`() {
        val r = conMarco(NEGRO, BLANCO).util()!!
        assertEquals(18, r.izq)
        assertEquals(18, r.arriba)
        assertEquals(82, r.der)
        assertEquals(82, r.abajo)
    }

    // El dibujo llega a los cuatro bordes: no sobra nada y se devuelve null en
    // vez de un recuadro igual que la pagina.
    @Test fun `una pagina sin marco no se toca`() {
        val p = Pagina(100, 100, BLANCO)
        p.rect(0, 0, 99, 0, NEGRO)
        p.rect(0, 99, 99, 99, NEGRO)
        p.rect(0, 0, 0, 99, NEGRO)
        p.rect(99, 0, 99, 99, NEGRO)
        assertNull(p.util())
    }

    // Una pagina lisa entera se comeria a si misma: el recorte dejaria un 5% y
    // la regla del 40% lo tira.
    @Test fun `si el recorte deja menos del 40 por ciento no se fia`() {
        assertNull(Pagina(100, 100, BLANCO).util())
    }

    @Test fun `una pagina diminuta no se mira siquiera`() {
        assertNull(Pagina(50, 50, BLANCO).util())
    }

    // Dos pixeles sueltos en una fila de 100 son el 2% justo: el limite entra.
    // Sin esta tolerancia, una mota del escaner impide recortar la pagina.
    @Test fun `un poco de ruido en el margen no impide el corte`() {
        val p = conMarco(BLANCO, NEGRO)
        p.punto(5, 5, NEGRO)
        p.punto(50, 5, NEGRO)
        val r = p.util()!!
        assertEquals(18, r.arriba)
        assertEquals(18, r.izq)
    }

    // Y tres ya no: esa fila deja de ser margen y el corte de arriba se para
    // ahi. Es el otro lado del mismo limite.
    @Test fun `demasiado ruido si para el corte`() {
        val p = conMarco(BLANCO, NEGRO)
        p.punto(5, 5, NEGRO)
        p.punto(50, 5, NEGRO)
        p.punto(90, 5, NEGRO)
        val r = p.util()!!
        assertEquals(3, r.arriba)     // la fila 5 menos los 2 de gracia
        assertEquals(18, r.izq)       // por los lados no ha cambiado nada
    }

    // Una splash page clara o una pagina de creditos es casi todo fondo. Si se
    // buscara sin tope, el recorte se comeria el dibujo entero.
    @Test fun `no se busca mas alla de la mitad de la pagina`() {
        val p = Pagina(100, 100, BLANCO)
        p.rect(60, 60, 99, 99, NEGRO)
        val r = p.util()!!
        assertEquals(48, r.izq)
        assertEquals(48, r.arriba)
        assertEquals(100, r.der)
        assertEquals(100, r.abajo)
    }
}
