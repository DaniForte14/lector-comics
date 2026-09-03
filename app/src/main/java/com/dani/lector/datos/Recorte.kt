package com.dani.lector.datos

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Quita el marco liso de una pagina escaneada.
 *
 * En un movil esto no es un adorno: los escaneos suelen traer entre un 5 y un
 * 15% de borde blanco o negro, y en una pantalla de seis pulgadas eso es la
 * diferencia entre leer un bocadillo y tener que ampliar.
 *
 * COMO SE DECIDE, que es donde estan las trampas:
 *
 *  - El color de fondo se toma de una esquina, no se supone blanco: hay muchos
 *    comics con el marco negro y con marcos de color.
 *  - Una fila cuenta como margen si CASI todos sus pixeles se parecen al fondo,
 *    no si todos. Un solo pixel de ruido del escaner no puede impedir el corte.
 *  - Se busca solo hasta la mitad de la pagina por cada lado. Si una pagina es
 *    casi toda blanca (una splash page clara, una pagina de creditos) no se
 *    come el dibujo.
 *  - Y si el recorte deja menos del 40% de la pagina, se descarta entero y se
 *    devuelve la original. Mas vale no recortar que recortar mal.
 */
object Recorte {

    /** Cuanto se puede alejar un pixel del fondo y seguir siendo margen. */
    private const val TOLERANCIA = 28

    /** Que proporcion de la fila puede ser distinta y aun asi contar como margen. */
    private const val RUIDO = 0.02f

    /** Se deja este margen de gracia para no comerse el filo del dibujo. */
    private const val GRACIA = 2

    fun aplicar(b: Bitmap): Bitmap {
        val r = util(b) ?: return b
        if (r.width() >= b.width && r.height() >= b.height) return b
        return runCatching {
            Bitmap.createBitmap(b, r.left, r.top, r.width(), r.height())
        }.getOrDefault(b)
    }

    /** El rectangulo con dibujo. null si no hay nada que cortar o no se fia. */
    fun util(b: Bitmap): Rect? {
        val an = b.width
        val al = b.height
        if (an < 60 || al < 60) return null

        val fondo = b.getPixel(2, 2)
        val paso = maxOf(1, an / 180)
        val pasoV = maxOf(1, al / 180)

        val fila = IntArray(an)
        val columna = IntArray(al)

        fun filaLisa(y: Int): Boolean {
            b.getPixels(fila, 0, an, 0, y, an, 1)
            var distintos = 0
            var mirados = 0
            var x = 0
            while (x < an) { if (lejos(fila[x], fondo)) distintos++; mirados++; x += paso }
            return distintos <= mirados * RUIDO
        }

        fun columnaLisa(x: Int): Boolean {
            b.getPixels(columna, 0, 1, x, 0, 1, al)
            var distintos = 0
            var mirados = 0
            var y = 0
            while (y < al) { if (lejos(columna[y], fondo)) distintos++; mirados++; y += pasoV }
            return distintos <= mirados * RUIDO
        }

        var arriba = 0
        while (arriba < al / 2 && filaLisa(arriba)) arriba++
        var abajo = al - 1
        while (abajo > al / 2 && filaLisa(abajo)) abajo--
        var izq = 0
        while (izq < an / 2 && columnaLisa(izq)) izq++
        var der = an - 1
        while (der > an / 2 && columnaLisa(der)) der--

        arriba = maxOf(0, arriba - GRACIA)
        izq = maxOf(0, izq - GRACIA)
        abajo = minOf(al - 1, abajo + GRACIA)
        der = minOf(an - 1, der + GRACIA)

        val ancho = der - izq + 1
        val alto = abajo - arriba + 1
        if (ancho < an * 0.4f || alto < al * 0.4f) return null    // no me fio
        if (ancho == an && alto == al) return null                // no habia nada que cortar
        return Rect(izq, arriba, der + 1, abajo + 1)
    }

    private fun lejos(pixel: Int, fondo: Int): Boolean {
        val dr = ((pixel shr 16) and 0xFF) - ((fondo shr 16) and 0xFF)
        val dg = ((pixel shr 8) and 0xFF) - ((fondo shr 8) and 0xFF)
        val db = (pixel and 0xFF) - (fondo and 0xFF)
        return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db) > TOLERANCIA
    }
}
