package com.dani.lector.datos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Vinetas.de` decide el orden en que se leen los globos y donde se corta uno
 * que rompe el marco, y **si se tuerce no da ningun error**: los globos salen
 * desordenados o cortados por una calle que no existe.
 *
 * Las paginas se pintan en un `IntArray`, como en `BocadillosTest`: el dibujo
 * es un color liso donde basta y ruido donde hace falta que no parezca calle.
 */
class VinetasTest {

    private class Pagina(val ancho: Int, val alto: Int, fondo: Int) {
        val px = IntArray(ancho * alto) { fondo }
        var lecturas = 0

        fun rect(izq: Int, arriba: Int, der: Int, abajo: Int, color: Int) {
            for (y in arriba until abajo) for (x in izq until der) px[y * ancho + x] = color
        }

        /** Un dibujo que no se parece a si mismo de un pixel al siguiente, ni al blanco ni al negro. */
        fun ruido(r: Recuadro) {
            for (y in r.arriba until r.abajo) for (x in r.izq until r.der) {
                px[y * ancho + x] = (0xFF shl 24) or
                    (((x * 37 + y * 91) % 180 + 40) shl 16) or
                    (((x * 53 + y * 17) % 180 + 40) shl 8) or
                    ((x * 29 + y * 71) % 180 + 40)
            }
        }

        fun vinetas() = Vinetas.de(ancho, alto) { x, y ->
            lecturas++
            px[y * ancho + x]
        }
    }

    /** Cuatro viñetas con margen de 10 y calles de 10, sobre una pagina del color de la calle. */
    private fun rejilla(calle: Int): Pagina {
        val p = Pagina(400, 300, calle)
        for (r in REJILLA) p.rect(r.izq, r.arriba, r.der, r.abajo, DIBUJO)
        return p
    }

    @Test fun `una rejilla de 2x2 con calles blancas da cuatro vinetas en orden`() {
        assertEquals(REJILLA, rejilla(BLANCO).vinetas())
    }

    @Test fun `una rejilla de 2x2 con calles negras da cuatro vinetas en orden`() {
        assertEquals(REJILLA, rejilla(NEGRO).vinetas())
    }

    @Test fun `una pagina sin calles es una sola vineta`() {
        // Dibujo hasta el borde: ni un borde liso de donde sacar la calle.
        val p = Pagina(400, 300, NEGRO)
        p.ruido(Recuadro(0, 0, 400, 300))
        assertEquals(listOf(Recuadro(0, 0, 400, 300)), p.vinetas())
    }

    @Test fun `el margen se quita y una vineta sola queda con su borde`() {
        val p = Pagina(400, 300, BLANCO)
        p.rect(20, 20, 380, 280, DIBUJO)
        assertEquals(listOf(Recuadro(20, 20, 380, 280)), p.vinetas())
    }

    @Test fun `una calle mas fina que el grosor minimo no corta`() {
        // Dos pixeles entre dos manchas es un hueco del dibujo, no una calle.
        val p = Pagina(400, 300, BLANCO)
        p.rect(10, 10, 390, 144, DIBUJO)
        p.rect(10, 146, 390, 290, DIBUJO)
        assertEquals(listOf(Recuadro(10, 10, 390, 290)), p.vinetas())
    }

    @Test fun `una franja blanca dentro de una vineta de fondo blanco no es calle`() {
        // Lo que se salta la pasada rala: una viñeta blanca con marco negro de
        // 2 px, y en medio cien filas blancas de lado a lado. Con muestras cada
        // 12 px el marco no sale, y parecen calle; la linea entera si lo ve.
        val p = Pagina(400, 300, BLANCO)
        p.rect(10, 10, 390, 290, NEGRO)
        p.rect(12, 12, 388, 288, BLANCO)
        p.rect(30, 30, 370, 100, DIBUJO)
        p.rect(30, 200, 370, 270, DIBUJO)
        assertEquals(listOf(Recuadro(10, 10, 390, 290)), p.vinetas())
    }

    @Test fun `detectar las vinetas lee muy poco de la pagina`() {
        // Una pagina de 800x1200 con nueve viñetas de dibujo: en una fila de
        // dibujo la segunda o tercera muestra ya no cuadra, asi que casi todo
        // lo que se lee son las calles.
        val p = Pagina(800, 1200, BLANCO)
        val esperadas = mutableListOf<Recuadro>()
        for (fila in 0 until 3) for (columna in 0 until 3) {
            val r = Recuadro(20 + columna * 260, 20 + fila * 393, 260 + columna * 260, 393 + fila * 393)
            p.ruido(r)
            esperadas += r
        }
        assertEquals(esperadas, p.vinetas())
        assertTrue(p.lecturas < 800 * 1200 / 10, "se leyeron ${p.lecturas} pixeles")
    }

    @Test fun `un margen negro con calles grises da cuatro vinetas en orden`() {
        // Green Lantern Corps Recharge, segun la captura de Dani: margen oscuro y
        // calles grises. Con el color de la calle sacado del margen no cortaba
        // nada; y la fila de una calle, de lado a lado, pisa el margen negro de
        // los lados y no sale lisa si no se quita antes el margen.
        val p = Pagina(400, 300, NEGRO)
        p.rect(10, 10, 390, 290, GRIS)
        for (r in REJILLA) p.rect(r.izq, r.arriba, r.der, r.abajo, DIBUJO)
        assertEquals(REJILLA, p.vinetas())
    }

    @Test fun `una pagina a sangre con calles blancas se corta igual`() {
        // Daredevil #006, medido: el dibujo llega a los cuatro bordes y las calles
        // son blancas. Sin un borde liso del que sacar el color, las 25 paginas
        // salian como una sola viñeta.
        // Calles de 10 px, un 3% de la pagina, como las de verdad (Daredevil, 17-22
        // filas de 1574).
        val p = Pagina(400, 300, BLANCO)
        p.ruido(Recuadro(0, 0, 400, 140))
        p.ruido(Recuadro(0, 150, 195, 300))
        p.ruido(Recuadro(205, 150, 400, 300))
        assertEquals(
            listOf(Recuadro(0, 0, 400, 140), Recuadro(0, 150, 195, 300), Recuadro(205, 150, 400, 300)),
            p.vinetas()
        )
    }

    @Test fun `una franja negra lisa a lo ancho de una vineta no la corta`() {
        // El otro riesgo de aceptar calles de cualquier color neutro: una noche o
        // una sombra que cruza una viñeta enmarcada de lado a lado. Con el marco,
        // que tambien es negro, la fila sale lisa de punta a punta. Lo que la
        // salva es el grosor: una calle de verdad no pasa del 1,4% de la pagina
        // (medido) y esta franja es un 27%.
        val p = Pagina(400, 300, BLANCO)
        p.rect(10, 10, 390, 290, NEGRO)
        p.ruido(Recuadro(12, 12, 388, 100))
        p.ruido(Recuadro(12, 180, 388, 288))
        assertEquals(listOf(Recuadro(10, 10, 390, 290)), p.vinetas())
    }

    @Test fun `un cielo liso a lo ancho de una vineta no la corta`() {
        // El riesgo de aceptar calles de cualquier color: una viñeta a sangre por
        // los lados, con una franja de cielo liso de lado a lado, tiene filas tan
        // uniformes como una calle. La franja es de 10 px, tan fina como una
        // calle, para que no la salve el grosor maximo sino el color: una calle
        // es blanca, gris o negra, y el cielo es azul.
        val p = Pagina(400, 300, BLANCO)
        p.ruido(Recuadro(0, 20, 400, 140))
        p.rect(0, 140, 400, 150, AZUL)
        p.ruido(Recuadro(0, 150, 400, 280))
        assertEquals(listOf(Recuadro(0, 20, 400, 280)), p.vinetas())
    }

    private companion object {
        val GRIS = 0xFF8C8C8C.toInt()
        val AZUL = 0xFF87CEEB.toInt()
        val NEGRO = 0xFF000000.toInt()
        val BLANCO = 0xFFFFFFFF.toInt()
        val DIBUJO = 0xFF2A4D8F.toInt()
        val REJILLA = listOf(
            Recuadro(10, 10, 195, 145), Recuadro(205, 10, 390, 145),
            Recuadro(10, 155, 195, 290), Recuadro(205, 155, 390, 290)
        )
    }
}
