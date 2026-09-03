package com.dani.lector.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El parser llevaba meses sin una sola prueba, y el documento del proyecto
 * decia que las tenia. El fallo del "Secret Origin 6" salio en el movil.
 */
class ParserTest {

    private fun n(f: String) = Parser.numeroDe(f)

    // ── el caso que lo destapo ──────────────────────────────────────────
    //
    // El titulo del comic acaba en una cifra —"Secret Origin 6"— y el barrido
    // de derecha a izquierda se la comia antes de llegar al #34.
    @Test fun `la almohadilla manda sobre una cifra del titulo`() {
        assertEquals(34, n("Green Lantern Vol4 #34 - Secret Origin 6 -.cbz"))
        assertEquals(31, n("Green Lantern Vol4 #31 - Secret Origin 3.cbz"))
    }

    @Test fun `el cero es un numero de grapa como otro cualquiera`() {
        assertEquals(0, n("Green Lantern Vol4 #00 Secret Origin.cbz"))
    }

    // ── lo que ya funcionaba y no se puede romper ───────────────────────
    @Test fun `sin almohadilla, la cifra del final`() {
        assertEquals(36, n("Daredevil 36.cbz"))
        assertEquals(35, n("Daredevil 035.cbz"))
    }

    @Test fun `con almohadilla suelta o pegada`() {
        assertEquals(28, n("Daredevil #28.cbz"))
        assertEquals(14, n("Moon Knight#14.cbz"))
        assertEquals(14, n("Moon Knight nº14.cbz"))
    }

    @Test fun `el volumen no es el numero`() {
        assertEquals(1, n("Daredevil Vol.7 #01 [x].cbz"))
        assertEquals(6, n("Green Lantern Vol4 #06.cbz"))
    }

    // Un año entre parentesis no es una grapa. Se quita con el parentesis, pero
    // aunque no lo estuviera, cuatro cifras entre 1930 y 2100 se descartan.
    @Test fun `un año no es un numero`() {
        assertEquals(32, n("Green Lantern (2005) 032.cbz"))
    }

    @Test fun `los especiales se reconocen y no cuentan el numero de serie`() {
        assertTrue(Parser.esEspecial("Batman Annual 2.cbz"))
        assertTrue(Parser.esEspecial("Green Lantern One-Shot.cbz"))
        assertEquals(2, n("Batman Annual 2.cbz"))
    }

    @Test fun `un nombre sin cifras no da numero`() {
        assertEquals(null, n("Green Lantern Rebirth.cbz"))
    }
}
