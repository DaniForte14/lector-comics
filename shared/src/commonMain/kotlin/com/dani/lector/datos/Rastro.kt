package com.dani.lector.datos

import kotlin.concurrent.Volatile
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Las migas de pan: que estaba haciendo la app justo antes.
 *
 * POR QUE EXISTE. El 02/09/2026 aparecio una pantalla en negro de la que no se
 * sale, y en tres intentos no se pudo averiguar de donde venia: Dani no sabe
 * decir cuando pasa exactamente y desde donde se programa no hay ni movil ni
 * logcat. Es EXACTAMENTE el caso que este proyecto ya tiene escrito como regla:
 * "si hay dos rondas seguidas de conjeturas, toca añadir un diagnostico que
 * diga el motivo exacto".
 *
 * COMO SE USA CUANDO PASA: se cierra la app del todo, se vuelve a abrir, y en
 * Ajustes > Diagnostico estan las ultimas lineas con su hora. Ahi se ve en que
 * pantalla estaba, que comic abrio y si hubo una excepcion.
 *
 * POR QUE UN FICHERO Y NO Logcat: logcat se pierde al desconectar el cable, y
 * el fallo aparece usando el movil por ahi, no enchufado al PC.
 *
 * Escribir una linea en un fichero cuesta microsegundos, asi que se hace en el
 * hilo que sea: meter una corrutina por miga perderia justo las ultimas, que
 * son las que importan, si el proceso muere.
 *
 * SIGUE SIENDO UN OBJETO GLOBAL, Y CON UN [Disco] DENTRO. La alternativa era
 * pasarlo a instancia, que es lo que se hizo con `Disco`, `Archivo` y los
 * cuatro almacenes; aqui se descarto a proposito y conviene saber por que:
 * sus llamadas estan repartidas por nueve ficheros —incluidas las cuatro
 * pantallas— asi que la instancia obliga a cambiar sus firmas justo en la
 * tanda en que ademas se muda la interfaz a Compose Multiplatform. Dos cambios
 * grandes cruzados en los mismos ficheros. El global con el disco inyectado es
 * un diff acotado y deja pasar la mudanza por delante.
 *
 * Lo que cuesta, dicho en claro: hay un `disco` que puede estar a null si a
 * alguien se le olvida [arranca] — y por eso, sin el, [apunta] no revienta, se
 * calla. Un rastro perdido es un diagnostico peor; una app que se cierra por
 * apuntar una miga es un fallo de verdad.
 */
object Rastro {

    /** Se queda con las ultimas. Un rastro infinito no lo lee nadie. */
    private const val LINEAS = 300

    private const val FICHERO = "rastro.txt"

    /**
     * Se escribe una vez al arrancar y se lee desde cualquier hilo (el visor,
     * el oyente de fotogramas, las corrutinas del indice). @Volatile para que
     * todos vean el disco de verdad y no un null viejo en cache.
     */
    @Volatile private var disco: Disco? = null

    /**
     * Cuanto ocupa el rastro, contado en memoria en vez de preguntandoselo al
     * disco en cada miga: [Disco] sabe leer, escribir, añadir y borrar, y
     * preguntar el tamaño seria leerlo entero, que es justo lo que la poda de
     * abajo evita.
     */
    @Volatile private var bytes = 0L

    /**
     * Se llama UNA vez, lo primero del arranque. Lee el rastro que ya hubiera
     * —una sola vez por proceso, y son 36 KB como mucho— para saber por donde
     * va y poder podar cuando toque.
     */
    fun arranca(disco: Disco) {
        Rastro.disco = disco
        bytes = runCatching { disco.leer(FICHERO)?.length?.toLong() }.getOrNull() ?: 0L
    }

    fun apunta(que: String) {
        val d = disco ?: return
        runCatching {
            val linea = "${ahora()}  $que\n"
            d.anadir(FICHERO, linea)
            bytes += linea.length
            // Se poda de vez en cuando y no en cada linea: leer y reescribir el
            // fichero entero por cada miga seria mas caro que lo que se apunta.
            if (bytes > LINEAS * 120L) {
                val podado = poda(d.leer(FICHERO).orEmpty())
                d.escribir(FICHERO, podado)
                bytes = podado.length.toLong()
            }
        }
    }

    fun leer(): String =
        runCatching { disco?.leer(FICHERO) }.getOrNull().orEmpty()
            .ifBlank { "Sin rastro todavía." }

    fun limpiar() {
        runCatching { disco?.borrar(FICHERO) }
        bytes = 0L
    }

    /**
     * Se queda con las ultimas [lineas] y siempre acaba en salto de linea, para
     * que la siguiente miga no se pegue a la ultima.
     *
     * Es pura y esta aparte porque tiene dos bordes que se rompen sin dar
     * ningun error: un rastro mas corto que el tope (no se recorta nada) y uno
     * vacio (que no puede devolver un salto de linea suelto).
     */
    fun poda(texto: String, lineas: Int = LINEAS): String {
        val utiles = texto.trimEnd('\n')
        if (utiles.isEmpty()) return ""
        return utiles.lines().takeLast(lineas).joinToString("\n") + "\n"
    }

    /**
     * `dd/MM HH:mm:ss.SSS`, el mismo formato de siempre.
     *
     * A mano y no con un formateador: `SimpleDateFormat` es de la JVM y el de
     * kotlinx-datetime seria una construccion mas por miga. Son cinco
     * `padStart`.
     *
     * En la hora del MOVIL y no en la de España (`Novedades.ZONA`), al reves
     * que la racha y el calendario: aqui la pregunta es "que hora era cuando
     * paso", y quien la contesta mirando el rastro es Dani con el movil en la
     * mano.
     */
    private fun ahora(): String {
        val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val ms = t.nanosecond / 1_000_000
        return "${dd(t.dayOfMonth)}/${dd(t.monthNumber)} " +
            "${dd(t.hour)}:${dd(t.minute)}:${dd(t.second)}.${ms.toString().padStart(3, '0')}"
    }

    private fun dd(n: Int) = n.toString().padStart(2, '0')
}
