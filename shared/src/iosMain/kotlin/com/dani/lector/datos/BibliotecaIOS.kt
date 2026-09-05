package com.dani.lector.datos

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970

/**
 * iOS — La [Biblioteca] del iPad: marcadores y `NSFileManager`.
 *
 * **ES LA PIEZA MAS DISTINTA DE TODO EL PORT**, y no porque leer una carpeta sea
 * dificil —lo es una linea— sino por el permiso. En Android el usuario elige una
 * carpeta una vez y SAF da un permiso persistente sobre su arbol. En iOS lo que
 * da el selector de documentos es **un acceso que se muere al cerrar la app**:
 * para volver a entrar mañana hay que guardar un *security-scoped bookmark* y
 * resolverlo en cada arranque.
 *
 * COMO SE REPARTEN LAS CADENAS OPACAS DE [Biblioteca], que es lo primero que
 * habria que mirar si algo no cuadra:
 *
 * | | Android | iOS |
 * |---|---|---|
 * | `raiz` | uri de arbol de SAF | **el marcador, en base64** |
 * | `docId` | id de documento | **la ruta absoluta de la carpeta** |
 * | `Comic.uri` | uri de documento | **la ruta absoluta del fichero** |
 *
 * Y esa ultima fila es la que cierra el circulo con [ArchivoIOS]: su
 * `ruta(uri) = uri` esperaba justo esto, una ruta que el sistema pueda abrir.
 *
 * **EL ACCESO SE ABRE UNA VEZ Y NO SE CIERRA**, y es una decision, no un
 * descuido. `startAccessingSecurityScopedResource` habria que equilibrarlo con
 * su `stop`, pero quien lee los bytes de una pagina es [ArchivoIOS] **mucho
 * despues** de que esto haya devuelto la lista, y no tiene el `NSURL` de la
 * raiz ni por que tenerlo. Cerrar el acceso al salir de aqui dejaria la app sin
 * poder abrir ni un comic. Se abre en el primer uso y se mantiene mientras la
 * app viva, que es una raiz y un acceso: si algun dia hay varias bibliotecas
 * abiertas a la vez, esto hay que repensarlo.
 *
 * **ESCRITO Y SIN COMPILAR**, como todo `iosMain` desde Windows. Y aqui hace
 * falta un aviso mas fuerte de lo normal: **el CI dira si compila, y compilar no
 * es funcionar**. Los marcadores son un mecanismo de tiempo de ejecucion —se
 * resuelven o no, caducan, se quedan rancios al mover el fichero— y eso **solo
 * se puede ver en un iPad de verdad**. Que este trabajo salga en verde no
 * significa que la biblioteca se abra.
 */
@OptIn(ExperimentalForeignApi::class)
class BibliotecaIOS : Biblioteca {

    private val fm = NSFileManager.defaultManager

    /** La raiz ya resuelta, para no rehacer el marcador en cada carpeta. */
    private var raizCache: Pair<String, String>? = null

    /**
     * Del marcador guardado a la ruta de la carpeta, abriendo el acceso.
     *
     * **DOS SITIOS DONDE APOSTARIA A QUE EL CI SE QUEJA**, siguiendo la
     * costumbre de esta serie de tandas de dejarlo escrito antes de mandarlo:
     *
     *  1. **El base64.** `NSData` se construye desde una cadena con un
     *     inicializador de Objective-C, y en Kotlin/Native los inicializadores
     *     se llaman `create`. Si el nombre o el tipo de `options` no cuadran,
     *     falla aqui.
     *  2. **`URLByResolvingBookmarkData`.** Lleva cinco parametros, dos de ellos
     *     punteros de salida. Se le pasa `null` al del error —no aporta nada:
     *     si no resuelve, no hay carpeta— pero **el de "esta rancio" SI se
     *     mira**, porque un marcador rancio resuelve y luego no deja leer, que
     *     es el fallo mas confuso de todos.
     *
     * Y una tercera, que el compilador **no** puede cazar: en iOS la opcion
     * `withSecurityScope` es de macOS. Aqui se resuelve sin opciones y se pide
     * el acceso despues, que es como funciona en el iPad. Si al probarlo de
     * verdad no deja abrir los ficheros, este es el primer sitio que mirar.
     */
    private fun rutaRaiz(marcador: String): String? {
        // UNA RUTA NORMAL SE ACEPTA TAL CUAL, igual que hace `ArchivoIOS` con su
        // `ruta(uri)`. No es un atajo: es lo que permite probar la cadena entera
        // —listar, abrir, decodificar— contra la carpeta Documents de la app,
        // **sin meter todavia el selector ni los marcadores**, que son la parte
        // que solo se puede juzgar en un iPad de verdad. Cuando el selector
        // exista, le llegara un marcador y esta linea no se activara.
        if (esCarpeta(marcador)) return marcador

        raizCache?.let { if (it.first == marcador) return it.second }

        val datos = NSData.create(base64EncodedString = marcador, options = 0u) ?: return null

        val url = memScoped {
            val rancio = alloc<BooleanVar>()
            val u = NSURL.URLByResolvingBookmarkData(datos, 0u, null, rancio.ptr, null)
            // Un marcador rancio es el que apunta a algo que se movio o se
            // volvio a instalar. Resuelve, y luego no deja leer: mejor tratarlo
            // como que no hay biblioteca y que el usuario la vuelva a elegir,
            // que dejarle una pantalla de comics que no se abren.
            if (rancio.value) null else u
        } ?: return null

        if (!url.startAccessingSecurityScopedResource()) return null

        val ruta = url.path ?: return null
        raizCache = marcador to ruta
        return ruta
    }

    /**
     * El marcador de una carpeta recien elegida, para guardarlo.
     *
     * ES EL PAR DE [rutaRaiz] Y POR ESO ESTA AQUI, aunque quien lo llame sea el
     * selector de documentos, que vivira en `iosApp`. El formato —marcador a
     * `NSData`, `NSData` a base64— tiene que ser el mismo en los dos lados, y
     * teniendolo en un solo fichero no hay dos formatos que puedan separarse
     * sin que nadie se entere.
     */
    fun marcadorDe(url: NSURL): String? =
        url.bookmarkDataWithOptions(0u, null, null, null)
            ?.base64EncodedStringWithOptions(0u)

    /**
     * `Default` Y NO `IO`, Y NO ES UNA PREFERENCIA: con kotlinx-coroutines 1.9.0
     * **`Dispatchers.IO` es `internal` en Kotlin/Native**. Existe en la JVM y por
     * eso el reflejo es escribirlo —lo hace `Escaner` en Android, y esta clase lo
     * llevaba— pero aqui no compila:
     *
     *     e: Cannot access 'val IO: CoroutineDispatcher':
     *        it is internal in 'kotlinx/coroutines/Dispatchers'
     *
     * Fuera de la JVM no hay una reserva de hilos aparte para esperar a disco, y
     * `Default` es la que hay. **Se cazo en el CI, como todas las de esta serie:
     * desde Windows el mismo codigo compila sin rechistar.**
     */
    override suspend fun abrir(
        raiz: String, docId: String?, ruta: String
    ): Contenido = withContext(Dispatchers.Default) {
        val base = rutaRaiz(raiz) ?: return@withContext Contenido(emptyList(), emptyList())
        val dentro = docId ?: base

        val carpetas = mutableListOf<Carpeta>()
        val comics = mutableListOf<Comic>()

        for (nombre in hijos(dentro)) {
            val completa = "$dentro/$nombre"
            if (esCarpeta(completa)) {
                if (nombre.lowercase() == CARPETA_ORIGINALES.lowercase()) continue
                val sub = if (ruta.isBlank()) nombre else "$ruta/$nombre"
                val cuenta = contar(completa)
                carpetas.add(Carpeta(completa, nombre, sub, cuenta.first, cuenta.second))
            } else if (Parser.esComic(nombre)) {
                comics.add(Comic(
                    uri = completa,
                    nombre = nombre,
                    carpeta = ruta,
                    padreId = dentro,
                    numero = Parser.numeroDe(nombre),
                    esEspecial = Parser.esEspecial(nombre),
                    cuando = modificado(completa)
                ))
            }
        }

        Contenido(
            carpetas.sortedBy { it.nombre.lowercase() },
            // Igual que el escaner de Android: SIEMPRE por numero, que es el
            // orden natural de una serie. Quien quiera otro lo pide con
            // OrdenCarpeta, para que el orden sea decision de la pantalla.
            OrdenCarpeta.de(comics, Orden.NUMERO)
        )
    }

    override suspend fun todosBajo(
        raiz: String, docId: String?, ruta: String
    ): List<Comic> = withContext(Dispatchers.Default) {
        val out = mutableListOf<Comic>()
        val base = rutaRaiz(raiz) ?: return@withContext out
        val pendientes = ArrayDeque<Pair<String, String>>()
        pendientes.addLast((docId ?: base) to ruta)
        val vistos = HashSet<String>()

        while (pendientes.isNotEmpty()) {
            val (id, r) = pendientes.removeLast()
            // Los enlaces simbolicos pueden hacer un ciclo, y sin esto seria un
            // bucle infinito en vez de un error. Es la misma guarda que Escaner.
            if (!vistos.add(id)) continue
            val c = abrir(raiz, id, r)
            out.addAll(c.comics)
            c.carpetas.forEach { pendientes.addLast(it.docId to it.ruta) }
        }
        out
    }

    private fun hijos(ruta: String): List<String> =
        (fm.contentsOfDirectoryAtPath(ruta, null) as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()

    private fun esCarpeta(ruta: String): Boolean = memScoped {
        val dir = alloc<BooleanVar>()
        fm.fileExistsAtPath(ruta, dir.ptr) && dir.value
    }

    /** Cuantas subcarpetas y cuantos comics tiene, sin bajar mas. */
    private fun contar(ruta: String): Pair<Int, Int> {
        var carpetas = 0
        var comics = 0
        for (nombre in hijos(ruta)) {
            if (esCarpeta("$ruta/$nombre")) {
                if (nombre.lowercase() != CARPETA_ORIGINALES.lowercase()) carpetas++
            } else if (Parser.esComic(nombre)) comics++
        }
        return carpetas to comics
    }

    /**
     * La fecha de modificacion **en milisegundos**, como la de Android.
     *
     * `timeIntervalSince1970` viene en SEGUNDOS y con decimales; SAF da
     * milisegundos. Sin el x1000 las dos plataformas ordenarian "por recientes"
     * en escalas distintas, y peor: los ficheros de iOS parecerian todos de
     * 1970 al lado de los de Android. Sin fecha, cero, que es "el mas viejo" y
     * es lo mismo que hace el escaner de Android.
     */
    private fun modificado(ruta: String): Long {
        val fecha = fm.attributesOfItemAtPath(ruta, null)
            ?.get(NSFileModificationDate) as? NSDate ?: return 0L
        return (fecha.timeIntervalSince1970 * 1000).toLong()
    }
}
