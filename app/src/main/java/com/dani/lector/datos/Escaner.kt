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
        ctx: Context, raiz: String, docId: String?, ruta: String = "",
        /**
         * Cuantos comics tiene cada subcarpeta, para el rotulo de su fila.
         *
         * SE PUEDE APAGAR PORQUE CUESTA UNA CONSULTA MAS POR SUBCARPETA. Quien
         * navega las quiere; [todosBajo] no, y era justo quien mas las pagaba:
         * recorriendo el arbol entero, cada carpeta se consultaba DOS veces
         * —una como `contar` desde su padre y otra como `abrir` al visitarla— y
         * las cuentas de esa segunda vuelta acababan en la basura.
         */
        conCuentas: Boolean = true
    ): Contenido = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(raiz)
        val id = docId ?: raizDe(raiz) ?: return@withContext Contenido(emptyList(), emptyList())

        val carpetas = mutableListOf<Carpeta>()
        val comics = mutableListOf<Comic>()

        // CRONOMETRO PARTIDO, PARA CAZAR LOS ~720 ms. Volver a la biblioteca
        // desde el visor tarda 700-725 ms de forma sospechosamente constante, y
        // esta misma lectura al arrancar tarda 20-60. Es la misma carpeta y el
        // mismo trabajo, asi que hay que saber DONDE se van: en la consulta de
        // hijos, en los `contar`, o en ninguna de las dos —y entonces es espera,
        // o sea contencion con otro recorrido del arbol—. Se apunta solo si
        // pasa de LENTO_MS: navegar normal no debe ensuciar el rastro.
        val t0 = System.currentTimeMillis()
        var msContar = 0L
        var tCursor = 0L

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
                        val cuenta = if (conCuentas) {
                            val tc = System.currentTimeMillis()
                            contar(ctx, treeUri, hijoId).also {
                                msContar += System.currentTimeMillis() - tc
                            }
                        } else 0 to 0
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
        tCursor = System.currentTimeMillis() - t0

        Contenido(
            carpetas.sortedBy { it.nombre.lowercase() },
            // El escaner devuelve SIEMPRE por numero, que es el orden natural
            // de una serie. Quien quiera otro lo pide con OrdenCarpeta: asi el
            // orden es una decision de la pantalla y no del que lee el disco.
            OrdenCarpeta.de(comics, Orden.NUMERO)
        ).also {
            val total = System.currentTimeMillis() - t0
            if (total >= LENTO_MS) Rastro.apunta(ctx,
                "  LENTA «${ruta.ifBlank { "raíz" }}»: $total ms " +
                "(cursor ${tCursor - msContar}, contar $msContar, " +
                "${carpetas.size} subcarpetas, ${comics.size} cómics)")
        }
    }

    /** A partir de aqui una lectura de carpeta se apunta en el rastro. */
    private const val LENTO_MS = 200

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
            // Sin cuentas: aqui solo hacen falta los comics y por donde seguir.
            val c = abrir(ctx, raiz, id, r, conCuentas = false)
            out.addAll(c.comics)
            c.carpetas.forEach { pendientes.addLast(it.docId to it.ruta) }
        }
        out
    }
}
