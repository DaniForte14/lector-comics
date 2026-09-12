package com.dani.lector.datos

import android.graphics.Bitmap

/**
 * ANDROID — el corte en si. Quien decide QUE se corta es [Recorte], en `:shared`.
 *
 * Aqui solo queda lo que necesita un `Bitmap` de verdad: leer los pixeles y
 * recortar. Se partio por aqui —y no se llevo el fichero entero— porque
 * `Bitmap.createBitmap` y `getPixels` no existen fuera de Android, mientras que
 * las cuatro reglas que deciden el margen son aritmetica y se pueden probar.
 */
object RecorteAndroid {

    /**
     * El trozo que se queda, o null si no sobra nada.
     *
     * Aparte de [aplicar] porque los globos lo necesitan SIN cortar (tanda 32):
     * se analizan en la pagina entera —si no, un globo que rompe el marco hacia
     * el margen toca el borde y se descarta— y luego se trasladan a la
     * recortada con este mismo recuadro.
     */
    fun recuadro(b: Bitmap): Recuadro? {
        val an = b.width
        val al = b.height

        // Un unico buffer por fila y otro por columna, reutilizados en cada
        // llamada: es lo que hacia la version de antes, y sin esto seria un
        // IntArray nuevo por cada borde que se mira.
        val fila = IntArray(an)
        val columna = IntArray(al)

        val r = Recorte.util(an, al,
            { y -> b.getPixels(fila, 0, an, 0, y, an, 1); fila },
            { x -> b.getPixels(columna, 0, 1, x, 0, 1, al); columna }
        ) ?: return null

        return if (r.ancho >= an && r.alto >= al) null else r
    }

    fun aplicar(b: Bitmap): Bitmap {
        val r = recuadro(b) ?: return b
        return runCatching {
            Bitmap.createBitmap(b, r.izq, r.arriba, r.ancho, r.alto)
        }.getOrDefault(b)
    }
}
