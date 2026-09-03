package com.dani.lector.datos

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.dani.lector.ui.Colores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * El color dominante de una portada, para teñir la interfaz con ella.
 *
 * Es lo que hace que un reproductor de musica caro parezca caro: el fondo no
 * es un gris fijo, es el color del disco que suena. Aqui vale igual: el visor
 * de un tomo de Daredevil tira a rojo y el de Green Lantern a verde, sin que
 * nadie haya elegido esos colores a mano.
 *
 * DOS PARTES A PROPOSITO, y la separacion importa:
 *
 *  - [dominante] es una funcion PURA: bitmap entra, color sale. Sin Context,
 *    sin cache, sin Compose. Se puede ejecutar fuera de Android con una imagen
 *    guardada y comprobar que da lo que tiene que dar. Es la misma regla que ya
 *    se sigue con Wiki.interpretar o elegirVolumen.
 *  - [de] es la parte sucia: cache, disco y corrutinas.
 *
 * NO se usa androidx.palette. Habria que meter una dependencia entera para
 * esto, y esto son cuarenta lineas que ademas hacen justo lo que queremos:
 * Palette apunta a colores "vibrantes" para rotulos, y aqui lo que hace falta
 * es el color que MANDA en la imagen aunque sea apagado.
 */
object ColorPortada {

    /** Cuadricula a la que se reduce la portada antes de contar. */
    private const val MUESTRA = 40

    /** argb por uri. Cabe de sobra: es un entero por comic. */
    private val cache = LruCache<String, Int>(400)

    /** Los que ya se sabe que no tienen portada legible. */
    private val fallidos = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * El color de la portada de un comic, o null si no se puede sacar.
     *
     * Se apoya en [Miniaturas], asi que no abre el fichero grande: reutiliza la
     * miniatura de 220 px que ya esta en cache para pintar el catalogo. Sacar
     * el color no cuesta ni una lectura de disco extra en el caso normal.
     */
    suspend fun de(ctx: Context, uri: String): Color? = withContext(Dispatchers.IO) {
        cache.get(uri)?.let { return@withContext Color(it) }
        if (uri in fallidos) return@withContext null

        val bmp = Miniaturas.obtener(ctx, uri)
        if (bmp == null) { fallidos.add(uri); return@withContext null }

        val c = dominante(bmp)
        // toArgb, NO value.toInt(): Color.value es un ULong con el espacio de
        // color dentro, y truncarlo a Int da un color que no es el que era.
        cache.put(uri, c.toArgb())
        c
    }

    /**
     * El color que manda en un bitmap. Funcion pura.
     *
     * Como se decide, y por que asi:
     *
     *  1. Se reduce a [MUESTRA] x [MUESTRA]. 1600 pixeles bastan de sobra para
     *     saber de que color es una portada, y recorrer la imagen entera seria
     *     tirar tiempo.
     *  2. Se DESCARTAN los pixeles sin color: casi negros, casi blancos y
     *     grises. En un comic eso es el negro de las viñetas y el blanco del
     *     bocadillo, que son la mitad de la pagina y no dicen nada del tono.
     *     Sin este filtro, TODAS las portadas darian gris.
     *  3. Los que quedan se agrupan por tono en 24 casillas de 15 grados, y
     *     cada uno pesa segun lo saturado que este y lo cerca que quede del
     *     brillo medio. Un rojo intenso pesa mas que un rosa palido, y un
     *     detalle chillon en la esquina no gana a la mancha grande.
     *  4. Gana la casilla mas pesada y se devuelve su color medio.
     *
     * Si no queda ningun pixel con color —una portada en blanco y negro, que
     * las hay— se devuelve un gris del brillo medio de la imagen y ya esta. No
     * se inventa un tono que no existe.
     */
    fun dominante(bmp: Bitmap): Color {
        val chico = Bitmap.createScaledBitmap(bmp, MUESTRA, MUESTRA, true)
        val pixeles = IntArray(MUESTRA * MUESTRA)
        chico.getPixels(pixeles, 0, MUESTRA, 0, 0, MUESTRA, MUESTRA)
        if (chico !== bmp) chico.recycle()

        val peso = DoubleArray(24)
        val sumaS = DoubleArray(24)
        val sumaV = DoubleArray(24)
        var brilloTotal = 0.0
        val hsv = FloatArray(3)

        for (p in pixeles) {
            android.graphics.Color.colorToHSV(p, hsv)
            val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
            brilloTotal += v

            // sin color util: negro de viñeta, blanco de bocadillo, gris
            if (v < 0.15f || s < 0.12f || (v > 0.95f && s < 0.20f)) continue

            val casilla = ((h / 15f).toInt()).coerceIn(0, 23)
            // saturado suma; alejarse del brillo medio resta
            val w = s.toDouble() * (1.0 - kotlin.math.abs(v - 0.60) )
            peso[casilla] += w
            sumaS[casilla] += s * w
            sumaV[casilla] += v * w
        }

        val mejor = peso.indices.maxByOrNull { peso[it] } ?: 0
        if (peso[mejor] <= 0.0) {
            // portada sin color: gris del brillo medio, sin inventar tono
            val v = (brilloTotal / pixeles.size).toFloat().coerceIn(0.2f, 0.7f)
            return Colores.desdeHsv(0f, 0f, v)
        }

        val h = mejor * 15f + 7.5f
        val s = (sumaS[mejor] / peso[mejor]).toFloat().coerceIn(0f, 1f)
        val v = (sumaV[mejor] / peso[mejor]).toFloat().coerceIn(0f, 1f)
        return Colores.desdeHsv(h, s, v)
    }

    // `oscurecer` y la conversion HSV se fueron a ui/Colores en :shared, que es
    // donde tienen que estar: las usa el tema, que ahora es comun, y estaban
    // escritas con android.graphics.Color, que no existe en iOS. Aqui se queda
    // solo lo que de verdad es de Android: leer un Bitmap.

    fun olvidar() {
        cache.evictAll()
        fallidos.clear()
    }
}
