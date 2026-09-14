package com.dani.lector.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `HojaQueSeDobla` decide que se ve de la hoja mientras pasa, y **si se tuerce
 * no da ningun error**: la hoja da un salto, deja un hueco o pinta el reverso
 * donde no toca. Se prueban sus propiedades —areas, lados del eje, espejo—, no
 * puntos concretos, que dependen del angulo elegido y pueden cambiar sin que
 * nada este mal.
 *
 * Una hoja de 300 x 450, la proporcion de una pagina de comic.
 */
class HojaQueSeDoblaTest {

    private val ancho = 300f
    private val alto = 450f
    private val hoja = ancho * alto

    /** Los avances intermedios que se miran: de 0,05 en 0,05, sin los extremos. */
    private val avances = (1..19).map { it / 20f }

    private fun cerca(a: Float, b: Float, margen: Float = 0.01f) = abs(a - b) <= margen

    private fun cerca(a: Offset, b: Offset) = cerca(a.x, b.x) && cerca(a.y, b.y)

    /** El area de un poligono, por la formula del cordon. */
    private fun area(p: List<Offset>): Float {
        var doble = 0f
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            doble += a.x * b.y - b.x * a.y
        }
        return abs(doble) / 2
    }

    /**
     * La distancia con signo de [p] al eje, en pixeles: el producto vectorial
     * entre lo que mide el eje. En pixeles y no el producto a secas porque un
     * punto que cae justo sobre el eje trae un error de coma flotante, y sin
     * dividir ese error se multiplica por lo largo del eje.
     */
    private fun lado(p: Offset, g: Pliegue): Float {
        val dx = g.ejeHasta.x - g.ejeDesde.x
        val dy = g.ejeHasta.y - g.ejeDesde.y
        return (dx * (p.y - g.ejeDesde.y) - dy * (p.x - g.ejeDesde.x)) / sqrt(dx * dx + dy * dy)
    }

    /** El trozo cortado de la hoja: la solapa devuelta a su sitio. */
    private fun trozo(g: Pliegue) = g.solapa.map { HojaQueSeDobla.reflejar(it, g.ejeDesde, g.ejeHasta) }

    @Test fun `con avance cero la hoja esta entera y sin solapa`() {
        val g = HojaQueSeDobla.pliegue(ancho, alto, 0f, adelante = true)
        assertEquals(listOf(Offset(0f, 0f), Offset(ancho, 0f), Offset(ancho, alto), Offset(0f, alto)), g.plana)
        assertEquals(emptyList(), g.solapa)
    }

    @Test fun `con avance uno la hoja ya ha pasado entera`() {
        // La plana vacia, y la hoja entera al otro lado del borde izquierdo:
        // en un visor de una pagina, la hoja se va de la pantalla.
        val g = HojaQueSeDobla.pliegue(ancho, alto, 1f, adelante = true)
        assertEquals(emptyList(), g.plana)
        assertTrue(cerca(area(g.solapa), hoja, 1f), "area de la solapa ${area(g.solapa)}")
        assertTrue(g.solapa.all { it.x <= 0.01f }, "la solapa no se ha ido: ${g.solapa}")
    }

    @Test fun `el avance fuera de cero a uno se acota`() {
        for (adelante in listOf(true, false)) {
            assertEquals(HojaQueSeDobla.pliegue(ancho, alto, 0f, adelante), HojaQueSeDobla.pliegue(ancho, alto, -0.5f, adelante))
            assertEquals(HojaQueSeDobla.pliegue(ancho, alto, 1f, adelante), HojaQueSeDobla.pliegue(ancho, alto, 1.7f, adelante))
        }
    }

    @Test fun `al empezar solo se levanta la esquina de abajo a la derecha`() {
        // Como al pasar una hoja con el pulgar: el trozo cortado es un
        // triangulo en esa esquina, y la de arriba sigue plana.
        val g = HojaQueSeDobla.pliegue(ancho, alto, 0.05f, adelante = true)
        val t = trozo(g)
        assertEquals(3, t.size, "trozo $t")
        assertTrue(t.any { cerca(it, Offset(ancho, alto)) }, "la esquina de abajo no se levanta: $t")
        assertTrue(g.plana.any { cerca(it, Offset(ancho, 0f)) }, "la esquina de arriba se levanta: ${g.plana}")
    }

    @Test fun `la solapa es el trozo cortado reflejado sobre el eje`() {
        // Devuelta a su sitio con reflejar, la solapa tiene que ser un trozo de
        // la hoja, entero del lado de la esquina levantada; y la plana, entera
        // del otro lado. Si la solapa no fuera el reflejo, sus puntos devueltos
        // caerian fuera de la hoja o del lado que no es.
        for (a in avances) {
            val g = HojaQueSeDobla.pliegue(ancho, alto, a, adelante = true)
            val signo = if (lado(Offset(ancho, alto), g) > 0f) 1f else -1f
            for (p in trozo(g)) {
                assertTrue(p.x in -0.01f..ancho + 0.01f && p.y in -0.01f..alto + 0.01f, "con $a, $p fuera de la hoja")
                assertTrue(lado(p, g) * signo >= -0.01f, "con $a, $p del lado de la plana")
            }
            for (p in g.plana) assertTrue(lado(p, g) * signo <= 0.01f, "con $a, $p de la plana del lado de la solapa")
        }
    }

    @Test fun `la plana y el trozo cortado suman la hoja`() {
        for (a in avances) {
            val g = HojaQueSeDobla.pliegue(ancho, alto, a, adelante = true)
            val suma = area(g.plana) + area(trozo(g))
            assertTrue(cerca(suma, hoja, 1f), "con $a suman $suma y la hoja es $hoja")
        }
    }

    @Test fun `el area de la plana baja sin saltos al avanzar`() {
        // Monotona, y continua: de milesima en milesima de avance, la plana no
        // puede cambiar mas de un 1% de la hoja.
        var antes = hoja
        var a = 0f
        while (a <= 1f) {
            val ahora = area(HojaQueSeDobla.pliegue(ancho, alto, a, adelante = true).plana)
            assertTrue(ahora <= antes + 0.5f, "con $a la plana crece: $antes -> $ahora")
            assertTrue(antes - ahora <= hoja / 100, "con $a la plana salta: $antes -> $ahora")
            antes = ahora
            a += 0.001f
        }
    }

    @Test fun `atras es el espejo de adelante`() {
        fun espejo(o: Offset) = Offset(ancho - o.x, o.y)
        for (a in avances + listOf(0f, 1f)) {
            val ade = HojaQueSeDobla.pliegue(ancho, alto, a, adelante = true)
            val atr = HojaQueSeDobla.pliegue(ancho, alto, a, adelante = false)
            assertEquals(ade.plana.map(::espejo), atr.plana)
            assertEquals(ade.solapa.map(::espejo), atr.solapa)
            assertEquals(espejo(ade.ejeDesde), atr.ejeDesde)
            assertEquals(espejo(ade.ejeHasta), atr.ejeHasta)
        }
    }

    // --- reflejar -----------------------------------------------------------

    private val ejes = listOf(
        Offset(5f, 0f) to Offset(5f, 10f),    // vertical
        Offset(0f, 4f) to Offset(10f, 4f),    // horizontal
        Offset(0f, 0f) to Offset(10f, 10f),   // oblicuo, y = x
        Offset(1f, 7f) to Offset(4f, -2f)     // oblicuo cualquiera
    )

    @Test fun `reflejar deja quieto un punto del eje`() {
        for ((desde, hasta) in ejes) {
            for (t in listOf(-1.5f, 0f, 0.3f, 1f, 2f)) {
                val p = Offset(desde.x + (hasta.x - desde.x) * t, desde.y + (hasta.y - desde.y) * t)
                assertTrue(cerca(HojaQueSeDobla.reflejar(p, desde, hasta), p), "$p sobre $desde-$hasta se mueve")
            }
        }
    }

    @Test fun `reflejar dos veces devuelve el punto`() {
        for ((desde, hasta) in ejes) {
            for (p in listOf(Offset(8f, 3f), Offset(-2f, 11f), Offset(0f, 0f))) {
                val dos = HojaQueSeDobla.reflejar(HojaQueSeDobla.reflejar(p, desde, hasta), desde, hasta)
                assertTrue(cerca(dos, p), "$p sobre $desde-$hasta vuelve como $dos")
            }
        }
    }

    @Test fun `reflejar da lo que toca con un eje vertical uno horizontal y uno oblicuo`() {
        assertTrue(cerca(HojaQueSeDobla.reflejar(Offset(8f, 3f), Offset(5f, 0f), Offset(5f, 10f)), Offset(2f, 3f)))
        assertTrue(cerca(HojaQueSeDobla.reflejar(Offset(1f, 10f), Offset(0f, 4f), Offset(10f, 4f)), Offset(1f, -2f)))
        assertTrue(cerca(HojaQueSeDobla.reflejar(Offset(3f, 1f), Offset(0f, 0f), Offset(10f, 10f)), Offset(1f, 3f)))
    }
}
