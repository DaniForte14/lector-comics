package com.dani.lector.datos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import com.github.junrar.Archive
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lectura de CBZ (ZIP) y CBR (RAR).
 * El formato se detecta por los primeros bytes, no por la extension: hay
 * ficheros llamados .cbr que por dentro son ZIP y al reves.
 */
object ComicZip {

    /**
     * Cache de paginas ya descomprimidas. Sin esto, cada pagina se abre y se
     * descomprime justo al llegar a ella, y por eso parpadea al pasar.
     * Ocho paginas a media resolucion caben de sobra en memoria.
     */
    private val cachePaginas = object : android.util.LruCache<String, ImageBitmap>(8) {
        override fun sizeOf(key: String, value: ImageBitmap) = 1
    }

    /**
     * Cache aparte para lo pequeño: portadas del catalogo y miniaturas del
     * lector.
     *
     * Sin esto comparten los ocho huecos de arriba, y como se cuentan por
     * NUMERO y no por tamaño, pasar una tira de treinta miniaturas echa fuera
     * las paginas que estabas leyendo. Luego pasas pagina y parpadea. Caben
     * muchas mas porque pesan una milesima.
     */
    private val cacheMini = object : android.util.LruCache<String, ImageBitmap>(60) {
        override fun sizeOf(key: String, value: ImageBitmap) = 1
    }

    /**
     * Cache del detalle: la version grande de la pagina que estas ampliando.
     *
     * SOLO DOS. Una pagina a resolucion de zoom puede pesar veinte megas, y
     * ocho de esas se comen el limite de memoria de la app. Dos bastan porque
     * solo se amplia la que tienes delante.
     */
    private val cacheDetalle = object : android.util.LruCache<String, ImageBitmap>(2) {
        override fun sizeOf(key: String, value: ImageBitmap) = 1
    }

    /** El limite por debajo del cual algo es una miniatura y no una pagina. */
    private const val MINI = 400

    /** Y por encima del cual es una peticion de detalle para el zoom. */
    private const val DETALLE = 1200

    private fun cache(anchoMax: Int) = when {
        anchoMax <= MINI -> cacheMini
        anchoMax >= DETALLE -> cacheDetalle
        else -> cachePaginas
    }

    private fun clavePagina(uri: String, nombre: String, ancho: Int) = "$uri|$nombre|$ancho"

    /**
     * Por que no ha salido la portada, en dos lineas y para una carta pequeña.
     *
     * El mensaje largo y con solucion ya existe, pero solo aparece al ABRIR el
     * comic. En el catalogo la carta se quedaba negra y muda, asi que con
     * cuarenta numeros no habia forma de saber cuales eran RAR5 sin ir uno por
     * uno. Un tope que se calla, otra vez.
     */
    fun motivoCorto(ctx: Context, uri: String): String = when (formato(ctx, uri)) {
        Formato.RAR5 -> "CBR RAR5\nno se puede abrir"
        Formato.RAR4 -> "CBR sin\nimágenes dentro"
        Formato.ZIP -> "CBZ sin\nimágenes dentro"
        Formato.DESCONOCIDO -> "archivo\nno reconocido"
    }

    /** El formato de un comic, por su firma. Lo necesita ConversorCarpeta. */
    fun formatoDe(ctx: Context, uri: String): Formato = formato(ctx, uri)

    private fun formato(ctx: Context, uri: String): Formato = try {
        ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { ins ->
            val c = ByteArray(Formatos.BYTES)
            val leidos = ins.read(c)
            if (leidos < 2) Formato.DESCONOCIDO
            else Formatos.de(c.copyOf(leidos))
        } ?: Formato.DESCONOCIDO
    } catch (_: Exception) { Formato.DESCONOCIDO }

    fun paginas(ctx: Context, uri: String): Paginas = try {
        when (formato(ctx, uri)) {
            Formato.ZIP -> paginasDeZip(
                { ctx.contentResolver.openInputStream(Uri.parse(uri)) },
                "El archivo no tiene imágenes dentro.")
            // RAR4 va por el mismo sitio que RAR5: se convierte a CBZ una vez.
            //
            // Antes se leia con junrar directamente y ahi estaba el cierre de la
            // app: junrar, con el stream de SAF que no se puede rebobinar, tiene
            // que bufferizar el archivo ENTERO para saltar por el, y un tomo
            // grande se lleva por delante el limite de 256 MB del proceso.
            //
            // junrar se queda solo de respaldo, para cuando el motor nativo no
            // arranque: en ese caso vale mas leer los RAR4 pequeños como siempre
            // que no leer ninguno.
            Formato.RAR4 -> {
                val cbz = Rar5.aCbz(ctx, uri, Formato.RAR4)
                if (cbz != null)
                    paginasDeZip({ cbz.inputStream() },
                                 "El CBR convertido no tiene imágenes dentro.")
                else paginasDeRar4Directo(ctx, uri)
            }
            // RAR5: se convierte a CBZ una sola vez y a partir de ahi es un ZIP
            // normal. El motor nativo vive en Rar5 y no se asoma por aqui.
            Formato.RAR5 -> {
                val cbz = Rar5.aCbz(ctx, uri)
                if (cbz == null)
                    Paginas.Error("Este CBR usa RAR5 y no se ha podido convertir. " +
                                  (Rar5.ultimoFallo() ?: "Motivo desconocido."))
                else paginasDeZip({ cbz.inputStream() },
                                  "El CBR convertido no tiene imágenes dentro.")
            }
            Formato.DESCONOCIDO ->
                Paginas.Error("No es ni ZIP ni RAR, o el archivo está incompleto.")
        }
    } catch (t: Throwable) {
        // Throwable, no Exception. Este catch se quedo sin cambiar cuando se
        // arreglaron los demas y por el se colaba el OutOfMemoryError de junrar:
        // la app se cerraba al ABRIR el comic aunque su portada ya saliera bien.
        Paginas.Error("No se ha podido abrir: ${t.javaClass.simpleName} ${t.message ?: ""}")
    }

    fun pagina(
        ctx: Context, uri: String, nombre: String, anchoMax: Int, recortar: Boolean = false
    ): ImageBitmap? = try {
        // el recorte entra en la clave: si cambias el ajuste, la cache no
        // puede devolverte la version anterior
        val clave = clavePagina(uri, nombre, anchoMax) + if (recortar) "|r" else ""
        cache(anchoMax).get(clave)?.let { return it }

        val bmp = when (formato(ctx, uri)) {
            // El ZIP se lee EN CHORRO, sin array intermedio: se abre dos veces
            // y se decodifica directo de la entrada.
            Formato.ZIP -> decodificarDe({
                abrirEntradaZip({ ctx.contentResolver.openInputStream(Uri.parse(uri)) }, nombre)
            }, anchoMax)

            // Ya convertido cuando se listaron las paginas: aqui solo se lee.
            // Si no estuviera, no se convierte a mitad de lectura; se devuelve
            // null y quien pidio la pagina se entera por el camino normal.
            Formato.RAR5 -> Rar5.yaConvertido(ctx, uri)?.let { cbz ->
                decodificarDe({ abrirEntradaZip({ cbz.inputStream() }, nombre) }, anchoMax)
            }

            // Lo normal es que ya este convertido a CBZ, y entonces esto es
            // leer un ZIP y punto.
            //
            // El respaldo, para cuando el motor nativo no arranca, extrae la
            // pagina a un temporal y decodifica de ahi. Nunca a un ByteArray:
            // junrar ya se traga el ARCHIVO ENTERO en memoria —con el stream de
            // SAF no puede hacer otra cosa— y sumarle la pagina completa en un
            // array ademas del bitmap es lo que cerraba la app en Blackest
            // Night.
            Formato.RAR4 -> Rar5.yaConvertido(ctx, uri)?.let { cbz ->
                decodificarDe({ abrirEntradaZip({ cbz.inputStream() }, nombre) }, anchoMax)
            } ?: run {
                val tmp = extraerRarA(ctx, uri, nombre)
                try {
                    tmp?.let { f -> decodificarDe({ f.inputStream() }, anchoMax) }
                } finally { tmp?.delete() }
            }
            else -> null
        }

        // LA CONVERSION A ImageBitmap SE HACE AQUI, UNA VEZ POR DECODIFICACION.
        // Antes salia un Bitmap y el visor llamaba a asImageBitmap() dentro del
        // composable, o sea **una conversion por recomposicion** — el mismo
        // fallo que la tanda 7 quito de la rejilla. Y ademas ImageBitmap es el
        // tipo que entienden las dos plataformas, asi que es la frontera.
        bmp?.let { if (recortar) RecorteAndroid.aplicar(it) else it }
            ?.asImageBitmap()
            ?.also { cache(anchoMax).put(clave, it) }
    } catch (_: Throwable) { null }

    /**
     * El camino viejo para RAR4, con junrar y sin convertir.
     *
     * Solo se usa si el motor nativo no ha arrancado. Es el que se come el
     * archivo entero en memoria, asi que con un tomo grande volvera a fallar
     * —pero fallara con un motivo, no cerrando la app.
     */
    private fun paginasDeRar4Directo(ctx: Context, uri: String): Paginas {
        val out = mutableListOf<String>()
        ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { ins ->
            Archive(ins).use { a ->
                var h = a.nextFileHeader()
                while (h != null) {
                    val n = h.fileName.replace('\\', '/')
                    if (!h.isDirectory && Imagenes.es(n)) out.add(n)
                    h = a.nextFileHeader()
                }
            }
        }
        return if (out.isEmpty()) Paginas.Error("Este CBR (RAR4) no tiene imágenes dentro.")
               else Paginas.Ok(Imagenes.ordenadas(out))
    }

    /** Lista las imagenes de un ZIP, venga de SAF o de un fichero convertido. */
    private fun paginasDeZip(abrir: () -> InputStream?, siVacio: String): Paginas {
        val out = mutableListOf<String>()
        abrir()?.use { ins ->
            ZipInputStream(ins.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory && Imagenes.es(e.name)) out.add(e.name)
                    e = z.nextEntry
                }
            }
        }
        return if (out.isEmpty()) Paginas.Error(siVacio)
               else Paginas.Ok(Imagenes.ordenadas(out))
    }

    /**
     * El ZipInputStream ya colocado en la entrada que se pide.
     *
     * Se devuelve el propio stream: despues de nextEntry, leer de el es leer
     * esa entrada. Lo cierra quien lo usa, y al cerrarlo se cierra tambien el
     * de SAF que lleva debajo.
     */
    private fun abrirEntradaZip(abrir: () -> InputStream?, nombre: String): InputStream? {
        val ins = abrir() ?: return null
        val z = ZipInputStream(ins.buffered())
        var e = z.nextEntry
        while (e != null) {
            if (e.name == nombre) return z
            e = z.nextEntry
        }
        z.close()
        return null
    }

    /** Saca una entrada del RAR a un fichero temporal de la cache. */
    private fun extraerRarA(ctx: Context, uri: String, nombre: String): File? {
        val destino = File.createTempFile("pag", null, ctx.cacheDir)
        try {
            ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { ins ->
                Archive(ins).use { a ->
                    var h = a.nextFileHeader()
                    while (h != null) {
                        if (h.fileName.replace('\\', '/') == nombre) {
                            destino.outputStream().use { out -> a.extractFile(h, out) }
                            return destino
                        }
                        h = a.nextFileHeader()
                    }
                }
            }
        } catch (t: Throwable) {
            // Throwable y no Exception: aqui es donde junrar se queda sin
            // memoria con un tomo grande, y OutOfMemoryError es un Error.
            destino.delete()
            return null
        }
        destino.delete()
        return null
    }

    /** Deja listas las paginas de alrededor para que el paso sea instantaneo. */
    fun precargar(
        ctx: Context, uri: String, nombres: List<String>, actual: Int,
        anchoMax: Int, recortar: Boolean = false
    ) {
        val sufijo = if (recortar) "|r" else ""
        for (i in listOf(actual + 1, actual + 2, actual - 1)) {
            val n = nombres.getOrNull(i) ?: continue
            if (cache(anchoMax).get(clavePagina(uri, n, anchoMax) + sufijo) == null) {
                runCatching { pagina(ctx, uri, n, anchoMax, recortar) }
            }
        }
    }

    fun portada(ctx: Context, uri: String, anchoMax: Int = 300): ImageBitmap? {
        val p = paginas(ctx, uri)
        if (p !is Paginas.Ok) return null
        return pagina(ctx, uri, p.nombres.first(), anchoMax)
    }

    /** Sin esto, un CBZ de 38 MB con paginas de 2000x3000 se come la memoria. */
    /**
     * Decodifica leyendo DOS VECES del origen, sin pasar por un ByteArray.
     *
     * El motivo es el cierre de la app del 25/08/2026. Antes se hacia
     * `extractFile` o `readBytes` a un array y se decodificaba de ahi: para una
     * pagina de un tomo grande eso son cientos de megas de array **ademas** del
     * bitmap, contra el limite de 256 MB del proceso. Petaba con
     * OutOfMemoryError, que ademas no es una Exception y se colaba entero.
     *
     * Abrir dos veces cuesta un recorrido mas del fichero y no cuesta memoria,
     * que es justo el cambio que hacia falta.
     */
    private fun decodificarDe(abrir: () -> InputStream?, anchoMax: Int): Bitmap? {
        val medir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir()?.use { BitmapFactory.decodeStream(it, null, medir) }

        // Si no se han podido leer las medidas, NO se sigue. Antes se caia por
        // aqui con outWidth = 0: la division daba 0, la muestra se quedaba en 1
        // y se intentaba decodificar la imagen a tamaño completo.
        if (medir.outWidth <= 0 || medir.outHeight <= 0) return null

        var muestra = 1
        while (medir.outWidth / muestra > anchoMax * 2) muestra *= 2
        return abrir()?.use { ins ->
            BitmapFactory.decodeStream(ins, null,
                BitmapFactory.Options().apply {
                    inSampleSize = muestra
                    if (anchoMax <= MINI || anchoMax >= DETALLE)
                        inPreferredConfig = Bitmap.Config.RGB_565
                })
        }
    }

}
