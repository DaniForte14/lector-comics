package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * El indice de un ZIP, leido a mano.
 *
 * SE FABRICA UN ZIP BYTE A BYTE y se lee. Es la unica forma de probar esto sin
 * un fichero de verdad, y ademas es la buena: si el lector se equivoca de
 * desplazamiento **no da ningun error**, devuelve una pagina corrupta o un
 * nombre raro. Es exactamente la clase de fallo que este proyecto persigue.
 *
 * Y es lo unico de todo `ArchivoIOS` que se puede comprobar desde Windows: lo
 * demas —descomprimir y leer el fichero— lo dira el CI de macOS.
 */
class ZipTest {

    /** Un ZIP con entradas guardadas sin comprimir, montado a mano. */
    private class Constructor {
        val bytes = ArrayList<Byte>()
        private val entradas = ArrayList<Triple<String, Int, Int>>()  // nombre, offset, tamaño

        fun u16(v: Int) { bytes.add((v and 0xFF).toByte()); bytes.add(((v shr 8) and 0xFF).toByte()) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v ushr 16) and 0xFFFF) }
        fun txt(s: String) { s.encodeToByteArray().forEach { bytes.add(it) } }

        fun añade(nombre: String, contenido: String, extraLocal: Int = 0) {
            val offset = bytes.size
            u32(0x04034b50); u16(20); u16(0); u16(0); u16(0); u16(0)
            u32(0)                                   // crc, que aqui da igual
            u32(contenido.length); u32(contenido.length)
            u16(nombre.length); u16(extraLocal)
            txt(nombre)
            repeat(extraLocal) { bytes.add(0) }      // el extra del encabezado local
            txt(contenido)
            entradas.add(Triple(nombre, offset, contenido.length))
        }

        fun cierra(comentario: String = ""): ByteArray {
            val inicioIndice = bytes.size
            entradas.forEach { (nombre, offset, tam) ->
                u32(0x02014b50); u16(20); u16(20); u16(0); u16(0); u16(0); u16(0)
                u32(0); u32(tam); u32(tam)
                u16(nombre.length); u16(0); u16(0); u16(0); u16(0)
                u32(0); u32(offset)
                txt(nombre)
            }
            val largoIndice = bytes.size - inicioIndice
            u32(0x06054b50); u16(0); u16(0)
            u16(entradas.size); u16(entradas.size)
            u32(largoIndice); u32(inicioIndice)
            u16(comentario.length); txt(comentario)
            return bytes.toByteArray()
        }
    }

    /** Lee de un ByteArray como si fuera el fichero. */
    private fun lectorDe(b: ByteArray): (Long, Int) -> ByteArray? = { pos, cuantos ->
        if (pos < 0 || pos > b.size) null
        else b.copyOfRange(pos.toInt(), minOf(b.size, pos.toInt() + cuantos))
    }

    private fun leeIndice(b: ByteArray) = Zip.entradas(b.size.toLong(), lectorDe(b))

    @Test fun `lee los nombres y los tamaños en orden`() {
        val z = Constructor().apply {
            añade("pagina01.jpg", "AAAA")
            añade("pagina02.jpg", "BBBBBB")
        }.cierra()
        val e = leeIndice(z)!!
        assertEquals(listOf("pagina01.jpg", "pagina02.jpg"), e.map { it.nombre })
        assertEquals(listOf(4L, 6L), e.map { it.original })
    }

    // El registro final se busca hacia atras, y un comentario lo empuja lejos
    // del final del fichero. Sin buscar, no se encuentra.
    @Test fun `lo encuentra aunque haya un comentario al final`() {
        val z = Constructor().apply { añade("a.jpg", "AAAA") }
            .cierra("un comentario bastante largo puesto ahi para estorbar")
        assertEquals(listOf("a.jpg"), leeIndice(z)!!.map { it.nombre })
    }

    // ESTE ES EL CASO QUE JUSTIFICA MIRAR EL ENCABEZADO LOCAL. El campo extra
    // puede medir distinto en el indice y en el encabezado; fiandose del indice,
    // la lectura queda desplazada y la imagen sale corrupta sin dar error.
    @Test fun `los datos empiezan donde dice el encabezado local`() {
        val z = Constructor().apply { añade("a.jpg", "HOLA", extraLocal = 7) }.cierra()
        val e = leeIndice(z)!!.first()
        val donde = Zip.datosEn(e) { p, c -> lectorDe(z)(p, c) }!!
        assertEquals("HOLA", z.decodeToString(donde.toInt(), donde.toInt() + 4))
    }

    @Test fun `un archivo vacio da una lista vacia`() {
        assertEquals(emptyList(), leeIndice(Constructor().cierra()))
    }

    @Test fun `lo que no es un zip da null`() {
        assertNull(leeIndice("esto no es un zip ni de lejos, es texto".encodeToByteArray()))
        assertNull(leeIndice(ByteArray(4)))
    }

    @Test fun `guarda el metodo de compresion de cada entrada`() {
        val e = leeIndice(Constructor().apply { añade("a.jpg", "AAAA") }.cierra())!!.first()
        assertTrue(e.metodo == 0 || e.metodo == 8)
    }
}
