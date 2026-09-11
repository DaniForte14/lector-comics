package com.dani.lector.datos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Bocadillos.globos` decide que se amplia y en que orden, y **si se tuerce no
 * da ningun error**: el lector salta un globo, amplia un trozo de cielo o lee
 * la fila de abajo antes que la de arriba.
 *
 * LAS PAGINAS SE PINTAN AQUI, en un `IntArray`, y no con un `ImageBitmap`: en
 * `commonTest` no se puede construir uno (ver `ColorPortadaTest`). Los globos
 * son rectangulos con borde, que para el relleno es lo mismo que una elipse, y
 * el texto son palotes de tinta con hueco entre ellos: lo que importa es que el
 * relleno tenga que rodear letras, no que se lean.
 *
 * LOS GLOBOS SE COMPARAN POR SU INTERIOR EXACTO, no por "hay uno": asi se
 * prueba a la vez que el borde para el relleno y que las letras, que son
 * agujeros, no encogen el recuadro.
 */
class BocadillosTest {

    private class Pagina(val ancho: Int, val alto: Int, fondo: Int) {
        val px = IntArray(ancho * alto) { fondo }
        var lecturas = 0

        fun rect(izq: Int, arriba: Int, der: Int, abajo: Int, color: Int) {
            for (y in arriba until abajo) for (x in izq until der) px[y * ancho + x] = color
        }

        /** Un globo: [relleno] con borde negro de 2 px. Su interior es la caja menos 2 por lado. */
        fun globo(izq: Int, arriba: Int, der: Int, abajo: Int, relleno: Int = BLANCO) {
            rect(izq, arriba, der, abajo, NEGRO)
            rect(izq + 2, arriba + 2, der - 2, abajo - 2, relleno)
        }

        /** Una linea de texto —palotes de 2 px cada 4— y su caja, como la daria el OCR. */
        fun linea(izq: Int, arriba: Int, der: Int, abajo: Int): Recuadro {
            var x = izq
            while (x < der) {
                rect(x, arriba, minOf(x + 2, der), abajo, NEGRO)
                x += 4
            }
            return Recuadro(izq, arriba, der, abajo)
        }

        fun globos(lineas: List<Recuadro>) = Bocadillos.globos(lineas, ancho, alto) { x, y ->
            lecturas++
            px[y * ancho + x]
        }
    }

    @Test fun `un globo blanco con borde negro da un globo`() {
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        val l = p.linea(140, 124, 260, 136)
        assertEquals(listOf(Recuadro(102, 82, 298, 178)), p.globos(listOf(l)))
    }

    @Test fun `un globo con ruido de escaneo pasado a 565 sale entero`() {
        // Lo que llega de verdad en Android: `ComicZip` decodifica en RGB_565
        // las paginas de 1200 de ancho o mas, y la sonda pide 1600. Aqui el
        // blanco lleva hasta 12 de ruido por canal, como un JPEG, y despues se
        // cuantiza a 565 como lo hace Android: rojo y azul en pasos de 8, verde
        // en pasos de 4. Guarda contra una tolerancia por debajo de ese escalon
        // —o contra comparar colores exactos—: con 8 falla y con 12 ya pasa.
        // NO sujeta el 60: el relleno rodea los pixeles que no pasan, asi que
        // apretar la tolerancia casi no se nota hasta que no pasa casi ninguno.
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        for (y in 82 until 178) for (x in 102 until 298) {
            p.px[y * 400 + x] = a565(
                255 - (x * 7 + y * 13) % 13,
                255 - (x * 11 + y * 5) % 13,
                255 - (x * 3 + y * 17) % 13
            )
        }
        val l = p.linea(140, 124, 260, 136)
        assertEquals(listOf(Recuadro(102, 82, 298, 178)), p.globos(listOf(l)))
    }

    @Test fun `dos lineas del mismo globo dan un solo globo`() {
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        val a = p.linea(140, 110, 260, 122)
        val b = p.linea(150, 126, 250, 138)
        assertEquals(listOf(Recuadro(102, 82, 298, 178)), p.globos(listOf(a, b)))
    }

    @Test fun `dos bloques de texto del mismo globo dan un solo globo`() {
        // Entre el segundo renglon de arriba y el de abajo hay 82 px: mas de una
        // linea, asi que son dos bloques. Los junta el relleno, no la cercania.
        val p = Pagina(400, 300, DIBUJO)
        p.globo(60, 40, 340, 260)
        val lineas = listOf(
            p.linea(100, 70, 300, 82),
            p.linea(110, 86, 290, 98),
            p.linea(100, 180, 300, 192)
        )
        assertEquals(listOf(Recuadro(62, 42, 338, 258)), p.globos(lineas))
    }

    @Test fun `el texto sobre el dibujo sin globo se descarta`() {
        // Un cielo claro sin contorno: el relleno se escapa hasta la ventana.
        val p = Pagina(400, 300, CIELO)
        val l = p.linea(140, 124, 260, 136)
        assertEquals(emptyList(), p.globos(listOf(l)))
    }

    @Test fun `el texto sobre un dibujo oscuro se descarta`() {
        // Sin nada claro en la caja no hay de que color buscar el globo.
        val p = Pagina(400, 300, DIBUJO)
        val l = p.linea(140, 124, 260, 136)
        assertEquals(emptyList(), p.globos(listOf(l)))
    }

    @Test fun `una cartela amarilla cuenta como globo`() {
        // Sobre una pagina BLANCA a proposito: si se supusiera que el globo es
        // blanco, se rellenaria la pagina y se descartaria.
        val p = Pagina(400, 300, BLANCO)
        p.globo(100, 40, 300, 100, relleno = AMARILLO)
        val l = p.linea(120, 64, 280, 76)
        assertEquals(listOf(Recuadro(102, 42, 298, 98)), p.globos(listOf(l)))
    }

    @Test fun `un globo que toca el borde de la pagina se descarta`() {
        // Sin borde por la izquierda: el blanco llega hasta la columna 0.
        val p = Pagina(400, 300, DIBUJO)
        p.rect(0, 80, 200, 180, NEGRO)
        p.rect(0, 82, 198, 178, BLANCO)
        val l = p.linea(40, 124, 160, 136)
        assertEquals(emptyList(), p.globos(listOf(l)))
    }

    @Test fun `el orden es por filas de arriba abajo y de izquierda a derecha`() {
        // Las filas no van alineadas al pixel, como en una pagina de verdad: el
        // de arriba a la derecha baja 10 y el de abajo a la derecha sube 10.
        val p = Pagina(400, 300, DIBUJO)
        p.globo(20, 20, 180, 110)
        p.globo(220, 30, 380, 120)
        p.globo(20, 160, 180, 250)
        p.globo(220, 150, 380, 240)
        val arribaIzq = p.linea(50, 59, 150, 71)
        val arribaDer = p.linea(250, 69, 350, 81)
        val abajoIzq = p.linea(50, 199, 150, 211)
        val abajoDer = p.linea(250, 189, 350, 201)
        assertEquals(
            listOf(
                Recuadro(22, 22, 178, 108),
                Recuadro(222, 32, 378, 118),
                Recuadro(22, 162, 178, 248),
                Recuadro(222, 152, 378, 238)
            ),
            p.globos(listOf(abajoDer, arribaIzq, abajoIzq, arribaDer))
        )
    }

    @Test fun `sin lineas no hay globos y no se mira ni un pixel`() {
        assertEquals(emptyList(), Bocadillos.globos(emptyList(), 100, 100) { _, _ ->
            error("sin lineas no hay nada que mirar")
        })
    }

    @Test fun `solo se leen los pixeles de la ventana y no la pagina entera`() {
        // Es lo que acota el tiempo en Android, donde cada lectura es un
        // `Bitmap.getPixel`: un globo pequeño en una pagina grande no puede
        // costar la pagina entera.
        val p = Pagina(1000, 1000, DIBUJO)
        p.globo(450, 450, 600, 540)
        val l = p.linea(480, 490, 570, 502)
        assertEquals(listOf(Recuadro(452, 452, 598, 538)), p.globos(listOf(l)))
        assertTrue(p.lecturas < 1000 * 1000 / 10, "se leyeron ${p.lecturas} pixeles")
    }

    /**
     * El color como lo devuelve `getPixel` de un bitmap en RGB_565: cada canal
     * cuantizado a 5 o 6 bits y vuelto a 8 repitiendo sus bits altos, que es
     * como Android lo expande al leerlo (31 da 255, no 248).
     */
    private fun a565(r: Int, g: Int, b: Int): Int {
        val r5 = r shr 3
        val g6 = g shr 2
        val b5 = b shr 3
        return (0xFF shl 24) or
            (((r5 shl 3) or (r5 shr 2)) shl 16) or
            (((g6 shl 2) or (g6 shr 4)) shl 8) or
            ((b5 shl 3) or (b5 shr 2))
    }

    private companion object {
        val NEGRO = 0xFF000000.toInt()
        val BLANCO = 0xFFFFFFFF.toInt()
        /** Un azul de dibujo, oscuro: no pasa por claro. */
        val DIBUJO = 0xFF2A4D8F.toInt()
        val CIELO = 0xFFDDEEFF.toInt()
        val AMARILLO = 0xFFF5E06E.toInt()
    }
}
