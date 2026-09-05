package com.dani.lector.datos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Convierte los CBR de tu biblioteca a CBZ, en tu carpeta.
 *
 * POR QUE EXISTE
 *
 * Un CBZ se lee EN CHORRO: se saca una pagina sin tocar el resto. Un CBR no:
 * junrar, con el stream que da SAF —que no se puede rebobinar—, tiene que
 * cargar el archivo entero en memoria para saltar por el. Un CBZ de 500 MB va
 * fino y un CBR de 366 MB cierra la app. El formato, no el tamaño.
 *
 * Convertidos una vez, esos comics dejan de ser un caso especial para siempre.
 *
 * COMO SE COMPORTA CON TUS FICHEROS, que es lo que importa
 *
 *  1. Convierte a un CBZ en la cache de la app, con [Rar5], que es TODO O NADA:
 *     si una sola pagina falla no hay fichero.
 *  2. Solo si eso ha ido bien, escribe el .cbz al lado del original.
 *  3. **Cuenta las paginas de los dos** y solo borra el CBR si el CBZ no tiene
 *     menos.
 *
 * Ese paso 3 no es una precaucion de manual. El 25/08/2026 habia un
 * `Blackest Night.cbz` de 19 paginas al lado de un CBR de 531: la regla
 * "si ya hay un CBZ, el CBR sobra" habria borrado 512 paginas sin que nadie se
 * enterara. Contar es barato; un comic perdido no se recupera.
 *
 * Si algo falla a mitad, el original se queda donde esta. El peor caso es que
 * sobre un .cbz al lado, nunca que falte el comic.
 */
object ConversorCarpeta {

    /**
     * El nombre de verdad esta en `Biblioteca.kt`, en `:shared`.
     *
     * Se deja este alias para no tocar a los tres que ya lo llamaban asi. La
     * cadena vive en comun porque **la regla de no enseñar esta carpeta es de
     * quien lee la biblioteca**, y en el iPad quien lee no es este fichero.
     */
    const val CARPETA_ORIGINALES = com.dani.lector.datos.CARPETA_ORIGINALES

    data class Resultado(
        val convertidos: Int,
        val fallidos: List<String>,
        val mensaje: String
    )

    /**
     * Convierte todos los CBR de [comics].
     *
     * [avance] se llama con un texto para la pantalla, que esto tarda: cada
     * comic se descomprime y se vuelve a empaquetar entero.
     */
    suspend fun convertir(
        ctx: Context,
        raiz: String,
        comics: List<Comic>,
        avance: (String) -> Unit
    ): Resultado = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(raiz)
        val cr = ctx.contentResolver

        // Solo los que son RAR de verdad. Se mira la FIRMA, no la extension:
        // hay ficheros .cbr que por dentro son ZIP y no hay nada que convertir.
        val candidatos = comics.filter {
            val f = ComicZip.formatoDe(ctx, it.uri)
            f == Formato.RAR4 || f == Formato.RAR5
        }

        if (candidatos.isEmpty())
            return@withContext Resultado(0, emptyList(), "No hay ningún CBR que convertir.")

        var hechos = 0
        val fallidos = mutableListOf<String>()

        candidatos.forEachIndexed { i, comic ->
            val corto = comic.nombre.substringBeforeLast('.')
            avance("Convirtiendo ${i + 1} de ${candidatos.size} · $corto")

            val fallo = convertirUno(ctx, cr, treeUri, comic)
            if (fallo == null) hechos++ else fallidos.add("$corto — $fallo")
        }

        Resultado(hechos, fallidos, buildString {
            append("Listos $hechos de ${candidatos.size}")
            if (fallidos.isEmpty()) append(". Los CBR se han borrado.")
            else append(", ${fallidos.size} sin tocar (el CBR sigue ahí).")
        })
    }

    /**
     * Devuelve null si ha ido bien, o el motivo si no.
     *
     * EL ORDEN ES LA SEGURIDAD. Nunca se toca el CBR sin haber contado antes las
     * paginas de los dos ficheros y comprobado que el CBZ no tiene menos.
     *
     * No es una precaucion de manual: el 25/08/2026 habia un `Blackest
     * Night.cbz` de 19 paginas al lado de un CBR de 531. La regla "si ya hay un
     * CBZ, el CBR sobra" habria borrado 512 paginas sin que nadie se enterara.
     * Contar es barato; un comic perdido no se recupera.
     */
    private fun convertirUno(
        ctx: Context,
        cr: android.content.ContentResolver,
        treeUri: Uri,
        comic: Comic
    ): String? = try {
        if (comic.padreId.isBlank()) "no se sabe en qué carpeta está"
        else {
            val base = comic.nombre.substringBeforeLast('.')
            val nombreCbz = "$base.cbz"
            val padreUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, comic.padreId)
            val delCbr = paginasDe(ctx, comic.uri)

            when {
                delCbr == null -> "no se ha podido leer el CBR: $ultimoMotivo"

                else -> {
                    // Tambien se busca con la extension doble: si la primera
                    // version dejo un "X.cbz.zip", ese comic YA esta convertido
                    // y volver a convertirlo es lo que genero los duplicados.
                    val existente = buscarHijo(cr, treeUri, comic.padreId, nombreCbz)
                        ?: buscarHijo(cr, treeUri, comic.padreId, "$nombreCbz.zip")
                    if (existente != null) {
                        // Ya hay CBZ: no se convierte nada, solo se decide si el
                        // CBR sobra. Y eso se decide contando, no suponiendo.
                        val delCbz = paginasDe(ctx, existente.toString())
                        when {
                            delCbz == null ->
                                "ya existe un $nombreCbz pero no se puede leer: no toco nada"
                            delCbz < delCbr ->
                                "el $nombreCbz que ya había tiene $delCbz páginas y el CBR " +
                                "$delCbr: no toco nada, míralo tú"
                            else -> borrarOriginal(cr, comic)
                        }
                    } else {
                        val cbz = Rar5.aCbz(ctx, comic.uri, ComicZip.formatoDe(ctx, comic.uri))
                        if (cbz == null) Rar5.ultimoFallo() ?: "no se ha podido convertir"
                        else {
                            val nuevo = crearConNombre(cr, padreUri, nombreCbz)

                            if (nuevo == null)
                                "no se ha podido crear el fichero (¿permiso de escritura?)"
                            else {
                                cr.openOutputStream(nuevo)!!.use { out ->
                                    cbz.inputStream().use { it.copyTo(out, 64 * 1024) }
                                }
                                // Se vuelve a abrir lo ESCRITO, no lo de la
                                // cache: lo que importa es el fichero que ha
                                // quedado en la carpeta.
                                val escritas = paginasDe(ctx, nuevo.toString())
                                val fallo = when {
                                    escritas == null ->
                                        "el CBZ escrito no se puede leer: no toco el original"
                                    escritas < delCbr ->
                                        "el CBZ escrito tiene $escritas de $delCbr páginas: " +
                                        "lo dejo ahí pero NO borro el CBR"
                                    else -> borrarOriginal(cr, comic)
                                }

                                // LA COPIA DE LA CACHE SE TIRA AQUI, y esto no
                                // es limpieza fina: es un tomo ENTERO por cada
                                // comic convertido.
                                //
                                // Rar5 guarda el CBZ que fabrica en la cache de
                                // la app para no tener que rehacerlo al leer.
                                // Eso tiene sentido cuando se LEE un CBR. Aqui
                                // no: el CBZ bueno acaba de quedarse en la
                                // carpeta del usuario y esta copia ya no sirve
                                // para nada. Convirtiendo la biblioteca entera
                                // se juntaron 3,78 GB de comics duplicados en
                                // la cache (Dani, 26/08/2026).
                                //
                                // Se borra pase lo que pase: si la escritura ha
                                // ido mal, el original sigue ahi y volver a
                                // convertirlo es cuestion de darle otra vez al
                                // boton. Guardar gigas por si acaso no compensa.
                                Rar5.olvidar(ctx, comic.uri)
                                fallo
                            }
                        }
                    }
                }
            }
        }
    } catch (t: Throwable) {
        "${t.javaClass.simpleName} ${t.message ?: ""}"
    }

    /**
     * Limpia la biblioteca: arregla nombres y quita duplicados.
     *
     * Hace tres cosas, y en este orden a proposito:
     *
     *  1. **Doble extension**: "X.cbz.zip" -> "X.cbz". Lo dejo la primera
     *     version del conversor, y no es cosmetico: rompe el numero de la grapa
     *     (ver [crearConNombre]).
     *  2. **Copias sin original**: si hay un "X (1).cbz" y NO hay un "X.cbz",
     *     el "(1)" sobra y se quita renombrando.
     *  3. **Copias con original**: si existen los dos, se cuentan las paginas de
     *     ambos y solo se borra la copia si tiene **las mismas** que el original.
     *     Si no coinciden no se toca nada y se dice con los dos numeros.
     *
     * Ese punto 3 es la misma regla que impidio perder 512 paginas de Blackest
     * Night: dos ficheros con nombres parecidos no son el mismo comic hasta que
     * se comprueba.
     *
     * Si el paso 1 renombra algo, la pasada TERMINA ahi. Los nombres que tiene
     * esta lista en memoria ya no son los del disco, y seguir buscando
     * duplicados sobre datos viejos es como se borra el fichero equivocado.
     * Se avisa y con darle otra vez al boton se sigue.
     */
    suspend fun limpiar(
        ctx: Context,
        comics: List<Comic>,
        avance: (String) -> Unit
    ): Resultado = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        var renombrados = 0
        var borrados = 0
        val avisos = mutableListOf<String>()
        // Se declara aqui porque el paso 1 ya puede borrar duplicados, no solo
        // renombrar.

        // ── 1. doble extension, MIRANDO SI EL BUENO YA ESTA ──
        //
        // Renombrar a secas fue un error caro: si "X.cbz" ya existia, Android
        // resuelve la colision poniendo "X (1).cbz", asi que el arreglo CREABA
        // duplicados. Y encima con un "(1)" que luego hay que distinguir de los
        // numeros de grapa de verdad.
        //
        // Ahora, si el bueno ya esta, no se renombra: se compara y se borra el
        // sobrante, que es lo que de verdad se queria.
        comics.groupBy { it.padreId }.forEach { (_, enCarpeta) ->
            val porNombre = enCarpeta.associateBy { it.nombre.lowercase() }
            enCarpeta.forEach { c ->
                val bueno = Limpieza.sinDobleExtension(c.nombre) ?: return@forEach
                avance("Arreglando nombre · ${c.nombre}")

                val yaEsta = porNombre[bueno.lowercase()]
                if (yaEsta == null) {
                    if (renombrar(cr, c.uri, bueno)) renombrados++
                } else {
                    // Los dos existen: sobra uno, pero solo si son el mismo.
                    val a = paginasDe(ctx, c.uri)
                    val b = paginasDe(ctx, yaEsta.uri)
                    when {
                        a == null || b == null ->
                            avisos.add("${c.nombre} — no se puede leer uno de los dos, " +
                                       "no toco nada")
                        a != b ->
                            avisos.add("${c.nombre} tiene $a páginas y ${yaEsta.nombre} " +
                                       "$b: no toco nada, míralo tú")
                        else -> if (borrarUri(cr, c.uri)) borrados++
                    }
                }
            }
        }
        if (renombrados > 0) {
            return@withContext Resultado(renombrados, avisos,
                "Arreglados $renombrados nombres con extensión doble" +
                (if (borrados > 0) " y borrados $borrados sobrantes" else "") +
                ". Dale otra vez para buscar duplicados: los nombres han cambiado " +
                "y prefiero no trabajar sobre la lista vieja.")
        }

        // ── 2 y 3. duplicados, carpeta por carpeta ──
        comics.groupBy { it.padreId }.forEach { (_, enCarpeta) ->
            val porNombre = enCarpeta.associateBy { it.nombre.lowercase() }

            enCarpeta.forEach { c ->
                val original = Limpieza.originalDe(c.nombre) ?: return@forEach
                val otro = porNombre[original.lowercase()]

                avance("Revisando · ${c.nombre}")

                if (otro == null) {
                    // NO SE TOCA. Sin un original al lado no hay forma de saber
                    // si ese "(n)" es una marca de copia o parte del nombre del
                    // comic, y casi siempre es lo segundo:
                    //
                    //   "Green Lantern Corps (21).cbz"  -> el 21 es el NUMERO
                    //   "Batman (2016).cbz"             -> el 2016 es el AÑO
                    //
                    // La primera version renombraba estos y se cargaba el numero
                    // de la grapa. Lo cazo Dani en una captura antes de que
                    // llegara a ejecutarse.
                    //
                    // Un duplicado de verdad SIEMPRE tiene a su original al
                    // lado; si no esta, no es un duplicado.
                    return@forEach
                } else {
                    val dela = paginasDe(ctx, c.uri)
                    val delOtro = paginasDe(ctx, otro.uri)
                    when {
                        dela == null || delOtro == null ->
                            avisos.add("${c.nombre} — uno de los dos no se puede leer, " +
                                       "no toco nada")
                        dela != delOtro ->
                            avisos.add("${c.nombre} tiene $dela páginas y " +
                                       "${otro.nombre} $delOtro: no son el mismo, " +
                                       "míralo tú")
                        else -> if (borrarUri(cr, c.uri)) borrados++
                                else avisos.add("${c.nombre} — no se ha podido borrar")
                    }
                }
            }
        }

        // ── 4. numeros entre parentesis a formato de grapa ──
        //
        // "Green Lantern Corps (1).cbz" -> "Green Lantern Corps #01.cbz"
        //
        // No es cosmetico: Parser quita a proposito lo que va entre parentesis
        // (para que un "(2016)" no cuele como numero), asi que esos comics se
        // quedan SIN numero. Sin numero no hay chapa en la portada y el orden es
        // alfabetico: (1), (10), (11), (12)... en vez de 1, 2, 3.
        //
        // Solo se toca si no se ha tocado nada antes en esta pasada: los nombres
        // de la lista en memoria tienen que seguir siendo los del disco.
        if (renombrados == 0 && borrados == 0) {
            comics.groupBy { it.padreId }.forEach { (_, enCarpeta) ->
                // Que numero es una grapa y cuantas cifras se rellenan lo decide
                // Limpieza, mirando la carpeta entera —el choque con un fichero
                // que ya existe incluido. Aqui solo se ejecuta.
                val plan = Limpieza.aGrapa(enCarpeta.map { it.nombre })
                    .associateBy { it.viejo }

                enCarpeta.forEach { c ->
                    val g = plan[c.nombre] ?: return@forEach
                    if (g.choca) {
                        avisos.add("${c.nombre} — ya existe un ${g.nuevo}, no toco nada")
                        return@forEach
                    }
                    avance("Numerando · ${c.nombre}")
                    if (renombrar(cr, c.uri, g.nuevo)) renombrados++
                }
            }
        }

        Resultado(renombrados + borrados, avisos, buildString {
            if (renombrados == 0 && borrados == 0 && avisos.isEmpty())
                append("Nada que limpiar: ni nombres raros ni duplicados.")
            else {
                append("Borrados $borrados duplicados")
                if (renombrados > 0) append(" y arreglados $renombrados nombres")
                append(".")
                if (avisos.isNotEmpty())
                    append(" ${avisos.size} sin tocar, mira abajo.")
            }
        })
    }

    private fun renombrar(
        cr: android.content.ContentResolver, uri: String, nuevo: String
    ): Boolean = try {
        DocumentsContract.renameDocument(cr, Uri.parse(uri), nuevo) != null
    } catch (_: Throwable) { false }

    private fun borrarUri(
        cr: android.content.ContentResolver, uri: String
    ): Boolean = try {
        DocumentsContract.deleteDocument(cr, Uri.parse(uri))
    } catch (_: Throwable) { false }

    /**
     * Crea el documento y se asegura de que se llame EXACTAMENTE asi.
     *
     * Se pide con mime `application/zip` porque los proveedores de SAF no
     * conocen los mimes de comic y algunos se niegan a crear el documento. Pero
     * entonces **le añaden su propia extension**: pidiendo "X.cbz" crean
     * "X.cbz.zip".
     *
     * Y eso, que parece cosmetico, rompe el numero de la grapa. `numeroDe` quita
     * la ultima extension, se queda con "...#01.cbz", y al buscar el numero
     * descarta ese trozo por su regla de "mas de dos letras no es un numero"
     * —la misma que evita que "Amazing" cuele—. Resultado: las 84 portadas
     * convertidas se quedaron sin su chapa con el numero.
     *
     * Asi que se crea, se mira como ha quedado, y si no es el nombre que se
     * pidio se renombra.
     */
    private fun crearConNombre(
        cr: android.content.ContentResolver,
        padreUri: Uri,
        nombre: String
    ): Uri? {
        val creado = DocumentsContract.createDocument(cr, padreUri, "application/zip", nombre)
            ?: return null
        val real = nombreDe(cr, creado)
        if (real == null || real.equals(nombre, ignoreCase = true)) return creado
        return try {
            DocumentsContract.renameDocument(cr, creado, nombre) ?: creado
        } catch (_: Throwable) { creado }
    }

    private fun nombreDe(cr: android.content.ContentResolver, uri: Uri): String? = try {
        cr.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                 null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Throwable) { null }

    /**
     * Cuantas paginas tiene un comic, o null si no se puede leer.
     *
     * El motivo se deja en [ultimoMotivo] en vez de perderlo. La primera version
     * devolvia solo null y en pantalla salia "no se ha podido leer el CBR" para
     * cualquier cosa: sirve para saber que ha fallado y no para saber por que,
     * que es justo lo que hace falta.
     */
    @Volatile private var ultimoMotivo: String = ""

    private fun paginasDe(ctx: Context, uri: String): Int? {
        return when (val p = ComicZip.paginas(ctx, uri)) {
            is Paginas.Ok -> {
                if (p.nombres.isEmpty()) { ultimoMotivo = "no tiene imágenes dentro"; null }
                else p.nombres.size
            }
            is Paginas.Error -> { ultimoMotivo = p.motivo; null }
        }
    }

    /**
     * Borra el CBR original. Solo se llama con las paginas ya comparadas.
     *
     * Si el proveedor no deja borrar, se DICE y el fichero se queda donde
     * esta. Nunca se da por hecho que se ha borrado: el mensaje distingue
     * "hecho" de "el CBZ esta bien pero el CBR sigue ahi", que son dos
     * situaciones distintas para quien luego mire la carpeta.
     */
    private fun borrarOriginal(
        cr: android.content.ContentResolver,
        comic: Comic
    ): String? = try {
        if (DocumentsContract.deleteDocument(cr, Uri.parse(comic.uri))) null
        else "el CBZ está bien pero el CBR no se ha podido borrar (sigue ahí)"
    } catch (t: Throwable) {
        "el CBZ está bien pero el CBR no se ha podido borrar " +
        "(${t.javaClass.simpleName}); sigue ahí"
    }

    /** Busca un hijo por nombre dentro de una carpeta. */
    private fun buscarHijo(
        cr: android.content.ContentResolver,
        treeUri: Uri,
        padreId: String,
        nombre: String,
        soloCarpetas: Boolean = false
    ): Uri? {
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, padreId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        try {
            cr.query(hijos, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val esDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    if (soloCarpetas && !esDir) continue
                    if (!c.getString(1).equals(nombre, ignoreCase = true)) continue
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                }
            }
        } catch (_: Throwable) { }
        return null
    }
}
