package com.dani.lector.datos

/**
 * Que entradas de un archivo son paginas de comic, y en que orden se leen.
 *
 * Vive aqui y no dentro de quien lee el ZIP porque **es una decision y no
 * fontaneria**: ni el nombre de una entrada ni su orden dependen de Android.
 *
 * Y sobre todo, porque estaba **duplicada letra por letra** en `ComicZip` y en
 * `Rar5`. El 03/09/2026 se unifico la lista de extensiones y se dejo la regla
 * copiada en los dos sitios, que es exactamente el fallo contra el que avisaba
 * el comentario de la lista: dos copias que hay que acordarse de cambiar a la
 * vez acaban no cambiandose a la vez.
 */
object Imagenes {

    /** Que se considera una pagina. */
    val EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    /**
     * Si una entrada del archivo es una pagina.
     *
     * Las tres condiciones estan por algo concreto:
     *
     *  - La extension, en minusculas: hay CBZ con `.JPG`.
     *  - **Los ficheros ocultos fuera**: un `.DS_Store` no tiene extension de
     *    imagen, pero un `._portada.jpg` de macOS SI, y es un fichero de
     *    metadatos de dos kilobytes que saldria como primera pagina del comic.
     *  - **`__MACOSX` fuera**: esa carpeta la mete el compresor de macOS con una
     *    copia sombra de cada imagen, asi que sin esto **cada pagina saldria dos
     *    veces**.
     */
    fun es(nombre: String) =
        nombre.substringAfterLast('.', "").lowercase() in EXT &&
        !nombre.substringAfterLast('/').startsWith(".") &&
        !nombre.contains("__MACOSX")

    /**
     * El orden en que se leen las paginas.
     *
     * **Primero por profundidad y luego por nombre**, y ese orden importa: hay
     * CBZ que traen las paginas sueltas en la raiz y ademas una subcarpeta de
     * extras o de portadas alternativas. Ordenando solo por nombre, un
     * `extras/aaa.jpg` se colaria delante de `pagina01.jpg` y abririas el comic
     * por los extras.
     *
     * El nombre se compara en minusculas porque un CBZ mezcla `Page01.jpg` con
     * `page02.jpg` y en orden de bytes las mayusculas van todas antes.
     */
    fun ordenadas(nombres: List<String>): List<String> =
        nombres.sortedWith(compareBy({ it.count { c -> c == '/' } }, { it.lowercase() }))
}
