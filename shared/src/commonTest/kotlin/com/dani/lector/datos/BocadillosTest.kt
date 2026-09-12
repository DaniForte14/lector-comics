package com.dani.lector.datos

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * LOS GLOBOS SE COMPARAN POR SU RECUADRO EXACTO, no por "hay uno". Desde la
 * tanda 30 el recuadro es el globo ENTERO, trazo incluido —quien pinta recorta
 * por el, y un trazo fuera no se veria—, asi que con las letras de 12 px el
 * trazo que se suma son 2 px y el recuadro es justo el rectangulo pintado.
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

        // El recuadro y no el Globo entero: estas pruebas son de que se amplia y
        // en que orden; la forma del contorno tiene las suyas.
        fun globos(lineas: List<Recuadro>) = Bocadillos.globos(lineas, ancho, alto) { x, y ->
            lecturas++
            px[y * ancho + x]
        }.map { it.recuadro }

        /** Igual, pero con las viñetas puestas a mano. */
        fun globosEn(lineas: List<Recuadro>, vinetas: List<Recuadro>) =
            Bocadillos.globosEn(lineas, ancho, alto, vinetas) { x, y -> px[y * ancho + x] }.map { it.recuadro }
    }

    @Test fun `un globo blanco con borde negro da un globo`() {
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        val l = p.linea(140, 124, 260, 136)
        assertEquals(listOf(Recuadro(100, 80, 300, 180)), p.globos(listOf(l)))
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
        assertEquals(listOf(Recuadro(100, 80, 300, 180)), p.globos(listOf(l)))
    }

    @Test fun `dos lineas del mismo globo dan un solo globo`() {
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        val a = p.linea(140, 110, 260, 122)
        val b = p.linea(150, 126, 250, 138)
        assertEquals(listOf(Recuadro(100, 80, 300, 180)), p.globos(listOf(a, b)))
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
        assertEquals(listOf(Recuadro(60, 40, 340, 260)), p.globos(lineas))
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
        assertEquals(listOf(Recuadro(100, 40, 300, 100)), p.globos(listOf(l)))
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
                Recuadro(20, 20, 180, 110),
                Recuadro(220, 30, 380, 120),
                Recuadro(20, 160, 180, 250),
                Recuadro(220, 150, 380, 240)
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
        assertEquals(listOf(Recuadro(450, 450, 600, 540)), p.globos(listOf(l)))
        assertTrue(p.lecturas < 1000 * 1000 / 10, "se leyeron ${p.lecturas} pixeles")
    }

    // --- Las viñetas ---------------------------------------------------------

    @Test fun `en una fila de tres vinetas el globo alto de la derecha va el ultimo`() {
        // El caso de Absolute Batman #01, pag. 5: sin viñetas, el globo de la
        // derecha se leia antes que los dos de en medio solo por estar mas alto.
        val p = Pagina(400, 300, BLANCO)
        p.rect(10, 10, 130, 290, DIBUJO)
        p.rect(140, 10, 260, 290, DIBUJO)
        p.rect(270, 10, 390, 290, DIBUJO)
        p.globo(20, 100, 120, 150)
        p.globo(150, 120, 250, 170)
        p.globo(150, 200, 250, 250)
        p.globo(280, 20, 380, 70)
        val lineas = listOf(
            p.linea(300, 39, 360, 51),
            p.linea(170, 219, 230, 231),
            p.linea(40, 119, 100, 131),
            p.linea(170, 139, 230, 151)
        )
        assertEquals(
            listOf(
                Recuadro(20, 100, 120, 150),
                Recuadro(150, 120, 250, 170),
                Recuadro(150, 200, 250, 250),
                Recuadro(280, 20, 380, 70)
            ),
            p.globos(lineas)
        )
    }

    @Test fun `un globo abierto a la calle sale cortado en el borde de su vineta`() {
        // Sin borde por arriba y pegado al techo de la viñeta: su blanco sigue
        // por el margen blanco de la pagina. Antes el relleno se escapaba por
        // ahi y el globo no salia; ahora se corta donde empieza la viñeta, que
        // es la fila 10. Por los otros tres lados lleva su trazo.
        val p = Pagina(400, 300, BLANCO)
        p.rect(10, 10, 195, 145, DIBUJO)
        p.rect(205, 10, 390, 145, DIBUJO)
        p.rect(10, 155, 195, 290, DIBUJO)
        p.rect(205, 155, 390, 290, DIBUJO)
        p.rect(38, 10, 162, 72, NEGRO)
        p.rect(40, 10, 160, 70, BLANCO)
        val l = p.linea(70, 34, 130, 46)
        assertEquals(listOf(Recuadro(38, 10, 162, 72)), p.globos(listOf(l)))
    }

    @Test fun `un globo en el margen de arriba se lee con la vineta mas cercana`() {
        // La pagina de Green Lantern: dos globos arriba a la izquierda que
        // empiezan en el margen y pisan la primera viñeta, con el centro del
        // texto FUERA de toda viñeta, y uno pequeño mas abajo a la derecha. Antes
        // lo que no caia en ninguna viñeta iba al final, y se leia primero el
        // pequeño. Y su relleno no se recorta a esa viñeta: saldria cortado por
        // la fila 50, que es donde empieza.
        val p = Pagina(400, 300, BLANCO)
        val vinetas = listOf(
            Recuadro(10, 50, 195, 145), Recuadro(205, 50, 390, 145),
            Recuadro(10, 155, 195, 290), Recuadro(205, 155, 390, 290)
        )
        for (v in vinetas) p.rect(v.izq, v.arriba, v.der, v.abajo, DIBUJO)
        p.globo(20, 10, 110, 70)
        p.globo(115, 15, 185, 72)
        p.globo(300, 80, 370, 120)
        val pequeno = p.linea(315, 94, 355, 106)
        val primero = p.linea(40, 24, 90, 36)
        val segundo = p.linea(130, 30, 170, 42)
        val esperado = listOf(Recuadro(20, 10, 110, 70), Recuadro(115, 15, 185, 72), Recuadro(300, 80, 370, 120))
        assertEquals(esperado, p.globosEn(listOf(pequeno, primero, segundo), vinetas))
        // Y con las viñetas que encuentre la pagina sola, el mismo orden.
        assertEquals(esperado, p.globos(listOf(pequeno, primero, segundo)))
    }

    // --- El contorno ---------------------------------------------------------
    //
    // Se prueba la FORMA del poligono, no sus puntos uno a uno: que puntos
    // exactos salgan depende de donde empiece el recorrido y de la
    // simplificacion, y eso puede cambiar sin que el recorte se vea distinto.

    @Test fun `el contorno de un globo eliptico deja fuera las esquinas del recuadro`() {
        // Si el contorno fuera el recuadro, sus esquinas estarian dentro.
        val (p, lineas) = eliptica()
        val g = p.completos(lineas).single()
        for ((x, y) in esquinas(g.recuadro)) {
            assertFalse(g.contorno.contiene(x, y), "la esquina ($x, $y) cae dentro")
        }
        assertCajasDentro(g, lineas)
    }

    @Test fun `el contorno de un globo rectangular ocupa casi todo su recuadro`() {
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        val g = p.completos(listOf(p.linea(140, 124, 260, 136))).single()
        val r = g.recuadro
        assertTrue(area(g.contorno) >= 0.95 * r.ancho * r.alto, "area ${area(g.contorno)} de ${r.ancho * r.alto}")
    }

    @Test fun `una letra pegada al trazo queda dentro del contorno`() {
        // Lo que vio Dani: la ultima letra, en las columnas 296-297, toca el
        // trazo de la derecha, que empieza en la 298. El relleno la rodea por
        // dentro y deja una muesca justo encima; si el contorno la siguiera, el
        // recorte se llevaria la letra.
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        val l = p.linea(200, 124, 298, 136)
        val g = p.completos(listOf(l)).single()
        assertCajasDentro(g, listOf(l))
    }

    @Test fun `el contorno abarca el trazo del globo`() {
        // El trazo son las columnas 100-101 y 298-299 y las filas 80-81 y
        // 178-179. Tiene que quedar dentro, y el dibujo de fuera no.
        val p = Pagina(400, 300, DIBUJO)
        p.globo(100, 80, 300, 180)
        val g = p.completos(listOf(p.linea(140, 124, 260, 136))).single()
        for ((x, y) in listOf(100.5 to 130.5, 298.5 to 130.5, 200.5 to 80.5, 200.5 to 178.5)) {
            assertTrue(g.contorno.contiene(x, y), "el trazo ($x, $y) se queda fuera")
        }
        for ((x, y) in listOf(95.5 to 130.5, 304.5 to 130.5, 200.5 to 75.5, 200.5 to 184.5)) {
            assertFalse(g.contorno.contiene(x, y), "el dibujo ($x, $y) entra")
        }
    }

    @Test fun `el contorno no pasa del tope de puntos`() {
        // Un borde con dieciseis ondas: sin tope, la simplificacion se quedaria
        // con mas de 64 puntos. Y aun aflojada tiene que seguir envolviendo el
        // texto, que es lo que no se puede perder.
        val (p, lineas) = ondulada()
        val g = p.completos(lineas).single()
        assertTrue(g.contorno.size <= 64, "${g.contorno.size} puntos")
        assertCajasDentro(g, lineas)
    }

    @Test fun `todos los puntos del contorno caen dentro del recuadro`() {
        for ((p, lineas) in listOf(eliptica(), ondulada(), unidos())) {
            for (g in p.completos(lineas)) {
                val r = g.recuadro
                for (q in g.contorno) {
                    assertTrue(q.x in r.izq..r.der && q.y in r.arriba..r.abajo, "$q fuera de $r")
                }
            }
        }
    }

    @Test fun `el contorno de dos globos unidos no se come el dibujo de entre medias`() {
        // Es el caso por el que el contorno se saca siguiendo el borde, y no con
        // rayos desde el texto, ni con el primer y el ultimo pixel de cada fila,
        // ni con la envolvente convexa: el punto (265, 110) esta dentro del
        // recuadro, en la muesca entre los dos globos, y es dibujo. Ensanchar
        // lo del trazo (3 px aqui) no la tapa: mide mas de 40.
        val (p, lineas) = unidos()
        val g = p.completos(lineas).single()
        assertTrue(265 in g.recuadro.izq until g.recuadro.der && 110 in g.recuadro.arriba until g.recuadro.abajo)
        assertFalse(g.contorno.contiene(265.5, 110.5), "se come el dibujo de entre los globos")
        assertTrue(g.contorno.contiene(265.5, 168.5), "se deja el cuello")
        assertCajasDentro(g, lineas)
    }

    @Test fun `al fusionar dos bloques el contorno envuelve los dos`() {
        val p = Pagina(400, 300, DIBUJO)
        p.globo(60, 40, 340, 260)
        val lineas = listOf(
            p.linea(100, 70, 300, 82),
            p.linea(110, 86, 290, 98),
            p.linea(100, 180, 300, 192)
        )
        assertCajasDentro(p.completos(lineas).single(), lineas)
    }

    /** Un globo eliptico con borde negro de 2 px y una linea dentro. */
    private fun eliptica(): Pair<Pagina, List<Recuadro>> {
        val p = Pagina(400, 300, DIBUJO)
        p.elipse(200, 150, 122, 72, NEGRO)
        p.elipse(200, 150, 120, 70, BLANCO)
        return p to listOf(p.linea(150, 140, 250, 152))
    }

    /** Un globo de radio 150 con dieciseis ondas de 20, y tres lineas altas dentro. */
    private fun ondulada(): Pair<Pagina, List<Recuadro>> {
        val p = Pagina(500, 500, DIBUJO)
        fun radio(x: Int, y: Int) = sqrt(((x - 250) * (x - 250) + (y - 250) * (y - 250)).toDouble())
        fun onda(x: Int, y: Int) = 150 + 20 * sin(16 * atan2((y - 250).toDouble(), (x - 250).toDouble()))
        p.pinta(NEGRO) { x, y -> radio(x, y) <= onda(x, y) + 3 }
        p.pinta(BLANCO) { x, y -> radio(x, y) <= onda(x, y) }
        return p to listOf(
            p.linea(150, 212, 350, 232),
            p.linea(150, 237, 350, 257),
            p.linea(150, 262, 350, 282)
        )
    }

    /**
     * Dos globos elipticos, uno grande a la izquierda y otro pequeño a la
     * derecha, unidos por un cuello bajo. Primero todo lo negro y luego todo lo
     * blanco, para que el borde quede solo por fuera de la union.
     *
     * Solo el bloque del globo grande llega a ver la union entera dentro de su
     * ventana; el del pequeño se descarta por tocarla. Da igual: con uno basta.
     */
    private fun unidos(): Pair<Pagina, List<Recuadro>> {
        val p = Pagina(500, 300, DIBUJO)
        p.elipse(150, 130, 102, 57, NEGRO)
        p.elipse(330, 130, 52, 47, NEGRO)
        p.rect(228, 158, 302, 178, NEGRO)
        p.elipse(150, 130, 100, 55, BLANCO)
        p.elipse(330, 130, 50, 45, BLANCO)
        p.rect(230, 160, 300, 176, BLANCO)
        return p to listOf(
            p.linea(80, 110, 220, 126),
            p.linea(90, 130, 210, 146),
            p.linea(310, 124, 350, 136)
        )
    }

    private fun Pagina.pinta(color: Int, dentro: (Int, Int) -> Boolean) {
        for (y in 0 until alto) for (x in 0 until ancho) if (dentro(x, y)) px[y * ancho + x] = color
    }

    private fun Pagina.elipse(cx: Int, cy: Int, rx: Int, ry: Int, color: Int) = pinta(color) { x, y ->
        val dx = (x - cx).toDouble() / rx
        val dy = (y - cy).toDouble() / ry
        dx * dx + dy * dy <= 1.0
    }

    /** Los globos enteros, con su contorno. */
    private fun Pagina.completos(lineas: List<Recuadro>) =
        Bocadillos.globos(lineas, ancho, alto) { x, y -> px[y * ancho + x] }

    /**
     * Que NINGUN pixel de las cajas de texto caiga fuera del contorno, por su
     * centro. Se miran todos los del filo de cada caja: para meterse dentro de
     * una caja, el poligono tiene que cruzar su filo.
     */
    private fun assertCajasDentro(g: Globo, lineas: List<Recuadro>) {
        for (l in lineas) {
            val filo = (l.izq until l.der).flatMap { x -> listOf(x to l.arriba, x to l.abajo - 1) } +
                (l.arriba until l.abajo).flatMap { y -> listOf(l.izq to y, l.der - 1 to y) }
            for ((x, y) in filo) {
                assertTrue(g.contorno.contiene(x + 0.5, y + 0.5), "la letra ($x, $y) se queda fuera")
            }
        }
    }

    /** Las cuatro esquinas de un recuadro, medio pixel hacia dentro para no caer justo en un lado. */
    private fun esquinas(r: Recuadro) = listOf(
        r.izq + 0.5 to r.arriba + 0.5, r.der - 0.5 to r.arriba + 0.5,
        r.der - 0.5 to r.abajo - 0.5, r.izq + 0.5 to r.abajo - 0.5
    )

    /** Punto en poligono, par-impar: cuantas veces corta los lados un rayo hacia la derecha. */
    private fun List<Punto>.contiene(x: Double, y: Double): Boolean {
        var dentro = false
        var j = size - 1
        for (i in indices) {
            val a = this[i]
            val b = this[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) dentro = !dentro
            j = i
        }
        return dentro
    }

    /** El area de un poligono, por la formula del cordon. */
    private fun area(p: List<Punto>): Double {
        var doble = 0L
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            doble += a.x.toLong() * b.y - b.x.toLong() * a.y
        }
        return abs(doble) / 2.0
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
