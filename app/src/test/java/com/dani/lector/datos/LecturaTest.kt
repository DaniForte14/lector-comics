package com.dani.lector.datos

import org.junit.Assert.assertEquals
import org.junit.Test

class LecturaTest {

    private val AHORA = 1_756_000_000_000L
    private val LUNES = "2026-08-31"
    private val MIERCOLES = "2026-09-02"

    /** El primer día que abres un cómic: una página vista, y empieza donde estás. */
    @Test fun `la primera pagina del dia abre la sesion`() {
        val s = Lectura.registrar(null, "x", LUNES, pagina = 0, ahora = AHORA)
        assertEquals(Sesion("x", LUNES, desde = 0, hasta = 0, paginas = 1, cuando = AHORA), s)
    }

    /** Pasar páginas suma de una en una. */
    @Test fun `avanzar suma paginas`() {
        var s = Lectura.registrar(null, "x", LUNES, 0, AHORA)
        s = Lectura.registrar(s, "x", LUNES, 1, AHORA + 1)
        s = Lectura.registrar(s, "x", LUNES, 2, AHORA + 2)
        assertEquals(3, s.paginas)
        assertEquals(2, s.hasta)
    }

    /**
     * En modo de dos páginas el visor avisa con la ÚLTIMA de la hoja, así que
     * cada pasada avanza dos. Se suma la diferencia, no un uno fijo.
     */
    @Test fun `una hoja doble suma dos`() {
        var s = Lectura.registrar(null, "x", LUNES, 0, AHORA)
        s = Lectura.registrar(s, "x", LUNES, 2, AHORA + 1)
        assertEquals(3, s.paginas)
    }

    /**
     * Ir hacia atrás a mirar una viñeta no suma —no es una página nueva— pero
     * tampoco resta, y el día sigue contando como leído.
     */
    @Test fun `volver atras no suma ni resta`() {
        var s = Lectura.registrar(null, "x", LUNES, 10, AHORA)
        s = Lectura.registrar(s, "x", LUNES, 11, AHORA + 1)
        val antes = s.paginas
        s = Lectura.registrar(s, "x", LUNES, 4, AHORA + 2)
        assertEquals(antes, s.paginas)
        assertEquals(11, s.hasta)
        assertEquals(AHORA + 2, s.cuando)
    }

    /**
     * EL CASO QUE PIDIÓ DANI: el mismo cómic otro día es OTRA sesión, con su
     * propia cuenta. Así sale en los tres días en vez de solo en el último.
     */
    @Test fun `otro dia es otra sesion que empieza donde lo dejaste`() {
        var lunes = Lectura.registrar(null, "x", LUNES, 0, AHORA)
        lunes = Lectura.registrar(lunes, "x", LUNES, 5, AHORA + 1)
        assertEquals(6, lunes.paginas)

        // el miércoles sigue por la 6: sesión nueva, cuenta desde cero
        val miercoles = Lectura.registrar(lunes, "x", MIERCOLES, 6, AHORA + 99)
        assertEquals(MIERCOLES, miercoles.dia)
        assertEquals(6, miercoles.desde)
        assertEquals(1, miercoles.paginas)
        // y la del lunes no se ha tocado
        assertEquals(6, lunes.paginas)
    }
}
