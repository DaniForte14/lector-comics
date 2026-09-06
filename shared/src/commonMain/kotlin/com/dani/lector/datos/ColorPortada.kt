package com.dani.lector.datos

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import com.dani.lector.ui.Colores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *  - [dominante] es una funcion PURA: bitmap entra, color sale. Sin cache, sin
 *    disco. Se puede ejecutar con una imagen guardada y comprobar que da lo que
 *    tiene que dar. Es la misma regla que ya se sigue con elegirVolumen.
 *  - [de] es la parte sucia: cache y corrutinas.
 *
 * NO se usa androidx.palette. Habria que meter una dependencia entera para
 * esto, y esto son cuarenta lineas que ademas hacen justo lo que queremos:
 * Palette apunta a colores "vibrantes" para rotulos, y aqui lo que hace falta
 * es el color que MANDA en la imagen aunque sea apagado.
 */
object ColorPortada {

    /** Cuadricula a la que se reduce la portada antes de contar. */
    private const val MUESTRA = 40

    /** Cuantos colores se recuerdan. Cabe de sobra: es un entero por comic. */
    private const val TOPE = 400

    /**
     * argb por uri.
     *
     * ERA UN `android.util.LruCache`, que no existe fuera de la JVM. El
     * sustituto es un `LinkedHashMap` podado a mano, y con eso **deja de ser
     * LRU y pasa a ser FIFO**: al pasar de [TOPE] se tira el que entro primero,
     * no el que se uso hace mas tiempo. En el comun no hay un mapa con orden de
     * acceso, y da igual: perder una entrada cuesta volver a contar los pixeles
     * de una miniatura que ya esta en cache, no una lectura de disco.
     */
    private var cache = LinkedHashMap<String, Int>()

    /** Los que ya se sabe que no tienen portada legible. */
    private var fallidos = HashSet<String>()

    /**
     * El cerrojo de los dos de arriba.
     *
     * `LruCache` y `Collections.synchronizedSet` se sincronizaban solos, y aqui
     * hay que hacerlo a mano: [de] la llaman tres composables a la vez y su
     * cuerpo corre en un hilo de fondo, asi que dos escrituras simultaneas en el
     * mapa son posibles de verdad. **Fuera del cerrojo queda [Portadas.obtener]
     * a proposito**: es la parte que toca disco, y meterla dentro serializaria
     * la carga de todas las portadas.
     */
    private val cerrojo = Mutex()

    /**
     * El color de la portada de un comic, o null si no se puede sacar.
     *
     * Se apoya en [Portadas], asi que no abre el fichero grande: reutiliza la
     * miniatura de 220 px que ya esta en cache para pintar el catalogo. Sacar
     * el color no cuesta ni una lectura de disco extra en el caso normal.
     */
    suspend fun de(portadas: Portadas, uri: String): Color? =
        // Dispatchers.Default, NO Dispatchers.IO: con coroutines 1.9.0 IO es
        // `internal` en Kotlin/Native y este fichero ya es comun.
        withContext(Dispatchers.Default) {
            cerrojo.withLock {
                cache[uri]?.let { return@withContext Color(it) }
                if (uri in fallidos) return@withContext null
            }

            val bmp = portadas.obtener(uri)
            if (bmp == null) {
                cerrojo.withLock { fallidos.add(uri) }
                return@withContext null
            }

            val c = dominante(bmp)
            cerrojo.withLock {
                // toArgb, NO value.toInt(): Color.value es un ULong con el
                // espacio de color dentro, y truncarlo a Int da un color que no
                // es el que era.
                cache[uri] = c.toArgb()
                if (cache.size > TOPE) cache.remove(cache.keys.first())
            }
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
    fun dominante(bmp: ImageBitmap): Color {
        // MUESTREAR CON SALTO, y no reescalar a MUESTRA x MUESTRA.
        //
        // Antes se hacia `Bitmap.createScaledBitmap` y `getPixels`, que son de
        // Android. Coger uno de cada N pixeles hace lo mismo para lo que se
        // busca aqui —que casilla de tono pesa mas— y ademas se ahorra crear y
        // reciclar un bitmap por cada portada.
        //
        // toPixelMap() es de Compose y vale en las dos plataformas.
        val mapa = bmp.toPixelMap()
        val salto = maxOf(1, minOf(bmp.width, bmp.height) / MUESTRA)

        val peso = DoubleArray(24)
        val sumaS = DoubleArray(24)
        val sumaV = DoubleArray(24)
        var brilloTotal = 0.0
        var cuantos = 0

        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val (h0, s, v) = Colores.aHsv(mapa[x, y])
                brilloTotal += v
                cuantos++
                x += salto

                // sin color util: negro de viñeta, blanco de bocadillo, gris
                if (v < 0.15f || s < 0.12f || (v > 0.95f && s < 0.20f)) continue

                val casilla = ((h0 / 15f).toInt()).coerceIn(0, 23)
                // saturado suma; alejarse del brillo medio resta
                val w = s.toDouble() * (1.0 - kotlin.math.abs(v - 0.60))
                peso[casilla] += w
                sumaS[casilla] += s * w
                sumaV[casilla] += v * w
            }
            y += salto
        }

        val mejor = peso.indices.maxByOrNull { peso[it] } ?: 0
        if (peso[mejor] <= 0.0) {
            // portada sin color: gris del brillo medio, sin inventar tono
            val v = (brilloTotal / maxOf(1, cuantos)).toFloat().coerceIn(0.2f, 0.7f)
            return Colores.desdeHsv(0f, 0f, v)
        }

        val h = mejor * 15f + 7.5f
        val s = (sumaS[mejor] / peso[mejor]).toFloat().coerceIn(0f, 1f)
        val v = (sumaV[mejor] / peso[mejor]).toFloat().coerceIn(0f, 1f)
        return Colores.desdeHsv(h, s, v)
    }

    // `oscurecer` y la conversion HSV se fueron a ui/Colores, que es donde
    // tienen que estar: las usa el tema, y estaban escritas con
    // android.graphics.Color, que no existe en iOS.

    /**
     * Se llama al vaciar la cache de portadas, porque los colores salen de las
     * miniaturas: si desaparecen ellas, lo que se recuerda de ellas tambien.
     *
     * NO suspende —la llama un boton de Ajustes— asi que **no puede coger el
     * [cerrojo]**, y por eso no hace `clear()`: vaciar un mapa mientras otro
     * hilo escribe en el es la clase de carrera que corrompe la tabla, no un
     * dato de mas. Se cambia la referencia, que es una escritura atomica; una
     * carga a medio vuelo termina de escribir en el mapa viejo, que ya no lee
     * nadie, y se lo lleva el recolector.
     */
    fun olvidar() {
        cache = LinkedHashMap()
        fallidos = HashSet()
    }
}
