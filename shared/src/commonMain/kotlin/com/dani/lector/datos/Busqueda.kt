package com.dani.lector.datos

/**
 * Buscar comics por texto en toda la biblioteca.
 *
 * Se parte la consulta en palabras y se exige que estEn TODAS, en cualquier
 * orden. Buscar "batman 01" y "01 batman" tienen que dar lo mismo, y una sola
 * cadena con contains no lo hace.
 *
 * Se busca en el nombre del fichero Y en su carpeta, asi que "vol 3 annual"
 * encuentra el annual dentro de la carpeta del volumen 3 aunque el fichero no
 * diga "vol 3" por ninguna parte.
 */
object Busqueda {

    /** Tope de resultados: mas de esto no se lee, solo se hace lento. */
    private const val TOPE = 300

    fun de(comics: List<Comic>, texto: String): List<Comic> {
        val palabras = texto.trim().split(" ")
            .map { Parser.normalizar(it) }
            .filter { it.isNotBlank() }
        if (palabras.isEmpty()) return emptyList()

        return comics.asSequence()
            .filter { c ->
                val donde = Parser.normalizar(c.nombre + " " + c.carpeta)
                palabras.all { donde.contains(it) }
            }
            .take(TOPE)
            .toList()
    }
}
