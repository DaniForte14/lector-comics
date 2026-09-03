package com.dani.lector.datos

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * iOS — El [Disco] de iOS: la carpeta Documents de la app.
 *
 * ES EL EQUIVALENTE DE `filesDir`: privada de la app, se respalda con el
 * dispositivo y sobrevive a las actualizaciones. La otra candidata era Caches,
 * y **el sistema la vacia cuando le hace falta espacio**: perder por donde ibas
 * leyendo porque el iPad andaba justo seria un fallo imposible de reproducir.
 *
 * ESCRITO Y SIN COMPILAR. Desde Windows no hay Kotlin/Native para iOS; esto lo
 * ve por primera vez el runner macOS del CI.
 */
// Las llamadas de Foundation llevan un puntero a NSError como ultimo parametro,
// y eso es API foranea: sin este opt-in, Kotlin 2.0 lo rechaza. Aqui se pasa
// null en los tres sitios porque el error no aporta nada — leer un fichero que
// no esta devuelve null igual, y es lo que la interfaz promete.
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class DiscoIOS : Disco {

    private val carpeta: String by lazy {
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).first() as String
    }

    private fun ruta(nombre: String) = "$carpeta/$nombre"

    override fun leer(nombre: String): String? =
        NSString.stringWithContentsOfFile(ruta(nombre), NSUTF8StringEncoding, null)

    override fun escribir(nombre: String, texto: String) {
        (texto as NSString).writeToFile(ruta(nombre), true, NSUTF8StringEncoding, null)
    }

    override fun borrar(nombre: String) {
        NSFileManager.defaultManager.removeItemAtPath(ruta(nombre), null)
    }
}
