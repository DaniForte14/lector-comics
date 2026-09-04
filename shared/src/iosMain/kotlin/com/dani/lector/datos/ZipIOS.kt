package com.dani.lector.datos

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.closeFile
// LOS METODOS DE UNA *CATEGORY* DE OBJECTIVE-C SE IMPORTAN UNO A UNO. En
// Kotlin/Native no son metodos de la clase: son funciones de extension sobre su
// companion, y sin el import la llamada no existe. Lo mismo que ya hace DiscoIOS
// con stringWithContentsOfFile y writeToFile. **Es lo unico que fallo del CI de
// esta tanda**, y no fue ninguna de las dos cosas que se habian marcado como
// sospechosas: zlib, inflateInit2_ y la ventana -15 pasaron a la primera.
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.posix.memcpy
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_NO_FLUSH
import platform.zlib.ZLIB_VERSION
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/**
 * iOS — Sacar los bytes de una pagina de un CBZ.
 *
 * ES LA OTRA MITAD DE [Zip]. Aquella entiende el indice del archivo y es comun
 * y esta probada; esta lee el fichero y descomprime, que es lo que necesita a
 * la plataforma. En Android lo hace `java.util.zip`, que no existe aqui.
 *
 * SIN DEPENDENCIAS NUEVAS, decidido por Dani el 04/09/2026: `zlib` viene con el
 * sistema y Kotlin/Native trae sus bindings. Este proyecto lleva desde el
 * principio sin Room y sin libreria de red —"menos dependencias, menos que se
 * rompa"— y no se rompe la racha por descomprimir.
 *
 * ESCRITO Y SIN COMPILAR. Desde Windows no hay Kotlin/Native; esto lo ve por
 * primera vez el runner macOS del CI, y lo normal es que haga falta mas de una
 * vuelta. Los dos sitios donde apostaria a que falla primero van marcados abajo.
 */
@OptIn(ExperimentalForeignApi::class)
object ZipIOS {

    /** Las paginas del archivo, o null si no se puede leer. */
    fun entradas(ruta: String): List<EntradaZip>? {
        val tam = tamano(ruta) ?: return null
        val h = NSFileHandle.fileHandleForReadingAtPath(ruta) ?: return null
        try {
            return Zip.entradas(tam) { pos, cuantos -> leer(h, pos, cuantos) }
        } finally {
            h.closeFile()
        }
    }

    /** Los bytes de una pagina, ya descomprimidos. */
    fun datos(ruta: String, e: EntradaZip): ByteArray? {
        val h = NSFileHandle.fileHandleForReadingAtPath(ruta) ?: return null
        try {
            val donde = Zip.datosEn(e) { pos, cuantos -> leer(h, pos, cuantos) } ?: return null
            val crudo = leer(h, donde, e.comprimido.toInt()) ?: return null
            // 0 = guardado tal cual. Pasa mas de lo que parece: un JPEG ya esta
            // comprimido y muchos empaquetadores no lo vuelven a comprimir.
            return if (e.metodo == 0) crudo else inflar(crudo, e.original.toInt())
        } finally {
            h.closeFile()
        }
    }

    private fun tamano(ruta: String): Long? {
        val atributos = NSFileManager.defaultManager.attributesOfItemAtPath(ruta, null)
        return (atributos?.get(NSFileSize) as? Number)?.toLong()
    }

    private fun leer(h: NSFileHandle, pos: Long, cuantos: Int): ByteArray? {
        if (cuantos <= 0) return ByteArray(0)
        h.seekToFileOffset(pos.toULong())
        val d = h.readDataOfLength(cuantos.toULong())
        return d.aBytes()
    }

    private fun NSData.aBytes(): ByteArray {
        val n = length.toInt()
        val salida = ByteArray(n)
        if (n > 0) salida.usePinned { memcpy(it.addressOf(0), bytes, length) }
        return salida
    }

    /**
     * Deflate crudo con zlib.
     *
     * **VENTANA NEGATIVA (-15), Y ES LA TRAMPA DE ESTO.** Dentro de un ZIP los
     * datos van en deflate CRUDO, sin la cabecera de dos bytes que lleva el
     * formato zlib. Con `15` a secas, zlib busca esa cabecera, no la encuentra y
     * falla en todas y cada una de las paginas.
     *
     * Y se llama a `inflateInit2_` y no a `inflateInit2`: **el segundo es una
     * macro de C y las macros no cruzan a Kotlin**, asi que hay que pasar a mano
     * la version y el tamaño de la estructura, que es lo que la macro hacia.
     * Este es el primer sitio donde apostaria a que el CI se queja.
     */
    private fun inflar(datos: ByteArray, tamanoOriginal: Int): ByteArray? = memScoped {
        if (tamanoOriginal <= 0) return@memScoped ByteArray(0)
        val salida = ByteArray(tamanoOriginal)
        val z = alloc<z_stream>()

        if (inflateInit2_(z.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().toInt()) != Z_OK)
            return@memScoped null

        try {
            datos.usePinned { entrada ->
                salida.usePinned { destino ->
                    z.next_in = entrada.addressOf(0).reinterpret()
                    z.avail_in = datos.size.toUInt()
                    z.next_out = destino.addressOf(0).reinterpret()
                    z.avail_out = tamanoOriginal.toUInt()
                    val r = inflate(z.ptr, Z_NO_FLUSH)
                    // Se pide de una vez porque el tamaño original lo dice el
                    // propio ZIP: no hay que iterar ni adivinar cuanto sale.
                    if (r != Z_STREAM_END && r != Z_OK) return@memScoped null
                }
            }
        } finally {
            inflateEnd(z.ptr)
        }
        salida
    }
}
