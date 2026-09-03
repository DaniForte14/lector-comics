package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Los casos que de verdad aparecen en una biblioteca de comics, no los bonitos.
 */
class HuecosTest {

    private fun huecos(vararg n: Int) = Huecos.de(n.toList()).faltan

    @Test fun `una serie seguida no tiene huecos`() {
        val e = Huecos.de((1..12).toList())
        assertTrue(e.seguida)
        assertEquals(12, e.tienes)
        assertEquals(0, e.cuantosFaltan)
    }

    @Test fun `un hueco de uno`() {
        assertEquals(listOf(3..3), huecos(1, 2, 4, 5))
    }

    @Test fun `un tramo entero`() {
        assertEquals(listOf(3..7), huecos(1, 2, 8))
    }

    // OJO AL ESCRIBIR ESTE CASO: la primera version ponia (11, 14, 49, 59) y
    // esperaba dos huecos. Son TRES, porque entre el 14 y el 49 tambien falta
    // todo. El fallo estaba en la prueba, no en el codigo.
    @Test fun `varios huecos separados`() {
        val tengo = (1..11).toList() + (14..49).toList() + listOf(59)
        assertEquals(listOf(12..13, 50..58), Huecos.de(tengo).faltan)
    }

    // Dos ficheros del mismo numero es lo normal cuando bajas una serie dos
    // veces. Un repetido NO tapa un hueco ni cuenta como numero de mas.
    @Test fun `los repetidos cuentan una sola vez`() {
        val e = Huecos.de(listOf(1, 1, 2, 2, 2, 4))
        assertEquals(3, e.tienes)
        assertEquals(listOf(3..3), e.faltan)
    }

    @Test fun `da igual el orden en que vengan`() {
        assertEquals(huecos(1, 2, 8), huecos(8, 2, 1))
    }

    // Lo que NO tiene que hacer: si empiezas por el 5, no se inventa que te
    // falten del 1 al 4. Puede que la serie empiece ahi.
    @Test fun `no inventa huecos antes del primero ni despues del ultimo`() {
        val e = Huecos.de(listOf(5, 6, 7))
        assertTrue(e.faltan.isEmpty())
        assertEquals(5, e.primero)
        assertEquals(7, e.ultimo)
    }

    @Test fun `una carpeta sin numeros no dice nada`() {
        val e = Huecos.de(emptyList())
        assertEquals(0, e.tienes)
        assertFalse(e.seguida)
        assertEquals("", Huecos.texto(e))
    }

    @Test fun `un solo numero no es una serie con huecos`() {
        val e = Huecos.de(listOf(7))
        assertTrue(e.seguida)
        assertEquals("", Huecos.texto(e))
    }

    // Hay series que empiezan en el 0 y numeros negativos no existen, pero un
    // #0 delante de un #1 no puede leerse como un hueco.
    @Test fun `el numero cero no es un hueco`() {
        assertTrue(Huecos.de(listOf(0, 1, 2)).seguida)
    }

    @Test fun `el texto de un hueco suelto`() {
        assertEquals("el 3", Huecos.texto(Huecos.de(listOf(1, 2, 4))))
    }

    @Test fun `el texto junta con y el ultimo tramo`() {
        val tengo = (1..11).toList() + (14..49).toList() + listOf(59)
        assertEquals("del 12 al 13 y del 50 al 58", Huecos.texto(Huecos.de(tengo)))
    }

    // Una serie a la que le falta media coleccion no puede escupir treinta
    // numeros: se enumeran tres tramos y se resume el resto.
    @Test fun `con muchos huecos se resume`() {
        val e = Huecos.de(listOf(1, 3, 5, 7, 9, 11))
        assertEquals(5, e.faltan.size)
        assertEquals("el 2, el 4, el 6 y 2 tramos más", Huecos.texto(e))
    }
}
