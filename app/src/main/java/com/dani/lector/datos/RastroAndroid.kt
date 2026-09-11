package com.dani.lector.datos

/**
 * ANDROID — Apunta en el [Rastro] tambien lo que revienta la app.
 *
 * NO SE VA A `commonMain` Y NO ES UN DESCUIDO:
 * `Thread.setDefaultUncaughtExceptionHandler` es de la JVM. En iOS un fallo de
 * Kotlin/Native no pasa por ahi, y ademas el sistema mata el proceso sin dar
 * ocasion a escribir nada. Cuando toque, iOS tendra su propia pieza — no esta.
 *
 * SE ENCADENA AL MANEJADOR QUE YA HABIA, no se sustituye: el de Android es el
 * que hace que la app se cierre y salga el dialogo del sistema. Si se quita, un
 * fallo dejaria el proceso colgado y en pantalla — que es sospechosamente
 * parecido a lo que estamos buscando.
 */
object RastroAndroid {

    fun instalar() {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            Rastro.apunta("!!! PETADA en ${hilo.name}: ${error}\n" +
                error.stackTrace.take(12).joinToString("\n") { "        $it" })
            anterior?.uncaughtException(hilo, error)
        }
    }
}
