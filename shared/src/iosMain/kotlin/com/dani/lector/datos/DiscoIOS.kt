package com.dani.lector.datos

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
// Los metodos de una *category* de Objective-C se importan uno a uno: no son
// metodos de la clase, son funciones de extension. Es lo unico que fallo del CI
// de la tanda de ZipIOS, asi que aqui van los cinco.
import platform.Foundation.closeFile
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeData
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

    /**
     * SI EL FICHERO NO ESTA, `fileHandleForWritingAtPath` devuelve null —no lo
     * crea, al reves que `appendText` de Java— asi que la primera miga del
     * rastro se escribe con [escribir] y las demas se pegan al final.
     */
    override fun anadir(nombre: String, texto: String) {
        val manejador = NSFileHandle.fileHandleForWritingAtPath(ruta(nombre))
        if (manejador == null) { escribir(nombre, texto); return }
        try {
            val datos = (texto as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
            manejador.seekToEndOfFile()
            manejador.writeData(datos)
        } finally {
            manejador.closeFile()
        }
    }

    override fun borrar(nombre: String) {
        NSFileManager.defaultManager.removeItemAtPath(ruta(nombre), null)
    }
}
