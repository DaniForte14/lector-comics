package com.dani.lector.datos

import androidx.compose.ui.graphics.ImageBitmap

/**
 * iOS — El [Archivo] del iPad. Ata [Zip], [ZipIOS] e [ImagenIOS] y poco mas.
 *
 * ES UN ENVOLTORIO FINO, igual que `ArchivoAndroid`: las tres piezas de debajo
 * ya saben lo suyo —el indice, descomprimir y decodificar ya reducido— y aqui
 * solo se decide el orden en que se llaman y que se hace cuando algo falla.
 * Casi toda la logica de verdad esta en `commonMain` y ya tiene pruebas.
 *
 * TRES COSAS QUE **NO** HACE, y estan puestas a proposito. Ninguna es un olvido:
 *
 *  1. **No abre CBR.** En el iPad no hay motor de RAR: junrar es Java y
 *     7-Zip-JBinding es JVM mas una libreria nativa, y ninguno cruza a
 *     Kotlin/Native. En vez de fallar con un mensaje raro de ZIP, se dice lo
 *     que pasa. Si algun dia hay CBR en el iPad sera por otra via —convertirlos
 *     antes, o un motor nuevo— y sera otra decision, no un parche aqui.
 *  2. **No recorta bordes.** El parametro [Archivo.pagina] lo pide y aqui se
 *     ignora. `Recorte` decide el recuadro y es comun, pero necesita los
 *     pixeles: en Android los saca `RecorteAndroid` de un `Bitmap`, y aqui
 *     harian falta los de [ImagenIOS] antes de envolverlos en Skia. Es otro
 *     fichero y otra vuelta de CI, y sin recorte la pagina se ve entera y bien.
 *  3. **No cachea paginas.** `ComicZip` tiene tres caches cuyos numeros
 *     costaron cierres de la app, y ninguno de esos numeros vale aqui: en iOS
 *     una imagen va en RGBA8888 y ocupa **el doble** que la de Android en 565,
 *     y `Runtime.maxMemory()` no existe fuera de la JVM, asi que el techo
 *     tendria que ser una cifra inventada. **Mejor que se note lento a que
 *     arrastre una cache mal dimensionada**: lo lento se mide, lo otro mata la
 *     app sin dejar rastro.
 *
 * LO UNICO QUE SI SE GUARDA ES EL INDICE DEL ULTIMO ARCHIVO, y no es una cache
 * de las de arriba: son unas decenas de [EntradaZip], sin un solo pixel. Sin
 * esto, **cada pasada de pagina volveria a leer la cola del fichero y a montar
 * el indice entero**, que es justo el trabajo que [Zip] hace una vez. Se guarda
 * uno y no un mapa porque se lee un comic a la vez.
 *
 * ESCRITO Y SIN COMPILAR: desde Windows no hay Kotlin/Native. Lo ve por primera
 * vez el runner de macOS del CI.
 */
class ArchivoIOS : Archivo {

    private var ultimoUri: String? = null
    private var ultimasEntradas: List<EntradaZip>? = null

    /**
     * De la cadena opaca de [Archivo] a una ruta que el sistema pueda abrir.
     *
     * HOY ES LA IDENTIDAD, Y ES UN SITIO DE PASO. En Android el `uri` es una uri
     * de SAF; en iOS sera el marcador de un *security-scoped bookmark*, porque
     * una app en el iPad no puede guardarse una ruta y volver a abrirla mañana.
     * Quien sepa resolverlo sera `BibliotecaIOS`, **que todavia no existe**.
     * Mientras tanto esto acepta rutas normales, que es lo que hace falta para
     * que las otras tres piezas se puedan probar de una vez en el CI.
     */
    private fun ruta(uri: String) = uri

    private fun entradasDe(uri: String): List<EntradaZip>? {
        if (uri == ultimoUri) return ultimasEntradas
        val e = ZipIOS.entradas(ruta(uri))
        // Se recuerda tambien el fallo: si este archivo no se ha podido leer,
        // volver a intentarlo por cada pagina es repetir el mismo error caro.
        ultimoUri = uri
        ultimasEntradas = e
        return e
    }

    override fun paginas(uri: String): Paginas {
        if (esRar(uri)) return Paginas.Error(
            "Los CBR no se pueden abrir en el iPad: no hay motor de RAR. " +
            "Conviertelo a CBZ desde el movil."
        )

        val entradas = entradasDe(uri)
            ?: return Paginas.Error("No es un ZIP, o el archivo está incompleto.")

        val nombres = Imagenes.ordenadas(
            entradas.map { it.nombre }.filter { Imagenes.es(it) }
        )
        return if (nombres.isEmpty()) Paginas.Error("El archivo no tiene imágenes dentro.")
        else Paginas.Ok(nombres)
    }

    override fun pagina(
        uri: String, nombre: String, anchoMax: Int, recortar: Boolean
    ): ImageBitmap? {
        val entrada = entradasDe(uri)?.firstOrNull { it.nombre == nombre } ?: return null
        val datos = ZipIOS.datos(ruta(uri), entrada) ?: return null
        return ImagenIOS.decodificar(datos, anchoMax)
    }

    /**
     * NO HACE NADA, Y ES LO CORRECTO MIENTRAS NO HAYA CACHE.
     *
     * Precargar es dejar hecho un trabajo para que alguien lo encuentre hecho
     * despues. Sin sitio donde dejarlo, decodificar las paginas de alrededor
     * seria gastar procesador y memoria para tirar el resultado — y ademas en
     * el hilo de quien llama. El dia que haya cache, esto se llena; hoy, callar
     * es mas barato que fingir.
     */
    override fun precargar(
        uri: String, nombres: List<String>, actual: Int, anchoMax: Int, recortar: Boolean
    ) {
    }

    private fun esRar(uri: String) =
        uri.substringAfterLast('.', "").lowercase() == "cbr"
}
