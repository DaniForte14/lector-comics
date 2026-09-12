package com.dani.lector.datos

import kotlin.math.abs

/**
 * Las viñetas de una pagina, en orden de lectura, cortando por las calles: las
 * franjas lisas que las separan. Es lo que da a [Bocadillos] dos cosas que sin
 * esto no sabe: en que orden van los globos cuando las viñetas no estan
 * alineadas, y donde acaba la viñeta cuando un globo se abre a la calle.
 *
 * CORTES RECURSIVOS: primero las filas que cruzan la pagina entera, luego las
 * columnas dentro de cada banda, luego filas otra vez... Como un corte de
 * guillotina, y en ese mismo orden se lee.
 *
 * LO RALO PROPONE Y LO DENSO CONFIRMA. Cada fila se mira con [MUESTRAS]
 * muestras, como [Recorte], y en una fila de dibujo la segunda o tercera ya no
 * cuadra, asi que cuesta dos o tres lecturas. Pero con muestras sueltas se
 * escapa la linea del marco de una viñeta, que son dos pixeles, y peor: una
 * fila que cruza un globo blanco en una viñeta de fondo blanco parece calle.
 * Por eso ninguna calle se da por buena hasta leer su linea central ENTERA, y
 * los margenes se ajustan igual, linea entera a linea entera. Son unas pocas
 * lineas por pagina.
 *
 * ponytail: guillotina y nada mas. Calles en diagonal, viñetas que se montan,
 * viñetas insertadas dentro de otra o dibujo que cruza de una a otra no se
 * cortan, y lo que no se corta se queda como una viñeta mas grande, que es
 * como funcionaba todo antes de esto. Si hace falta, el siguiente paso es
 * buscar los marcos en vez de las calles.
 */
object Vinetas {

    /** Muestras por linea en la pasada rala. Ver arriba por que no hacen falta mas. */
    private const val MUESTRAS = 32

    /**
     * Cuanto se puede alejar un pixel del color de la calle, en suma de
     * diferencias RGB. Mas que los 28 de [Recorte] porque aqui entra la pagina
     * en RGB_565 (ver `Bocadillos.TOLERANCIA`: ~20 contra la media), y menos
     * que los 60 del globo porque un cielo palido esta a unos 55 del blanco y
     * no es una calle.
     */
    private const val TOLERANCIA = 40

    /**
     * Cuantas veces se corta, como mucho: pagina en bandas, banda en columnas,
     * columna en viñetas apiladas, y alguna mas. Una maquetacion normal no pasa
     * de tres; esto acota el tiempo en una que se cuele.
     */
    private const val PROFUNDIDAD = 5

    fun de(ancho: Int, alto: Int, pixel: (x: Int, y: Int) -> Int): List<Recuadro> {
        val pagina = Recuadro(0, 0, ancho, alto)
        if (ancho < 60 || alto < 60) return listOf(pagina)
        val calle = colorDeCalle(ancho, alto, pixel) ?: return listOf(pagina)
        return Cortador(ancho, alto, calle, pixel).partir(pagina, filas = true, profundidad = 0)
    }

    /**
     * El color de la calle, sacado de los cuatro bordes de la pagina: uno por
     * lado, un poco hacia dentro para no pisar el filo del escaneo. Las hay
     * blancas y negras, asi que no se supone ninguna. Null si ningun borde es
     * liso: una pagina a sangre, que se queda como una sola viñeta.
     *
     * Solo los bordes y no la pagina entera, a proposito: es una docena de
     * muestras por lado, y la margen es de la calle casi siempre.
     */
    private fun colorDeCalle(ancho: Int, alto: Int, pixel: (x: Int, y: Int) -> Int): Int? {
        val bordes = listOf(
            List(MUESTRAS) { pixel(it * ancho / MUESTRAS, alto / 100) },
            List(MUESTRAS) { pixel(it * ancho / MUESTRAS, alto - 1 - alto / 100) },
            List(MUESTRAS) { pixel(ancho / 100, it * alto / MUESTRAS) },
            List(MUESTRAS) { pixel(ancho - 1 - ancho / 100, it * alto / MUESTRAS) }
        )
        // Liso: como mucho una muestra se aparta de la primera. Y se devuelve la
        // media de las que cuadran, no la primera, para que el ruido del
        // escaneo no lo decida un solo pixel.
        val lisos = bordes.mapNotNull { m ->
            val iguales = m.filter { distanciaRgb(it, m[0]) <= TOLERANCIA }
            if (iguales.size < m.size - 1) null else media(iguales)
        }
        // Si dos bordes no coinciden —margen blanca arriba y dibujo a sangre por
        // un lado que por casualidad sale liso—, manda el que mas se repite.
        return lisos.maxByOrNull { c -> lisos.count { distanciaRgb(it, c) <= TOLERANCIA } }
    }

    private fun media(colores: List<Int>): Int {
        var r = 0
        var g = 0
        var b = 0
        for (c in colores) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val n = colores.size
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    private class Cortador(
        val ancho: Int, val alto: Int, val calle: Int, val pixel: (x: Int, y: Int) -> Int
    ) {
        /**
         * Lo que tiene que medir una calle, en pixeles. Una calle de verdad son
         * entre el 1 y el 3% del ancho; por debajo de esto es el hueco entre dos
         * letras o entre dos figuras, no una separacion de viñetas.
         */
        val grosor = maxOf(4, minOf(ancho, alto) / 200)

        /**
         * Recursivo, pero con la profundidad acotada a [PROFUNDIDAD]: no es el
         * relleno, que va con pila porque son miles de pasos.
         */
        fun partir(r: Recuadro, filas: Boolean, profundidad: Int): List<Recuadro> {
            if (profundidad >= PROFUNDIDAD) return listOf(r)
            val trozos = trozos(r, filas)
            if (trozos.size > 1 || trozos[0] != r) return trozos.flatMap { partir(it, !filas, profundidad + 1) }
            // Por aqui no hay nada que cortar; se prueba en la otra direccion, y
            // si tampoco, es una viñeta.
            val otros = trozos(r, !filas)
            if (otros.size == 1 && otros[0] == r) return listOf(r)
            return otros.flatMap { partir(it, filas, profundidad + 1) }
        }

        /** [r] partido por sus calles en una direccion, y sin su margen. */
        fun trozos(r: Recuadro, filas: Boolean): List<Recuadro> {
            val n = if (filas) r.alto else r.ancho
            // Una viñeta de menos de un doceavo de pagina no existe: eso es un
            // hueco del dibujo que ha salido liso.
            val minimo = if (filas) alto / 12 else ancho / 12
            val lisa = BooleanArray(n) { lisa(r, filas, it, densa = false) }
            val a = lisa.indexOfFirst { !it }
            if (a < 0) return listOf(r)
            val b = lisa.indexOfLast { !it }
            if (b - a + 1 < minimo) return listOf(r)

            // EL MARGEN SOLO SE QUITA POR EL LADO QUE DA AL BORDE DE LA PAGINA.
            // Dentro de una viñeta, una franja lisa es fondo del dibujo y no una
            // calle, y quitarla moveria el borde de la viñeta hacia dentro.
            var ini = 0
            var fin = n
            val tocaIni = if (filas) r.arriba == 0 else r.izq == 0
            val tocaFin = if (filas) r.abajo == alto else r.der == ancho
            if (tocaIni && a >= grosor) {
                ini = a
                while (ini > 0 && !lisa(r, filas, ini - 1, densa = true)) ini--
            }
            if (tocaFin && n - 1 - b >= grosor) {
                fin = b + 1
                while (fin < n && !lisa(r, filas, fin, densa = true)) fin++
            }

            // Las calles de dentro. Cada viñeta acaba donde empieza la calle, con
            // el borde ajustado linea entera a linea entera, porque las muestras
            // sueltas pueden haberse saltado el marco.
            val piezas = mutableListOf<IntArray>()
            var desde = ini
            var i = a
            while (i <= b) {
                if (!lisa[i]) { i++; continue }
                var e = i
                while (e + 1 <= b && lisa[e + 1]) e++
                if (e - i + 1 >= grosor && lisa(r, filas, (i + e) / 2, densa = true)) {
                    var hasta = i
                    while (hasta < e && !lisa(r, filas, hasta, densa = true)) hasta++
                    var sigue = e + 1
                    while (sigue > hasta + 1 && !lisa(r, filas, sigue - 1, densa = true)) sigue--
                    if (hasta - desde >= minimo && fin - sigue >= minimo) {
                        piezas += intArrayOf(desde, hasta)
                        desde = sigue
                    }
                }
                i = e + 1
            }
            piezas += intArrayOf(desde, fin)
            return piezas.map { (d, h) ->
                if (filas) Recuadro(r.izq, r.arriba + d, r.der, r.arriba + h)
                else Recuadro(r.izq + d, r.arriba, r.izq + h, r.abajo)
            }
        }

        /**
         * Si la linea [i] de [r] —fila o columna— es calle. Rala: [MUESTRAS]
         * muestras y se perdona una, para el polvo del escaneo. Densa: todos los
         * pixeles y no se perdona ninguno, porque lo que se busca es justo la
         * linea fina que las muestras se saltan.
         */
        fun lisa(r: Recuadro, filas: Boolean, i: Int, densa: Boolean): Boolean {
            val largo = if (filas) r.ancho else r.alto
            val cuantas = if (densa) largo else minOf(MUESTRAS, largo)
            val perdon = if (densa) 0 else 1
            var fallos = 0
            for (k in 0 until cuantas) {
                val t = if (densa) k else k * largo / cuantas
                val p = if (filas) pixel(r.izq + t, r.arriba + i) else pixel(r.izq + i, r.arriba + t)
                if (distanciaRgb(p, calle) > TOLERANCIA) {
                    fallos++
                    if (fallos > perdon) return false
                }
            }
            return true
        }
    }
}

/** Suma de las diferencias de los tres canales, como [Recorte]. La usan [Vinetas] y [Bocadillos]. */
internal fun distanciaRgb(a: Int, b: Int): Int =
    abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
        abs((a and 0xFF) - (b and 0xFF))
