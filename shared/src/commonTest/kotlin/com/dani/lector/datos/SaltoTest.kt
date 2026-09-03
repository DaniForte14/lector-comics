package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class SaltoTest {

    @Test fun `un numero normal, en base 1`() {
        assertEquals(0, Salto.destino("1", 22))
        assertEquals(11, Salto.destino("12", 22))
        assertEquals(21, Salto.destino("22", 22))
    }

    @Test fun `los espacios de sobra no estorban`() {
        assertEquals(11, Salto.destino("  12  ", 22))
    }

    @Test fun `lo que no es un numero no mueve nada`() {
        assertNull(Salto.destino("", 22))
        assertNull(Salto.destino("   ", 22))
        assertNull(Salto.destino("doce", 22))
        assertNull(Salto.destino("12a", 22))
        assertNull(Salto.destino("1,5", 22))
    }

    @Test fun `fuera del comic no mueve nada`() {
        assertNull(Salto.destino("0", 22))
        assertNull(Salto.destino("-3", 22))
        assertNull(Salto.destino("23", 22))
        assertNull(Salto.destino("900", 22))
    }

    /** Un comic que no ha podido cargar ninguna pagina: no hay a donde ir. */
    @Test fun `sin paginas no hay destino`() {
        assertNull(Salto.destino("1", 0))
    }

    // ───────────────────── la barra de progreso ─────────────────────

    @Test fun `la barra reparte las paginas a lo ancho`() {
        assertEquals(0, Salto.deBarra(0f, 100, 10))
        assertEquals(5, Salto.deBarra(50f, 100, 10))
        assertEquals(9, Salto.deBarra(99f, 100, 10))
    }

    /** El dedo se sale de la barra constantemente: eso se acota, no se ignora. */
    @Test fun `pasarse por los lados da los extremos`() {
        assertEquals(0, Salto.deBarra(-40f, 100, 10))
        assertEquals(9, Salto.deBarra(500f, 100, 10))
    }

    /** El ultimo pixel daria 10 con 10 paginas, y la ultima es la 9. */
    @Test fun `el borde derecho no se sale por uno`() {
        assertEquals(9, Salto.deBarra(100f, 100, 10))
    }

    @Test fun `antes de medir la barra no se divide por cero`() {
        assertEquals(0, Salto.deBarra(30f, 0, 10))
        assertEquals(0, Salto.deBarra(30f, 100, 0))
    }
}
