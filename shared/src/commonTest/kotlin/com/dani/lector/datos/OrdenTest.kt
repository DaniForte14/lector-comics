package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.Test

class OrdenTest {

    private fun c(nombre: String, numero: Int?, cuando: Long = 0L) =
        Comic(uri = "u/$nombre", nombre = nombre, carpeta = "s",
              numero = numero, esEspecial = false, cuando = cuando)

    private fun nombres(l: List<Comic>) = l.map { it.nombre }

    @Test fun `por numero, y los sin numero al final`() {
        val l = listOf(c("Annual.cbz", null), c("B 10.cbz", 10), c("A 2.cbz", 2))
        assertEquals(listOf("A 2.cbz", "B 10.cbz", "Annual.cbz"),
            nombres(OrdenCarpeta.de(l, Orden.NUMERO)))
    }

    /** El 10 va DESPUES del 2: es un numero, no una cadena. */
    @Test fun `por numero no es por texto`() {
        val l = listOf(c("x 10.cbz", 10), c("x 2.cbz", 2))
        assertEquals(listOf("x 2.cbz", "x 10.cbz"),
            nombres(OrdenCarpeta.de(l, Orden.NUMERO)))
    }

    @Test fun `por nombre, sin mirar mayusculas`() {
        val l = listOf(c("zorro.cbz", 3), c("Alfa.cbz", 1), c("beta.cbz", 2))
        assertEquals(listOf("Alfa.cbz", "beta.cbz", "zorro.cbz"),
            nombres(OrdenCarpeta.de(l, Orden.NOMBRE)))
    }

    @Test fun `recientes primero`() {
        val l = listOf(
            c("viejo.cbz", 1, cuando = 100),
            c("nuevo.cbz", 2, cuando = 900),
            c("medio.cbz", 3, cuando = 500))
        assertEquals(listOf("nuevo.cbz", "medio.cbz", "viejo.cbz"),
            nombres(OrdenCarpeta.de(l, Orden.NUEVOS)))
    }

    /** Copiar una carpeta entera deja a todos con la misma fecha. */
    @Test fun `a igual fecha manda el numero`() {
        val l = listOf(
            c("c 3.cbz", 3, cuando = 700),
            c("a 1.cbz", 1, cuando = 700),
            c("b 2.cbz", 2, cuando = 700))
        assertEquals(listOf("a 1.cbz", "b 2.cbz", "c 3.cbz"),
            nombres(OrdenCarpeta.de(l, Orden.NUEVOS)))
    }

    @Test fun `una carpeta vacia no revienta`() {
        assertEquals(emptyList<String>(),
            nombres(OrdenCarpeta.de(emptyList(), Orden.NUEVOS)))
    }
}
