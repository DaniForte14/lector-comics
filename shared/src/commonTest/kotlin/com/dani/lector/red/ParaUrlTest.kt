package com.dani.lector.red

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El codificador de URL, que sustituye a `URLEncoder` porque aquel es de la JVM.
 *
 * Se prueba porque si se equivoca **no da ningun error**: Comic Vine responde
 * 200 con cero resultados y la app se queda sin datos como si la serie no
 * existiera. Es el mismo fallo silencioso que ya costo un dia con `filter=name:`.
 */
class ParaUrlTest {

    @Test fun `las letras y los digitos pasan tal cual`() {
        assertEquals("GreenLantern2021", paraUrl("GreenLantern2021"))
    }

    // LO MAS IMPORTANTE DE ESTA PRUEBA. Con `+` se buscaria la cadena literal
    // "Green+Lantern" en /search/?query= y no encontraria nada, nunca.
    @Test fun `el espacio va como %20 y nunca como mas`() {
        assertEquals("Green%20Lantern", paraUrl("Green Lantern"))
    }

    @Test fun `los dos puntos y la coma se escapan`() {
        assertEquals("name%3AAbsolute%20Batman", paraUrl("name:Absolute Batman"))
        assertEquals("a%2Cb", paraUrl("a,b"))
    }

    // Un caracter de dos bytes: cada byte va por separado. Si la guarda de
    // "menos de 128" no estuviera, alguno podria colarse sin escapar.
    @Test fun `los acentos van byte a byte en UTF-8`() {
        assertEquals("Espa%C3%B1a", paraUrl("España"))
        assertEquals("a%C3%A9", paraUrl("aé"))
    }

    @Test fun `los sin reservar de RFC 3986 no se tocan`() {
        assertEquals("-_.~", paraUrl("-_.~"))
    }

    @Test fun `el vacio da vacio`() {
        assertEquals("", paraUrl(""))
    }
}
