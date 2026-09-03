package com.dani.lector.datos

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Lo unico de la app que sabe leer RAR5, y lo hace UNA sola vez por comic.
 *
 * POR QUE ESTA ENCERRADO AQUI
 *
 * junrar es Java puro y lee RAR4. RAR5 necesita el motor nativo de 7-Zip, y el
 * codigo nativo es la parte que falla: un .so que no carga en una arquitectura,
 * una version de la libreria que cambia de API. Si eso estuviera repartido por
 * el visor, un fallo suyo se notaria en diez sitios distintos.
 *
 * Asi que el motor nativo no se usa para LEER. Se usa para CONVERTIR el CBR a
 * un CBZ que se guarda en la cache de la app, y a partir de ahi ese comic lo
 * lee el camino de siempre, el de ZIP, que lleva meses funcionando. Si esto
 * falla, falla en una funcion y con un motivo.
 *
 * Y de paso arregla dos cosas de un tiro:
 *  - los RAR5, que no se podian abrir
 *  - los RAR4 GRANDES, que cerraban la app: junrar, con un stream que no se
 *    puede rebobinar como el de SAF, se traga el archivo entero en memoria.
 *    Convertido a CBZ, ese comic no vuelve a pasar por junrar nunca.
 *
 * NO escribe nada en la carpeta de comics del usuario: todo vive en la cache
 * de la app, asi que no hace falta el permiso de escritura que se quito a
 * proposito. El boton que convierte de verdad en su carpeta es otra cosa y va
 * aparte.
 */
object Rar5 {

    /** Extensiones que se consideran pagina. Igual que en ComicZip. */
    private val EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    @Volatile private var iniciado = false
    @Volatile private var fallo: String? = null

    /** Por que no se ha podido, si no se ha podido. */
    fun ultimoFallo() = fallo

    /**
     * Arranca el motor nativo. Una vez por proceso.
     *
     * Es lo que extrae el .so de los assets del aar y lo carga. Si esto falla
     * —arquitectura no soportada, .so corrupto— no hay RAR5 posible, y lo suyo
     * es saberlo aqui y no en mitad de una pagina.
     *
     * SE LE PASA UN DIRECTORIO, no el Context. Esta version de la libreria solo
     * ofrece initSevenZipFromPlatformJAR(File) y (String); la sobrecarga con
     * Context que documentan algunas guias no existe aqui. Lo dijo el propio
     * compilador: "None of the following candidates is applicable".
     *
     * Se usa cacheDir porque el directorio es solo el sitio donde extraer la
     * libreria antes de cargarla. Si Android lo vacia, en el siguiente arranque
     * se vuelve a extraer y ya esta.
     *
     * OJO SI ESTO FALLA EN EJECUCION: desde Android 10, una app que apunta a
     * API 29 o mas —esta apunta a 35— no puede cargar codigo desde su propio
     * directorio de datos. Si el motivo que sale es un UnsatisfiedLinkError
     * hablando de permisos, es eso, y entonces la libreria tiene que traer sus
     * .so en jniLibs y no en assets. El mensaje lo dira: para eso se guarda.
     */
    @Synchronized
    private fun iniciar(ctx: Context): Boolean {
        if (iniciado) return true
        return try {
            SevenZip.initSevenZipFromPlatformJAR(ctx.cacheDir)
            iniciado = true
            fallo = null
            true
        } catch (t: Throwable) {
            // Throwable: aqui se cargan librerias nativas, y un
            // UnsatisfiedLinkError es un Error, no una Exception.
            fallo = "el motor de RAR5 no ha arrancado: " +
                    "${t.javaClass.simpleName} ${t.message ?: ""}"
            false
        }
    }

    private fun carpeta(ctx: Context) = File(ctx.cacheDir, "convertidos").apply { mkdirs() }

    private fun clave(uri: String): String =
        MessageDigest.getInstance("MD5").digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** El CBZ ya convertido de este comic, si existe. */
    fun yaConvertido(ctx: Context, uri: String): File? =
        File(carpeta(ctx), clave(uri) + ".cbz").takeIf { it.exists() && it.length() > 0 }

    /**
     * Convierte un CBR a CBZ en la cache y devuelve el fichero.
     *
     * Devuelve null y deja el motivo en [ultimoFallo] si no ha podido.
     * Es lento —descomprime y recomprime el tomo entero— asi que va en IO y
     * solo se hace una vez: la segunda vez lo encuentra hecho.
     */
    fun aCbz(ctx: Context, uri: String, formato: Formato = Formato.RAR5): File? {
        yaConvertido(ctx, uri)?.let { return it }

        // CADA MOTOR PARA LO SUYO.
        //
        // RAR4 lo convierte junrar y RAR5 el motor nativo. No es simetria por
        // gusto: con Blackest Night (RAR4, 531 paginas) 7-Zip decia tener 19
        // ficheros y junrar los 531 correctos. Se comprobo abriendo el mismo
        // archivo por los dos caminos. Usar el motor nativo "porque lee las dos
        // versiones" costo un CBZ de 19 paginas dado por bueno.
        if (formato == Formato.RAR4) return aCbzConJunrar(ctx, uri)

        if (!iniciar(ctx)) return null

        // El motor de 7-Zip lee las dos versiones de RAR, asi que RAR4 tambien
        // pasa por aqui. No es por gusto: junrar, con el stream de SAF que no se
        // puede rebobinar, se traga el ARCHIVO ENTERO en memoria, y con un tomo
        // grande eso es un OutOfMemoryError y la app fuera. Convertido a CBZ, el
        // comic no vuelve a pasar por junrar nunca.
        //
        // El formato NO se le impone: se abre con null y 7-Zip lo detecta solo.
        // Con Blackest Night, forzandolo a partir de la firma del fichero, el
        // motor decia tener 19 ficheros donde junrar veia 531. Que decida quien
        // va a leerlo. El parametro [formato] se queda para el mensaje de fallo
        // y por si algun dia hace falta forzarlo de verdad.

        val destino = File(carpeta(ctx), clave(uri) + ".cbz")
        // Se escribe a un temporal y se renombra al final. Si el proceso muere
        // a mitad, lo que queda es un .parcial que nadie va a leer, no un CBZ
        // truncado que parece bueno.
        val parcial = File(destino.absolutePath + ".parcial")

        // 7-Zip necesita acceso ALEATORIO al fichero, y SAF solo da un stream.
        // No hay forma de esquivarlo: se copia a la cache primero.
        val copia = File(carpeta(ctx), clave(uri) + ".src")

        return try {
            ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { ins ->
                copia.outputStream().use { out -> ins.copyTo(out, 64 * 1024) }
            }

            // TODO O NADA.
            //
            // La primera version salia del bucle con `return@use` al fallar una
            // pagina y luego seguia adelante renombrando el parcial como si
            // estuviera entero. Con un tomo de 531 paginas eso dejo un CBZ de 19
            // con nombre de convertido, y la app lo leia tan contenta hasta que
            // reventaba. Un fichero a medias con nombre de bueno es peor que no
            // tener fichero.
            //
            // Ahora se lleva la cuenta de lo esperado y lo escrito y solo se
            // asciende el parcial si cuadran. Nada de `return@use` con tres
            // `use` anidados: cual de los tres coge no se lee de un vistazo.
            // Tres cuentas y no una, y aqui esta la leccion:
            //
            //  - dentro  = ficheros que dice tener el archivo (sin carpetas)
            //  - esperadas = los que ademas parecen una pagina
            //  - escritas  = los que han llegado al CBZ
            //
            // La primera version solo comparaba esperadas con escritas, o sea
            // la lista del motor CONSIGO MISMA. Con Blackest Night el motor
            // dijo tener 19 ficheros, escribio 19, y la comprobacion dio por
            // buena una conversion a la que le faltaban 512 paginas.
            var dentro = 0
            var esperadas = 0
            var escritas = 0
            var corte: String? = null
            // Los primeros nombres que ve, para poder decirlos si no hay
            // ninguna pagina. "Ve 1 fichero y ninguno es una pagina" deja la
            // pregunta a medias: lo util es saber COMO se llama ese fichero.
            val vistos = mutableListOf<String>()

            RandomAccessFile(copia, "r").use { raf ->
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf))
                    .use { archivo ->
                        // Nivel 0: las paginas ya son JPG o PNG, volver a
                        // comprimirlas no baja el tamaño y en un tomo de 500
                        // paginas se nota muchisimo en el tiempo.
                        ZipOutputStream(parcial.outputStream().buffered()).use { zip ->
                            zip.setLevel(0)
                            val usados = HashSet<String>()

                            for (i in 0 until archivo.numberOfItems) {
                                if (corte != null) break

                                val esCarpeta =
                                    archivo.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                                if (esCarpeta) continue
                                dentro++

                                var nombre = (archivo.getProperty(i, PropID.PATH) as? String)
                                    ?.replace('\\', '/') ?: continue
                                if (vistos.size < 4) vistos.add(nombre.substringAfterLast('/'))
                                if (!esImagen(nombre)) continue
                                esperadas++

                                // Dos entradas con el mismo nombre hacen que
                                // putNextEntry lance y se pierda la conversion
                                // entera por un duplicado tonto.
                                if (!usados.add(nombre)) {
                                    nombre = "$i-$nombre"
                                    usados.add(nombre)
                                }

                                try {
                                    zip.putNextEntry(ZipEntry(nombre))
                                    val res = archivo.extractSlow(i, ISequentialOutStream { datos ->
                                        zip.write(datos)
                                        datos.size      // hay que devolver cuanto se ha escrito
                                    })
                                    zip.closeEntry()
                                    if (res != ExtractOperationResult.OK)
                                        corte = "la pagina ${esperadas} no se ha podido " +
                                                "extraer del CBR ($res)"
                                    else escritas++
                                } catch (t: Throwable) {
                                    corte = "${t.javaClass.simpleName} en la pagina $esperadas" +
                                            (t.message?.let { ": $it" } ?: "")
                                }
                            }
                        }
                    }
            }

            if (esperadas == 0) {
                // El numero importa: "0 de 0" es un archivo que no se ha sabido
                // abrir, y "0 de 340" es un archivo que se abre pero cuyos
                // ficheros no parecen paginas. Son dos problemas distintos y
                // antes los dos decian lo mismo.
                // El caso del PDF merece su propio mensaje: no es un fallo del
                // fichero ni de la app, es que ese comic no son paginas sueltas.
                // Con el mensaje generico parecia que algo iba mal.
                val soloPdf = vistos.isNotEmpty() &&
                    vistos.all { it.substringAfterLast('.', "").lowercase() == "pdf" }
                fallo = if (soloPdf)
                    "dentro hay un PDF (${vistos.first()}), no páginas sueltas. " +
                    "La app todavía no lee PDF, así que no hay nada que convertir"
                else
                    "el motor ve $dentro ficheros dentro y ninguno parece una página" +
                    if (vistos.isEmpty()) "" else ": " + vistos.joinToString(", ")
                parcial.delete()
                return null
            }
            if (corte != null || escritas != esperadas) {
                fallo = corte ?: "solo se han convertido $escritas de $esperadas paginas"
                parcial.delete()
                return null
            }
            // Y la comprobacion que faltaba: que lo escrito se parezca a lo que
            // hay DENTRO del archivo. Si el motor dice tener 19 ficheros y el
            // comic tiene 500 paginas, algo va mal en la lectura del archivo y
            // el CBZ resultante seria una mentira convincente.
            if (escritas < dentro) {
                fallo = "el archivo dice tener $dentro ficheros y solo $escritas " +
                        "son paginas: puede estar partido en varios volumenes o dañado"
                parcial.delete()
                return null
            }

            destino.delete()
            if (!parcial.renameTo(destino)) {
                fallo = "no se ha podido guardar el CBZ convertido"
                parcial.delete()
                return null
            }
            fallo = null
            podar(ctx)          // no dejar que la cache crezca sin fin
            destino
        } catch (t: Throwable) {
            // Throwable otra vez: por aqui pasa un tomo entero y el
            // OutOfMemoryError no es una Exception.
            fallo = "${t.javaClass.simpleName} ${t.message ?: ""}"
            parcial.delete()
            null
        } finally {
            copia.delete()
        }
    }

    /**
     * Convierte un CBR de RAR4 usando junrar, que es quien lo lee bien.
     *
     * `extractFile` escribe DIRECTO al ZipOutputStream: la pagina no pasa por un
     * array intermedio en ningun momento. Eso importa con un tomo de 500
     * paginas, que es donde reventaba la version anterior.
     *
     * Aqui no hace falta comparar contra "lo que dice el archivo": junrar
     * recorre las cabeceras una por una y lo que no aparece es que no esta.
     */
    private fun aCbzConJunrar(ctx: Context, uri: String): File? {
        val destino = File(carpeta(ctx), clave(uri) + ".cbz")
        val parcial = File(destino.absolutePath + ".parcial")
        var escritas = 0

        // Se copia a la cache y se le da a junrar un FICHERO.
        //
        // Con el InputStream de SAF, junrar no puede saltar por el archivo y lo
        // buffea entero en memoria: convertir Blackest Night (366 MB) daba
        // OutOfMemoryError con el monton a tope de sus 256 MB. Con un fichero
        // lee por posiciones y no guarda nada.
        //
        // Curiosidad util: leer UNA pagina si funcionaba, porque solo busca su
        // cabecera y extrae esa. Recorrerlo entero es otra cosa. No es lo mismo
        // "junrar abre este comic" que "junrar puede recorrerlo".
        val copia = File(carpeta(ctx), clave(uri) + ".src")

        return try {
            ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { ins ->
                copia.outputStream().use { out -> ins.copyTo(out, 64 * 1024) }
            }
            run {
                Archive(copia).use { a ->
                    ZipOutputStream(parcial.outputStream().buffered()).use { zip ->
                        // Nivel 0: las paginas ya son JPG, recomprimirlas no
                        // ahorra nada y en 500 paginas se nota un mundo.
                        zip.setLevel(0)
                        val usados = HashSet<String>()

                        // TODAS las cabeceras primero, y luego extraer.
                        //
                        // Intercalar nextFileHeader() con extractFile() no vale:
                        // extraer mueve la posicion de lectura del archivo y la
                        // siguiente cabecera sale mal o no sale. Se leen todas de
                        // una y despues se extrae, que es como junrar espera que
                        // se use.
                        val cabeceras = a.fileHeaders.filter {
                            !it.isDirectory && esImagen(it.fileName.replace('\\', '/'))
                        }

                        for (h in cabeceras) {
                            val n = h.fileName.replace('\\', '/')
                            val nombre = if (usados.add(n)) n else "$escritas-$n"
                            usados.add(nombre)
                            zip.putNextEntry(ZipEntry(nombre))
                            a.extractFile(h, zip)
                            zip.closeEntry()
                            escritas++
                        }
                    }
                }
            }

            if (escritas == 0) {
                fallo = "el CBR no tiene imagenes dentro"
                parcial.delete()
                return null
            }
            destino.delete()
            if (!parcial.renameTo(destino)) {
                fallo = "no se ha podido guardar el CBZ convertido"
                parcial.delete()
                return null
            }
            fallo = null
            podar(ctx)          // no dejar que la cache crezca sin fin
            destino
        } catch (t: Throwable) {
            fallo = "${t.javaClass.simpleName} ${t.message ?: ""}"
            parcial.delete()
            null
        } finally {
            copia.delete()
        }
    }

    private fun esImagen(n: String) =
        n.substringAfterLast('.', "").lowercase() in EXT &&
        !n.substringAfterLast('/').startsWith(".") &&
        !n.contains("__MACOSX")

    /** Cuanto ocupan los convertidos, para poder decirlo en Ajustes. */
    fun tamano(ctx: Context): Long =
        carpeta(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    fun limpiar(ctx: Context) {
        carpeta(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Tira la copia de este comic, cuando ya no hace falta. */
    fun olvidar(ctx: Context, uri: String) {
        File(carpeta(ctx), clave(uri) + ".cbz").delete()
    }

    /**
     * Techo de la cache de convertidos. Medio giga.
     *
     * POR QUE HACE FALTA UN TECHO, que antes no habia ninguno:
     *
     * Lo que se guarda aqui no son miniaturas, son TOMOS ENTEROS. Un comic
     * convertido ocupa lo que ocupaba el CBR —Blackest Night, 366 MB— y hasta
     * el 26/08/2026 no se borraba nunca: se llego a 3,78 GB de cache en el
     * movil de Dani, mas que la propia biblioteca de muchos.
     *
     * La cache sigue teniendo sentido para LEER un CBR sin reconvertirlo cada
     * vez. Lo que no tiene sentido es que crezca sin fin. Al guardar uno nuevo
     * se tiran los mas viejos hasta bajar del techo: el que estas leyendo
     * ahora es el ultimo que se tocó, asi que es el ultimo en caer.
     */
    private const val TECHO_BYTES = 500L * 1024 * 1024

    /**
     * Y ademas por EDAD, no solo por tamaño.
     *
     * El techo solo actua cuando te acercas a el. Si conviertes tres tomos y no
     * los vuelves a abrir, se quedan ahi ocupando un giga para siempre porque
     * nunca llegan a los 500 MB... o si, y entonces echan fuera a otro que si
     * usabas. Dos semanas sin abrir un comic es que ese comic no esta en uso.
     */
    private const val CADUCA_DIAS = 14L

    private fun podar(ctx: Context) {
        val ficheros = carpeta(ctx).listFiles()?.filter { it.name.endsWith(".cbz") }
            ?: return

        // El tiempo entra por parametro del sistema, no se inventa: aqui no hay
        // reloj propio ni falta.
        val limite = System.currentTimeMillis() - CADUCA_DIAS * 24 * 60 * 60 * 1000
        ficheros.filter { it.lastModified() < limite }.forEach { it.delete() }

        val quedan = carpeta(ctx).listFiles()?.filter { it.name.endsWith(".cbz") } ?: return
        var total = quedan.sumOf { it.length() }
        if (total <= TECHO_BYTES) return

        // Del mas viejo al mas reciente por fecha de uso, y se va borrando
        // hasta caber. lastModified se toca al escribirlo y al leerlo.
        for (f in quedan.sortedBy { it.lastModified() }) {
            if (total <= TECHO_BYTES) break
            val ocupaba = f.length()
            if (f.delete()) total -= ocupaba
        }
    }
}
