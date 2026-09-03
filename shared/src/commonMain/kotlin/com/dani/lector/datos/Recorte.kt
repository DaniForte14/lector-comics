package com.dani.lector.datos

/**
 * El trozo de pagina que tiene dibujo.
 *
 * `der` y `abajo` son EXCLUYENTES, igual que en `android.graphics.Rect`, para
 * que quien corta pueda pasar `ancho`/`alto` sin sumar ni restar nada.
 */
data class Recuadro(val izq: Int, val arriba: Int, val der: Int, val abajo: Int) {
    val ancho get() = der - izq
    val alto get() = abajo - arriba
}

/**
 * Decide que marco liso sobra de una pagina escaneada. **Aqui no se corta
 * nada**: cortar necesita un bitmap de verdad y eso es de cada plataforma
 * (en Android, `RecorteAndroid`).
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
 *    devuelve null. Mas vale no recortar que recortar mal.
 */
object Recorte {

    /** Cuanto se puede alejar un pixel del fondo y seguir siendo margen. */
    private const val TOLERANCIA = 28

    /** Que proporcion de la fila puede ser distinta y aun asi contar como margen. */
    private const val RUIDO = 0.02f

    /** Se deja este margen de gracia para no comerse el filo del dibujo. */
    private const val GRACIA = 2

    /**
     * El rectangulo con dibujo. null si no hay nada que cortar o si no se fia.
     *
     * Los pixeles entran por dos funciones y no como un array entero **para no
     * copiar la pagina a memoria**: una pagina de 2000x3000 son 24 MB de ints,
     * y de todas formas esto solo mira unas pocas filas y columnas de los
     * bordes. Quien las implementa puede reutilizar un unico buffer, que es lo
     * que hace la version de Android.
     *
     * `fila(y)` devuelve los `ancho` pixeles de la fila y; `columna(x)`, los
     * `alto` pixeles de la columna x. En ARGB, como `Bitmap.getPixels`.
     */
    fun util(
        ancho: Int, alto: Int,
        fila: (Int) -> IntArray,
        columna: (Int) -> IntArray
    ): Recuadro? {
        if (ancho < 60 || alto < 60) return null

        // La esquina, y no el blanco: el marco negro es igual de comun.
        val fondo = fila(2)[2]

        // No se miran todos los pixeles de la fila: con ~180 muestras se sabe
        // igual si es lisa, y una pagina grande tiene miles.
        val paso = maxOf(1, ancho / 180)
        val pasoV = maxOf(1, alto / 180)

        fun filaLisa(y: Int): Boolean {
            val p = fila(y)
            var distintos = 0
            var mirados = 0
            var x = 0
            while (x < ancho) { if (lejos(p[x], fondo)) distintos++; mirados++; x += paso }
            return distintos <= mirados * RUIDO
        }

        fun columnaLisa(x: Int): Boolean {
            val p = columna(x)
            var distintos = 0
            var mirados = 0
            var y = 0
            while (y < alto) { if (lejos(p[y], fondo)) distintos++; mirados++; y += pasoV }
            return distintos <= mirados * RUIDO
        }

        var arriba = 0
        while (arriba < alto / 2 && filaLisa(arriba)) arriba++
        var abajo = alto - 1
        while (abajo > alto / 2 && filaLisa(abajo)) abajo--
        var izq = 0
        while (izq < ancho / 2 && columnaLisa(izq)) izq++
        var der = ancho - 1
        while (der > ancho / 2 && columnaLisa(der)) der--

        arriba = maxOf(0, arriba - GRACIA)
        izq = maxOf(0, izq - GRACIA)
        abajo = minOf(alto - 1, abajo + GRACIA)
        der = minOf(ancho - 1, der + GRACIA)

        val an = der - izq + 1
        val al = abajo - arriba + 1
        if (an < ancho * 0.4f || al < alto * 0.4f) return null    // no me fio
        if (an == ancho && al == alto) return null                // no habia nada que cortar
        return Recuadro(izq, arriba, der + 1, abajo + 1)
    }

    private fun lejos(pixel: Int, fondo: Int): Boolean {
        val dr = ((pixel shr 16) and 0xFF) - ((fondo shr 16) and 0xFF)
        val dg = ((pixel shr 8) and 0xFF) - ((fondo shr 8) and 0xFF)
        val db = (pixel and 0xFF) - (fondo and 0xFF)
        return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db) > TOLERANCIA
    }
}
