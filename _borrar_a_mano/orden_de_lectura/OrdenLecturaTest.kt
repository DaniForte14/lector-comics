package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdenLecturaTest {

    /** Un numero con fecha de portada "aaaa-mm-01". */
    private fun num(n: Int, anio: Int, mes: Int) =
        NumeroRemoto("$n", n, "%04d-%02d-01".format(anio, mes))

    /**
     * El caso que motiva todo esto: una serie regular y una mini que arranca a
     * mitad. Lo que demuestra el intercalado es que a partir del mes en que la
     * mini empieza, el mes trae DOS tramos: los dos estuvieron en la tienda a
     * la vez, y eso es exactamente lo que dice el dato.
     */
    @Test fun `intercala dos series por fecha de portada`() {
        val gl = OrdenLectura.Entrada(
            serie = "Green Lantern", ruta = "GL/Vol 4",
            numeros = (1..10).map { num(it, 2005, it) },
            mios = emptySet()
        )
        val corps = OrdenLectura.Entrada(
            serie = "GL Corps Recharge", ruta = "GL/Recharge",
            numeros = (1..5).map { num(it, 2005, 5 + it) },   // meses 6 a 10
            mios = emptySet()
        )

        val r = OrdenLectura.de(listOf(gl, corps))

        assertEquals(10, r.meses.size)
        assertEquals("2005-01", r.meses[0].clave)
        assertEquals(1, r.meses[0].tramos.size)
        // Junio: entra la mini. Dentro del mes, alfabetico: "GL Corps" antes
        // que "Green Lantern". Ese orden es arbitrario A PROPOSITO y por eso la
        // pantalla enseña el mes: dentro de un mes no hay secuencia que seguir.
        val junio = r.meses[5]
        assertEquals("2005-06", junio.clave)
        assertEquals(2, junio.tramos.size)
        assertEquals("GL Corps Recharge", junio.tramos[0].serie)
        assertEquals("#1", junio.tramos[0].rango)
        assertEquals("Green Lantern", junio.tramos[1].serie)
        assertEquals("#6", junio.tramos[1].rango)
        assertEquals(15, r.total)
    }

    /** Dentro del mismo mes los numeros de una serie van seguidos, no barajados. */
    @Test fun `en el mismo mes cada serie va seguida`() {
        val a = OrdenLectura.Entrada("A", "a", (1..3).map { num(it, 2010, 1) }, emptySet())
        val b = OrdenLectura.Entrada("B", "b", (1..3).map { num(it, 2010, 1) }, emptySet())
        val r = OrdenLectura.de(listOf(a, b))
        assertEquals(1, r.meses.size)
        assertEquals(2, r.meses[0].tramos.size)
        assertEquals("#1–3", r.meses[0].tramos[0].rango)
        assertEquals("#1–3", r.meses[0].tramos[1].rango)
    }

    @Test fun `marca lo que tienes y lo que te falta`() {
        val e = OrdenLectura.Entrada(
            "Daredevil", "DD/Vol 6",
            (1..5).map { num(it, 2019, it) },
            mios = setOf(1, 2, 5)
        )
        val r = OrdenLectura.de(listOf(e))
        assertEquals(5, r.meses.size)
        assertEquals(3, r.tienes)
        assertEquals(2, r.meses.sumOf { it.faltan })
    }

    /**
     * Un "Annual 2" no se puede cruzar con los numeros de tus ficheros, asi que
     * queda en "no se sabe" y NO cuenta como que falta. Misma regla que en
     * EstadoSerie: decir que falta algo que no sabemos ni que es es peor que no
     * decir nada.
     */
    @Test fun `una etiqueta que no es un numero queda en no se sabe`() {
        val e = OrdenLectura.Entrada(
            "Daredevil", "DD",
            listOf(num(1, 2019, 1), NumeroRemoto("Annual 2", null, "2019-02-01")),
            mios = setOf(1)
        )
        val r = OrdenLectura.de(listOf(e))
        val anual = r.meses[1].tramos[0].numeros.first()
        assertEquals("Annual 2", anual.etiqueta)
        assertNull(anual.tienes)
        assertEquals(0, r.meses[1].faltan)
        assertEquals(1, r.tienes)
    }

    /** Sin fecha no hay sitio: se aparta en vez de colocarlo a ojo. */
    @Test fun `los numeros sin fecha se apartan`() {
        val e = OrdenLectura.Entrada(
            "X", "x",
            listOf(num(1, 2020, 1), NumeroRemoto("2", 2, null), NumeroRemoto("3", 3, "")),
            mios = emptySet()
        )
        val r = OrdenLectura.de(listOf(e))
        assertEquals(1, r.meses.sumOf { it.cuantos })
        assertEquals(2, r.sinFecha.size)
        assertEquals(3, r.total)   // el total los sigue contando: existen
    }

    @Test fun `sin series no revienta`() {
        val r = OrdenLectura.de(emptyList())
        assertTrue(r.meses.isEmpty())
        assertEquals(0, r.total)
    }

    /** Un tramo de uno solo no se escribe como rango. */
    @Test fun `el rango de un solo numero`() {
        val a = OrdenLectura.Entrada("A", "a", listOf(num(7, 2010, 1)), emptySet())
        val r = OrdenLectura.de(listOf(a))
        assertEquals("#7", r.meses[0].tramos[0].rango)
    }

    /** Los meses salen ordenados aunque las series entren en cualquier orden. */
    @Test fun `los meses van en orden aunque las series no`() {
        val nueva = OrdenLectura.Entrada("Nueva", "n", listOf(num(1, 2021, 3)), emptySet())
        val vieja = OrdenLectura.Entrada("Vieja", "v", listOf(num(1, 1999, 12)), emptySet())
        val r = OrdenLectura.de(listOf(nueva, vieja))
        assertEquals(listOf("1999-12", "2021-03"), r.meses.map { it.clave })
    }

    @Test fun `el mes en cristiano`() {
        assertEquals("agosto de 2007", OrdenLectura.mes("2007-08-01"))
        assertEquals("agosto de 2007", OrdenLectura.mes("2007-08"))
        assertEquals("sin fecha", OrdenLectura.mes(null))
    }
}
