package com.dani.lector.datos

/** Una entrada del archivo: donde esta y cuanto ocupa. */
data class EntradaZip(
    val nombre: String,
    /** 0 = guardado tal cual, 8 = deflate. Son los dos unicos que usan los CBZ. */
    val metodo: Int,
    val comprimido: Long,
    val original: Long,
    /** Donde empieza su encabezado local, no sus datos. Ver [datosEn]. */
    val encabezado: Long
)

/**
 * El indice de un fichero ZIP, leido a mano.
 *
 * POR QUE EXISTE. `java.util.zip` es de la JVM y en el iPad no hay. Leer un CBZ
 * son dos cosas: **entender donde esta cada pagina dentro del fichero** —esto,
 * que es aritmetica de bytes y no sabe de ninguna plataforma— y
 * **descomprimirla**, que si necesita a la plataforma. Se parten a proposito:
 * asi la mitad dificil de equivocarse sin enterarse se puede probar desde
 * Windows, y solo la otra espera al CI de macOS.
 *
 * NO SE LEE EL FICHERO ENTERO EN MEMORIA, y no es un detalle: un CBZ son
 * decenas de megas y este proyecto ya cerro la app dos veces por cargar un
 * archivo completo. Los bytes entran por [leer], que da un trozo desde una
 * posicion — igual que [Recorte] recibe filas de pixeles en vez de la imagen.
 *
 * QUE NO CUBRE, dicho aqui para que nadie lo descubra con un CBZ raro delante:
 *
 *  - **ZIP64**. Hace falta a partir de 4 GB o de 65.535 entradas. Un tomo son
 *    ~50 MB y ~50 paginas, asi que no toca ni de lejos; si algun dia toca, se
 *    ve porque las cifras salen a 0xFFFFFFFF.
 *  - **Nombres que no sean UTF-8.** El ZIP viejo usaba CP437. Se decodifica como
 *    UTF-8 siempre: los CBZ modernos lo son, y un nombre mal decodificado
 *    **no pierde la pagina**, solo la ordena raro.
 *  - **Cifrado.** Un CBZ con contraseña no es un caso de esta app.
 */
object Zip {

    private const val FIRMA_FINAL = 0x06054b50
    private const val FIRMA_INDICE = 0x02014b50
    private const val FIRMA_LOCAL = 0x04034b50

    /** El comentario final puede ocupar hasta 64 KB, y el registro son 22 bytes. */
    private const val COLA = 66_000

    /**
     * Las entradas del archivo, o null si esto no es un ZIP.
     *
     * [leer] devuelve [cuantos] bytes desde [posicion], o null si no puede.
     */
    fun entradas(tamano: Long, leer: (posicion: Long, cuantos: Int) -> ByteArray?):
        List<EntradaZip>? {

        // EL REGISTRO FINAL SE BUSCA HACIA ATRAS, y no hay otra forma: el ZIP se
        // escribio de delante hacia atras pero solo se puede LEER al reves,
        // porque lo unico que dice donde empieza el indice esta al final.
        val cola = minOf(tamano, COLA.toLong()).toInt()
        if (cola < 22) return null
        val fin = leer(tamano - cola, cola) ?: return null

        var f = -1
        for (i in fin.size - 22 downTo 0) {
            if (u32(fin, i) == FIRMA_FINAL.toLong()) { f = i; break }
        }
        if (f < 0) return null

        val cuantas = u16(fin, f + 10)
        val donde = u32(fin, f + 16)
        if (cuantas == 0) return emptyList()

        // El indice entero de golpe: son unas decenas de bytes por entrada, o
        // sea unos kilobytes en un tomo. Eso si cabe en memoria.
        val largo = u32(fin, f + 12).toInt()
        val indice = leer(donde, largo) ?: return null

        val salida = ArrayList<EntradaZip>(cuantas)
        var p = 0
        repeat(cuantas) {
            if (p + 46 > indice.size || u32(indice, p) != FIRMA_INDICE.toLong()) return salida
            val nombreLargo = u16(indice, p + 28)
            val extraLargo = u16(indice, p + 30)
            val comentario = u16(indice, p + 32)
            val desde = p + 46
            if (desde + nombreLargo > indice.size) return salida
            salida.add(EntradaZip(
                nombre = indice.decodeToString(desde, desde + nombreLargo),
                metodo = u16(indice, p + 10),
                comprimido = u32(indice, p + 20),
                original = u32(indice, p + 24),
                encabezado = u32(indice, p + 42)
            ))
            p = desde + nombreLargo + extraLargo + comentario
        }
        return salida
    }

    /**
     * Donde empiezan los DATOS de una entrada, o null.
     *
     * Hay que mirarlo en el encabezado local y no fiarse del indice: **el
     * campo de datos extra puede medir distinto en los dos sitios**, y sumar el
     * del indice deja la lectura desplazada unos bytes. Eso no da ningun error:
     * la imagen simplemente sale corrupta.
     */
    fun datosEn(e: EntradaZip, leer: (posicion: Long, cuantos: Int) -> ByteArray?): Long? {
        val cab = leer(e.encabezado, 30) ?: return null
        if (cab.size < 30 || u32(cab, 0) != FIRMA_LOCAL.toLong()) return null
        return e.encabezado + 30 + u16(cab, 26) + u16(cab, 28)
    }

    // Little-endian, que es como el ZIP guarda todos sus numeros.
    private fun u16(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long =
        (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
        ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)
}
