package com.dani.lector.datos

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 */
object Rastro {

    /** Se queda con las ultimas. Un rastro infinito no lo lee nadie. */
    private const val LINEAS = 300

    private val RELOJ = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale.US)

    private fun fichero(ctx: Context) = File(ctx.filesDir, "rastro.txt")

    fun apunta(ctx: Context, que: String) {
        runCatching {
            val f = fichero(ctx)
            f.appendText("${RELOJ.format(Date())}  $que\n")
            // Se poda de vez en cuando y no en cada linea: leer y reescribir el
            // fichero entero por cada miga seria mas caro que lo que se apunta.
            if (f.length() > LINEAS * 120L) {
                val ultimas = f.readLines().takeLast(LINEAS)
                f.writeText(ultimas.joinToString("\n") + "\n")
            }
        }
    }

    fun leer(ctx: Context): String =
        runCatching { fichero(ctx).readText() }.getOrDefault("").ifBlank { "Sin rastro todavía." }

    fun limpiar(ctx: Context) { runCatching { fichero(ctx).delete() } }

    /**
     * Apunta tambien lo que revienta la app.
     *
     * SE ENCADENA AL MANEJADOR QUE YA HABIA, no se sustituye: el de Android es
     * el que hace que la app se cierre y salga el dialogo del sistema. Si se
     * quita, un fallo dejaria el proceso colgado y en pantalla — que es
     * sospechosamente parecido a lo que estamos buscando.
     */
    fun instalar(ctx: Context) {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            apunta(ctx, "!!! PETADA en ${hilo.name}: ${error}\n" +
                error.stackTrace.take(12).joinToString("\n") { "        $it" })
            anterior?.uncaughtException(hilo, error)
        }
    }
}
