package com.dani.lector.datos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee el arbol de carpetas del usuario.
 *
 * NO usa DocumentFile: esa clase hace una consulta por cada atributo de cada
 * fichero y con carpetas grandes devuelve resultados incompletos. Aqui se
 * consulta el ContentResolver con un cursor y una sola consulta por carpeta.
 */
object Escaner {

    private val PROYECCION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        // Para poder ordenar por "recientes". Va en la MISMA consulta que ya se
        // hacia: una columna mas no cuesta otra vuelta a SAF, que es lo caro.
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    /** Lo que hay dentro de una carpeta concreta: subcarpetas y comics. */
    data class Contenido(val carpetas: List<Carpeta>, val comics: List<Comic>)

    /**
     * Carpetas que el escaner NO mira.
     *
     * `_cbr_originales` la crea la propia app al convertir CBR a CBZ, y guarda
     * los ficheros viejos. Si se escaneara:
     *
     *  - saldrian en el catalogo como una carpeta mas, y verias cada comic dos
     *    veces, el CBZ nuevo y el CBR guardado
     *  - la siguiente conversion los encontraria y volveria a convertirlos,
     *    dejando CBZ dentro de la carpeta de originales
     *
     * Es la carpeta de "papelera" de la app, no biblioteca.
     */
    private val IGNORADAS = setOf(ConversorCarpeta.CARPETA_ORIGINALES.lowercase())

    /** Id de documento de la raiz elegida. */
    fun raizDe(raiz: String): String? = try {
        DocumentsContract.getTreeDocumentId(Uri.parse(raiz))
    } catch (_: Exception) { null }

    /**
     * Abre una carpeta. [docId] null significa la raiz.
     * Solo lee UN nivel: la navegacion por carpetas no necesita mas y asi es
     * instantanea aunque tengas mil comics.
     */
    suspend fun abrir(
        ctx: Context, raiz: String, docId: String?, ruta: String = ""
    ): Contenido = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(raiz)
        val id = docId ?: raizDe(raiz) ?: return@withContext Contenido(emptyList(), emptyList())

        val carpetas = mutableListOf<Carpeta>()
        val comics = mutableListOf<Comic>()

        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        try {
            ctx.contentResolver.query(hijos, PROYECCION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val hijoId = c.getString(0) ?: continue
                    val nombre = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (nombre.lowercase() in IGNORADAS) continue
                        val sub = if (ruta.isBlank()) nombre else "$ruta/$nombre"
                        val cuenta = contar(ctx, treeUri, hijoId)
                        carpetas.add(Carpeta(hijoId, nombre, sub, cuenta.first, cuenta.second))
                    } else if (Parser.esComic(nombre)) {
                        comics.add(Comic(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, hijoId).toString(),
                            nombre = nombre,
                            carpeta = ruta,
                            padreId = id,
                            numero = Parser.numeroDe(nombre),
                            esEspecial = Parser.esEspecial(nombre),
                            // isNull antes de getLong: hay proveedores que no
                            // rellenan la fecha, y ahi getLong devuelve basura o
                            // revienta segun la implementacion. Sin fecha, cero,
                            // que es "el mas viejo de todos" y no rompe nada.
                            cuando = if (c.isNull(3)) 0L else c.getLong(3)
                        ))
                    }
                }
            }
        } catch (_: Exception) { }

        Contenido(
            carpetas.sortedBy { it.nombre.lowercase() },
            // El escaner devuelve SIEMPRE por numero, que es el orden natural
            // de una serie. Quien quiera otro lo pide con OrdenCarpeta: asi el
            // orden es una decision de la pantalla y no del que lee el disco.
            OrdenCarpeta.de(comics, Orden.NUMERO)
        )
    }

    /** Cuantas subcarpetas y cuantos comics tiene una carpeta, sin bajar mas. */
    private fun contar(ctx: Context, treeUri: Uri, docId: String): Pair<Int, Int> {
        var carpetas = 0
        var comics = 0
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        try {
            ctx.contentResolver.query(hijos, PROYECCION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val nombre = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (nombre.lowercase() !in IGNORADAS) carpetas++
                    } else if (Parser.esComic(nombre)) comics++
                }
            }
        } catch (_: Exception) { }
        return carpetas to comics
    }

    /** Todos los comics de una carpeta y sus subcarpetas. Para vincular al TODO. */
    suspend fun todosBajo(
        ctx: Context, raiz: String, docId: String?, ruta: String = ""
    ): List<Comic> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Comic>()
        val pendientes = ArrayDeque<Pair<String, String>>()
        val inicial = docId ?: raizDe(raiz) ?: return@withContext out
        pendientes.addLast(inicial to ruta)
        val vistos = HashSet<String>()

        while (pendientes.isNotEmpty()) {
            val (id, r) = pendientes.removeLast()
            if (!vistos.add(id)) continue
            val c = abrir(ctx, raiz, id, r)
            out.addAll(c.comics)
            c.carpetas.forEach { pendientes.addLast(it.docId to it.ruta) }
        }
        out
    }
}
