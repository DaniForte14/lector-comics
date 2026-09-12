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
 * UNA CALLE ES CUALQUIER LINEA CASI UNIFORME DE UN COLOR NEUTRO: blanco, gris o
 * negro, sin suponer cual. Hasta la tanda 31 el color se sacaba de los bordes de
 * la pagina y solo se cortaba por ese, y en paginas de verdad fallaba mucho
 * (medido con 96 paginas de Daredevil y Green Lantern): Daredevil #006 tiene
 * calles blancas pero el dibujo llega a los cuatro bordes, asi que no habia
 * color de calle y salia una sola viñeta en las 25 paginas; Daredevil 30 tiene
 * calles negras por dentro y bordes de dibujo. Y en Green Lantern Corps
 * Recharge, segun la captura de Dani, el margen es oscuro y las calles grises.
 *
 * LO QUE EVITA CORTAR UN CIELO: el color. Una viñeta a sangre con una franja de
 * cielo liso de lado a lado tiene filas tan uniformes como una calle, pero el
 * cielo es azul y una calle no tiene color. `VinetasTest` lo prueba.
 *
 * LO RALO PROPONE Y LO DENSO CONFIRMA. Cada fila se mira con [MUESTRAS]
 * muestras, como [Recorte], y en una fila de dibujo la primera muestra ya tiene
 * color, asi que cuesta una lectura. Pero con muestras sueltas se escapa la
 * linea del marco de una viñeta, que son dos pixeles, y peor: una fila que
 * cruza un globo blanco en una viñeta de fondo blanco parece calle. Por eso
 * ninguna calle se da por buena hasta leer su linea central ENTERA, y los
 * margenes se ajustan igual, linea entera a linea entera.
 *
 * ponytail: guillotina, y calles de color neutro. Calles en diagonal, viñetas
 * que se montan, viñetas insertadas dentro de otra o dibujo que cruza de una a
 * otra no se cortan, y lo que no se corta se queda como una viñeta mas grande,
 * que es como funcionaba todo antes de esto. Y al reves: una viñeta SIN MARCO
 * con un fondo blanco, gris o negro liso de lado a lado —un cielo nublado, un
 * vacio blanco— si se parte en dos. Si la sonda lo enseña, el siguiente paso es
 * buscar los marcos en vez de las calles.
 */
object Vinetas {

    /** Muestras por linea en la pasada rala. Ver arriba por que no hacen falta mas. */
    private const val MUESTRAS = 32

    /**
     * Cuanto se puede alejar un pixel del color de la calle, en suma de
     * diferencias RGB. Mas que los 28 de [Recorte] porque aqui entra la pagina
     * en RGB_565 (ver `Bocadillos.TOLERANCIA`: ~20 contra la media), y menos
     * que los 60 del globo, que ahi un pixel que no pasa solo deja un agujero.
     */
    private const val TOLERANCIA = 40

    /**
     * Cuanto pueden separarse el canal mas alto y el mas bajo de un color para
     * que sea "sin color", y por tanto pueda ser una calle. Medido: las calles
     * de Green Lantern (F2F7F1) se separan 9, las blancas y negras de Daredevil
     * menos de 5, y el papel viejo que amarillea anda por 40. Un cielo (87CEEB)
     * se separa 100 y la portada roja de Daredevil (ED1B24), 210.
     */
    private const val NEUTRO = 48

    /**
     * Cuantas veces se corta, como mucho: pagina en bandas, banda en columnas,
     * columna en viñetas apiladas, y alguna mas. Una maquetacion normal no pasa
     * de tres; esto acota el tiempo en una que se cuele.
     */
    private const val PROFUNDIDAD = 5

    fun de(ancho: Int, alto: Int, pixel: (x: Int, y: Int) -> Int): List<Recuadro> {
        val pagina = Recuadro(0, 0, ancho, alto)
        if (ancho < 60 || alto < 60) return listOf(pagina)
        val c = Cortador(ancho, alto, pixel)
        // LOS MARGENES PRIMERO, Y EN LAS DOS DIRECCIONES. La fila de una calle que
        // cruza la pagina tambien pisa los margenes de los lados, y si el margen es
        // de otro color —negro, con calles grises— la fila ya no sale lisa.
        val sinMargen = c.trozos(c.trozos(pagina, filas = true, cortar = false)[0], filas = false, cortar = false)[0]
        return c.partir(sinMargen, filas = true, profundidad = 0)
    }

    /** Sin color: sus tres canales casi iguales. Blanco, gris o negro. */
    private fun neutro(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) <= NEUTRO
    }

    private class Cortador(val ancho: Int, val alto: Int, val pixel: (x: Int, y: Int) -> Int) {
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
            val trozos = trozos(r, filas, cortar = true)
            if (trozos.size > 1 || trozos[0] != r) return trozos.flatMap { partir(it, !filas, profundidad + 1) }
            // Por aqui no hay nada que cortar; se prueba en la otra direccion, y
            // si tampoco, es una viñeta.
            val otros = trozos(r, !filas, cortar = true)
            if (otros.size == 1 && otros[0] == r) return listOf(r)
            return otros.flatMap { partir(it, filas, profundidad + 1) }
        }

        /** [r] sin su margen en una direccion y, si [cortar], partido por sus calles. */
        fun trozos(r: Recuadro, filas: Boolean, cortar: Boolean): List<Recuadro> {
            val n = if (filas) r.alto else r.ancho
            // Una viñeta de menos de un doceavo de pagina no existe: eso es un
            // hueco del dibujo que ha salido liso.
            val minimo = if (filas) alto / 12 else ancho / 12
            // Una calle es un TRAMO de lineas lisas del mismo color, de al menos
            // [grosor]. Una sola linea lisa no: el marco de una viñeta o el trazo
            // de una cartela son dos pixeles negros de lado a lado, y contados
            // como calle se los comeria el margen, y con ellos lo que separaba
            // una franja blanca de dentro de una calle de verdad.
            val colores = Array(n) { colorLiso(r, filas, it) }
            val calle = BooleanArray(n)
            var t = 0
            while (t < n) {
                val c = colores[t]
                if (c == null) { t++; continue }
                var e = t
                while (e + 1 < n && colores[e + 1]?.let { distanciaRgb(it, colores[e]!!) <= TOLERANCIA } == true) e++
                if (e - t + 1 >= grosor) for (k in t..e) calle[k] = true
                t = e + 1
            }
            val a = calle.indexOfFirst { !it }
            if (a < 0) return listOf(r)
            val b = calle.indexOfLast { !it }
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
                while (ini > 0 && !densa(r, filas, ini - 1)) ini--
            }
            if (tocaFin && n - 1 - b >= grosor) {
                fin = b + 1
                while (fin < n && !densa(r, filas, fin)) fin++
            }
            if (!cortar) return listOf(pieza(r, filas, ini, fin))

            // Las calles de dentro. Cada viñeta acaba donde empieza la calle, con
            // el borde ajustado linea entera a linea entera, porque las muestras
            // sueltas pueden haberse saltado el marco.
            //
            // Y UNA CALLE DE DENTRO ES FINA: como mucho un 4% de la pagina. Las de
            // verdad no pasan del 1,4% (medido: Daredevil 17-22 filas de 1574,
            // Green Lantern 9-12). Una franja lisa mas gruesa es fondo —una noche,
            // una sombra que cruza la viñeta con el marco, que tambien es negro— y
            // partirla desordenaria sus globos. El margen no tiene maximo: ese si
            // puede ser ancho.
            val maximo = maxOf(grosor, (if (filas) alto else ancho) * 4 / 100)
            val piezas = mutableListOf<Recuadro>()
            var desde = ini
            var i = a
            while (i <= b) {
                if (!calle[i]) { i++; continue }
                var e = i
                while (e + 1 <= b && calle[e + 1]) e++
                if (e - i + 1 <= maximo && densa(r, filas, (i + e) / 2)) {
                    var hasta = i
                    while (hasta < e && !densa(r, filas, hasta)) hasta++
                    var sigue = e + 1
                    while (sigue > hasta + 1 && !densa(r, filas, sigue - 1)) sigue--
                    if (hasta - desde >= minimo && fin - sigue >= minimo) {
                        piezas += pieza(r, filas, desde, hasta)
                        desde = sigue
                    }
                }
                i = e + 1
            }
            piezas += pieza(r, filas, desde, fin)
            return piezas
        }

        fun pieza(r: Recuadro, filas: Boolean, desde: Int, hasta: Int) =
            if (filas) Recuadro(r.izq, r.arriba + desde, r.der, r.arriba + hasta)
            else Recuadro(r.izq + desde, r.arriba, r.izq + hasta, r.abajo)

        /**
         * El color de la linea [i] de [r] —fila o columna— si es calle, o null.
         * Rala: [MUESTRAS] muestras, que se parezcan a la primera salvo una, por
         * el polvo del escaneo, y que la media no tenga color. Se devuelve la
         * media y no la primera, para que el ruido no lo decida un solo pixel.
         */
        fun colorLiso(r: Recuadro, filas: Boolean, i: Int): Int? {
            val largo = if (filas) r.ancho else r.alto
            val cuantas = minOf(MUESTRAS, largo)
            val primera = muestra(r, filas, i, 0)
            // Casi todo el dibujo tiene color, y con esto basta una lectura para
            // saber que la linea no es calle.
            if (!neutro(primera)) return null
            var fallos = 0
            var sr = 0
            var sg = 0
            var sb = 0
            var iguales = 0
            for (k in 0 until cuantas) {
                val p = if (k == 0) primera else muestra(r, filas, i, k * largo / cuantas)
                if (distanciaRgb(p, primera) > TOLERANCIA) {
                    fallos++
                    if (fallos > 1) return null
                } else {
                    sr += (p shr 16) and 0xFF
                    sg += (p shr 8) and 0xFF
                    sb += p and 0xFF
                    iguales++
                }
            }
            val media = (0xFF shl 24) or ((sr / iguales) shl 16) or ((sg / iguales) shl 8) or (sb / iguales)
            return if (neutro(media)) media else null
        }

        /**
         * Si la linea [i] es calle LEYENDOLA ENTERA, sin perdonar un pixel: lo
         * que se busca es justo la linea fina que las muestras se saltan.
         */
        fun densa(r: Recuadro, filas: Boolean, i: Int): Boolean {
            val color = colorLiso(r, filas, i) ?: return false
            val largo = if (filas) r.ancho else r.alto
            for (t in 0 until largo) {
                if (distanciaRgb(muestra(r, filas, i, t), color) > TOLERANCIA) return false
            }
            return true
        }

        fun muestra(r: Recuadro, filas: Boolean, i: Int, t: Int) =
            if (filas) pixel(r.izq + t, r.arriba + i) else pixel(r.izq + i, r.arriba + t)
    }
}

/** Suma de las diferencias de los tres canales, como [Recorte]. La usan [Vinetas] y [Bocadillos]. */
internal fun distanciaRgb(a: Int, b: Int): Int =
    abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
        abs((a and 0xFF) - (b and 0xFF))
