package com.dani.lector.datos

/**
 * Las reglas de nombre de la limpieza de la biblioteca: **que se considera una
 * copia, que es una extension doble y que numero entre parentesis es una grapa**.
 *
 * POR QUE ESTAN AQUI Y NO DENTRO DE `ConversorCarpeta`. Esa funcion **renombra y
 * borra ficheros del usuario**, y estas tres reglas son las que deciden sobre
 * cual. Son de las que se rompen sin dar ningun error: el fichero se renombra
 * igual, solo que no era ese. Dos veces ha estado a punto de costar caro:
 *
 *  - "Green Lantern Corps (21).cbz" **no es una copia**: el 21 es el numero de
 *    la grapa. La primera version los renombraba y se cargaba la numeracion de
 *    la serie entera. Lo cazo Dani en una captura antes de que se ejecutara.
 *  - "Batman (2016).cbz" tampoco: eso es el año.
 *
 * Aqui no se toca ningun fichero. Lo unico que sale de estas funciones son
 * nombres; quien borra y renombra es `ConversorCarpeta`, y solo despues de
 * contar las paginas de los dos ficheros.
 */
object Limpieza {

    /** "Batman 01 (1)" -> base "Batman 01" y copia numero 1. */
    private val RE_COPIA = Regex("""^(.*?)\s*\((\d+)\)$""")

    /** Lo mismo, pero para leer el numero de grapa de "Corps (14)". */
    private val RE_PARENT_FINAL = Regex("""^(.*?)\s*\((\d{1,4})\)$""")

    /** Un numero de cuatro cifras en este rango es un año, no una grapa. */
    private val FECHAS = 1930..2100

    /**
     * El nombre sin la extension que le sobra: "X.cbz.zip" -> "X.cbz". null si
     * no la tiene.
     *
     * La dejo la primera version del conversor, al crear el documento con mime
     * `application/zip`. **No es cosmetico**: rompe el numero de la grapa,
     * porque `Parser` quita la ultima extension y se queda con "...#01.cbz",
     * donde ya no encuentra el numero.
     */
    fun sinDobleExtension(nombre: String): String? =
        if (nombre.lowercase().endsWith(".cbz.zip")) nombre.dropLast(4) else null

    /**
     * Si [nombre] tiene pinta de copia, **como se llamaria su original**. null
     * si no la tiene.
     *
     * Devuelve un nombre y no un veredicto a proposito: **un "(n)" solo es una
     * copia si el original esta al lado**, y eso solo lo sabe quien tiene la
     * lista de la carpeta delante. Sin original, "Corps (21).cbz" es la grapa
     * numero 21 y no se toca.
     */
    fun originalDe(nombre: String): String? {
        val ext = nombre.substringAfterLast('.', "")
        val base = nombre.substringBeforeLast('.')
        val m = RE_COPIA.matchEntire(base) ?: return null
        return m.groupValues[1] + "." + ext
    }

    /** Un renombrado de "(n)" a "#0n". [choca] si ya hay un fichero asi. */
    data class Grapa(val viejo: String, val nuevo: String, val choca: Boolean)

    /**
     * Pasa los numeros entre parentesis a formato de grapa, mirando la carpeta
     * entera: "Green Lantern Corps (1).cbz" -> "Green Lantern Corps #01.cbz".
     *
     * **No es cosmetico**: `Parser` descarta a proposito lo que va entre
     * parentesis —para que un "(2016)" no cuele como numero—, asi que esos
     * comics se quedan SIN numero. Y sin numero no hay chapa en la portada y el
     * orden pasa a ser alfabetico: (1), (10), (11), (12)... en vez de 1, 2, 3.
     *
     * **Cuantas cifras se rellenan lo decide el mayor de la carpeta**, no un
     * numero fijo: con numeros hasta el 17 basta "#01", pero si la serie llega
     * al 120 hace falta "#001" o el orden se vuelve a romper en el 100.
     *
     * Los años quedan fuera, y los nombres que ya estan bien no salen en la
     * lista. Los que chocan con un fichero que ya existe salen marcados, para
     * que quien llama avise en vez de pisar nada.
     */
    fun aGrapa(nombres: List<String>): List<Grapa> {
        val candidatos = nombres.mapNotNull { n ->
            val ext = n.substringAfterLast('.', "")
            val base = n.substringBeforeLast('.')
            val m = RE_PARENT_FINAL.matchEntire(base) ?: return@mapNotNull null
            val num = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            if (num in FECHAS) return@mapNotNull null    // un año no es una grapa
            Triple(n, m.groupValues[1].trim(), num) to ext
        }
        if (candidatos.isEmpty()) return emptyList()

        // El mayor de ESTA carpeta decide el relleno de todos.
        val mayor = candidatos.maxOf { it.first.third }
        val cifras = if (mayor >= 100) 3 else 2

        val hay = nombres.map { it.lowercase() }.toSet()
        return candidatos.mapNotNull { (t, ext) ->
            val (viejo, base, num) = t
            val nuevo = "$base #" + num.toString().padStart(cifras, '0') +
                        if (ext.isBlank()) "" else ".$ext"
            if (nuevo.equals(viejo, ignoreCase = true)) null
            else Grapa(viejo, nuevo, nuevo.lowercase() in hay)
        }
    }
}
