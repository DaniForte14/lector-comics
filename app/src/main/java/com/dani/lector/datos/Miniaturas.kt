package com.dani.lector.datos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Cache de portadas en dos niveles: memoria y disco.
 *
 * Sin esto, pintar la rejilla de 36 numeros significaria abrir 36 ficheros de
 * 35 MB cada vez que haces scroll. Con esto se genera una vez, se guarda en la
 * cache de la app y a partir de ahi es instantaneo.
 */
object Miniaturas {

    private const val ANCHO = 220

    /**
     * Miniaturas en memoria, medidas POR TAMANO y no por numero.
     *
     * Antes eran 30 a pelo, contando una por una. Una miniatura de 220 px de
     * ancho ocupa unos 300 KB, asi que 30 eran 9 MB: en la rejilla de tres
     * columnas caben doce a la vez y los carruseles de la portada gastan otras
     * tantas, asi que al bajar rapido la cache se vaciaba sola y cada carta que
     * volvia a entrar tenia que ir otra vez al disco. Eso es el tiron.
     *
     * Ahora el limite es un OCTAVO del monton de memoria de la app —la receta
     * de siempre en Android— con un techo de 48 MB. En un movil de hoy eso son
     * mas de cien portadas: al hacer scroll hacia atras ya estan puestas.
     *
     * Se mide en KB, no en bytes, porque LruCache lleva la cuenta en Int y en
     * bytes se pasaria de la raya con 2 GB.
     */
    private val TECHO_KB = (Runtime.getRuntime().maxMemory() / 1024 / 8)
        .coerceIn(8L * 1024, 48L * 1024).toInt()

    // LA CACHE GUARDA ImageBitmap, NO Bitmap. Es el tipo que entiende Compose
    // en las dos plataformas, y asi la conversion se hace UNA vez al decodificar
    // y no en cada repintado de cada carta de la rejilla.
    //
    // Se mide a mano porque ImageBitmap no tiene `byteCount`: ancho x alto x 2,
    // que son los bytes de un pixel en RGB_565, que es como se decodifica.
    private val memoria = object : LruCache<String, ImageBitmap>(TECHO_KB) {
        override fun sizeOf(key: String, value: ImageBitmap) =
            maxOf(1, value.width * value.height * 2 / 1024)
    }

    /**
     * La miniatura si ya esta en memoria, SIN suspender.
     *
     * Existe por el scroll: la version suspendida salta a un hilo de IO aunque
     * la respuesta este ahi mismo, y ese salto son uno o dos fotogramas con la
     * carta gris. Cuando la carta vuelve a entrar en pantalla lo normal es que
     * la portada ya este puesta, y asi se pinta en el mismo fotograma.
     */
    fun enMemoria(uri: String): ImageBitmap? = memoria.get(uri)

    /**
     * Ficheros que ya sabemos que no se pueden abrir (un CBR en RAR5, por ejemplo).
     * Sin esto se reintenta abrir 35 MB en cada recomposicion de la lista.
     */
    private val fallidos = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Por que fallo cada uno, para poder decirlo en la carta. */
    private val motivos = java.util.Collections.synchronizedMap(HashMap<String, String>())

    fun motivo(uri: String): String? = motivos[uri]

    /** Como mucho tres portadas a la vez: si no, se dispara la memoria. */
    private val turnos = Semaphore(3)

    // ─────────────── CRONOMETRO DE PORTADAS (04/09/2026) ───────────────
    //
    // Dani: "al principio cuando entro en la app todo tarda y va con lag,
    // supongo que esta cargando las portadas". PUEDE QUE SI Y PUEDE QUE NO, y
    // en este proyecto adivinar el rendimiento ya ha fallado tres veces
    // seguidas. El arranque estaba cronometrado entero MENOS esto, asi que era
    // el unico hueco por donde se podia escapar la respuesta.
    //
    // A PRIORI LAS PORTADAS NO DEBERIAN DAR TIRONES: se sacan en Dispatchers.IO
    // y de tres en tres, asi que no bloquean el hilo de la interfaz. Lo que si
    // podrian hacer es marear al recolector de basura descomprimiendo bitmaps
    // sin parar, y ESO si se nota en la fluidez. Los dos numeros de aqui abajo
    // distinguen los dos casos: si el total es pequeño, no eran ellas.
    //
    // Se apunta un RESUMEN y no una linea por portada: con trescientos comics
    // el rastro no tendria otra cosa y taparia lo que se busca.
    //
    // LA PRIMERA Y LUEGO CADA DIEZ, y esto ya fallo una vez: la primera version
    // apuntaba cada 25 y **en la pantalla de inicio no llegaba a hablar nunca**.
    // La raiz de Dani tiene 2 carpetas y ningun comic suelto, asi que ahi solo
    // se piden el banner, las tres de "tu recorrido" y lo visible de dos
    // carruseles: unas quince o veinte. El cronometro se quedaba mudo justo en
    // la pantalla de la que se estaba hablando.
    private var deDisco = 0
    private var msDisco = 0L
    private var generadas = 0
    private var msGenerar = 0L

    @Synchronized
    private fun apunta(ctx: Context, disco: Boolean, ms: Long) {
        if (disco) { deDisco++; msDisco += ms } else { generadas++; msGenerar += ms }
        val n = deDisco + generadas
        if (n == 1 || n % 10 == 0) Rastro.apunta(ctx,
            "  portadas: $deDisco de cache ($msDisco ms), " +
            "$generadas generadas ($msGenerar ms)")
    }

    private fun carpeta(ctx: Context) = File(ctx.cacheDir, "miniaturas").apply { mkdirs() }

    private fun clave(uri: String): String {
        val d = MessageDigest.getInstance("MD5").digest(uri.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    /**
     * Las miniaturas se guardan en memoria en RGB_565, no en el ARGB_8888 que
     * decodifica Android por defecto.
     *
     * Dos motivos, y los dos se notan justo al scrollear rapido:
     *
     *  - Ocupan la MITAD. Con el techo por tamaño de arriba, eso es el doble de
     *    portadas cacheadas con la misma memoria.
     *  - La mitad de bytes que subir a la tarjeta grafica. Cada bitmap nuevo que
     *    entra en pantalla se sube una vez, y bajando rapido entran muchos de
     *    golpe: ahi es donde se pierde el fotograma.
     *
     * Lo que se pierde es el canal alfa —que un JPEG no tiene— y precision de
     * color. En una imagen de 220 px de ancho no se ve, y ColorPortada, que es
     * lo unico que las lee pixel a pixel, agrupa en casillas de 15 grados de
     * tono: la diferencia se pierde en el redondeo.
     */
    private fun decodificar(f: File): Bitmap? = BitmapFactory.decodeFile(
        f.absolutePath,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
    )

    suspend fun obtener(ctx: Context, uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memoria.get(uri)?.let { return@withContext it }
        if (uri in fallidos) return@withContext null

        val f = File(carpeta(ctx), clave(uri) + ".jpg")
        if (f.exists()) {
            val t0 = System.currentTimeMillis()
            decodificar(f)?.let {
                val img = it.asImageBitmap()
                memoria.put(uri, img)
                apunta(ctx, disco = true, ms = System.currentTimeMillis() - t0)
                return@withContext img
            }
        }

        // A partir de aqui la portada hay que SACARLA DEL COMIC, que es abrir un
        // fichero de decenas de megas y descomprimir su primera pagina. Es el
        // camino caro y solo se recorre una vez en la vida de cada comic.
        val tGenerar = System.currentTimeMillis()

        // TODO envuelto, incluido el OutOfMemoryError.
        //
        // Un fichero de la biblioteca NUNCA puede tumbar la app. Aqui se abren
        // ficheros de decenas de megas escritos por terceros: un CBZ con una
        // pagina corrupta, un RAR con una cabecera rara o un tomo cuya portada
        // no cabe en memoria. ComicZip ya atrapa Exception en sus metodos, pero
        // OutOfMemoryError es un Error, NO una Exception, y se colaba entero
        // hasta arriba llevandose el proceso por delante.
        //
        // Se atrapa Throwable a proposito, que es lo unico que cubre las dos
        // ramas. La portada se queda sin salir y se apunta el motivo, que es
        // exactamente lo que ya se hace con los demas fallos.
        val bmp = try {
            turnos.withPermit { ComicZip.portada(ctx, uri, ANCHO) }
        } catch (t: Throwable) {
            fallidos.add(uri)
            motivos[uri] = if (t is OutOfMemoryError) "portada\ndemasiado grande"
                           else "no se ha podido\nabrir"
            return@withContext null
        }

        if (bmp == null) {
            fallidos.add(uri)
            // Se mira el formato SOLO cuando ya ha fallado: son ocho bytes, pero
            // hacerlo siempre seria una lectura de disco por cada portada del
            // catalogo para nada. Y tambien envuelto: si el fichero es el que
            // esta dando problemas, volver a abrirlo puede dar mas de lo mismo.
            motivos[uri] = runCatching { ComicZip.motivoCorto(ctx, uri) }
                .getOrDefault("no se ha podido\nabrir")
            return@withContext null
        }
        // Se guarda y se vuelve a leer del fichero para quedarse con la version
        // de 565, no con la recien salida del comic. Es una decodificacion de
        // un JPEG de 220 px, UNA vez en la vida de cada comic, y a cambio todo
        // lo que hay en memoria pesa lo mismo y la cuenta del techo cuadra. Si
        // guardar falla, se usa la que ya se tiene: peor cache, pero portada.
        val guardada = runCatching {
            // De vuelta a Bitmap SOLO para guardarlo: comprimir a JPEG es de
            // Android y esto vive en :app. Es una vez en la vida de cada comic,
            // no una por repintado, que es lo que se ha quitado del visor.
            f.outputStream().use {
                bmp.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 80, it)
            }
            podarSiToca(ctx)
            // Se relee lo GUARDADO y no se usa lo recien salido del comic: asi
            // en memoria vive la version de 565 y la cuenta del techo cuadra.
            decodificar(f)?.asImageBitmap()
        }.getOrNull() ?: bmp

        val img = guardada
        memoria.put(uri, img)
        apunta(ctx, disco = false, ms = System.currentTimeMillis() - tGenerar)
        img
    }

    /**
     * Techo de las miniaturas EN DISCO, y por que tambien hace falta aqui.
     *
     * Es la misma fuga que la de los convertidos, en pequeño: se escribe una
     * miniatura por comic y no se borra ninguna nunca. Cada una son unos 20 KB,
     * asi que hacen falta miles para que se note — pero una biblioteca de
     * comics ES miles, y la cuenta no para de subir aunque borres comics: la
     * miniatura de un fichero que ya no existe se queda ahi para siempre.
     *
     * 150 MB dan para unas siete mil portadas. Se poda de la menos usada a la
     * mas usada, asi que lo que se cae es lo que no miras.
     */
    private const val TECHO_DISCO = 150L * 1024 * 1024

    /** Cada cuantas escrituras toca mirar. Listar la carpeta entera cuesta. */
    private const val CADA = 50

    private var escritas = 0

    @Synchronized
    private fun podarSiToca(ctx: Context) {
        if (++escritas < CADA) return
        escritas = 0

        val ficheros = carpeta(ctx).listFiles() ?: return
        var total = ficheros.sumOf { it.length() }
        if (total <= TECHO_DISCO) return

        for (f in ficheros.sortedBy { it.lastModified() }) {
            if (total <= TECHO_DISCO) break
            val ocupaba = f.length()
            if (f.delete()) total -= ocupaba
        }
    }

    /** Lo que ocupan las miniaturas guardadas, para poder enseñarlo. */
    fun tamano(ctx: Context): Long =
        carpeta(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    fun limpiar(ctx: Context) {
        memoria.evictAll()
        fallidos.clear()
        motivos.clear()
        carpeta(ctx).listFiles()?.forEach { it.delete() }
    }
}
