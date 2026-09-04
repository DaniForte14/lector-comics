package com.dani.lector.datos

/** Lo que hay dentro de una carpeta concreta: subcarpetas y comics. */
data class Contenido(val carpetas: List<Carpeta>, val comics: List<Comic>)

/**
 * Recorrer la biblioteca del usuario. **La pieza mas distinta de todo el port.**
 *
 * En Android es SAF: el usuario elige una carpeta una vez, el sistema da un
 * permiso persistente sobre su arbol y a partir de ahi se consulta con un
 * `ContentResolver`. En iOS **no existe nada parecido**: hay `UIDocumentPicker`
 * y *security-scoped bookmarks*, que ademas hay que **guardar y volver a
 * resolver en cada arranque**, y entre medias abrir y cerrar el acceso a mano.
 *
 * Por eso esto va detras de una interfaz y no de un `expect/actual` con las
 * mismas firmas: no es la misma operacion escrita en dos idiomas, son dos
 * mecanismos distintos que casualmente responden a la misma pregunta —que hay
 * en esta carpeta—.
 *
 * LAS RUTAS SON CADENAS OPACAS. `raiz` y `docId` no son rutas de fichero: en
 * Android son uris de SAF y en iOS seran marcadores. **Quien llama no las
 * interpreta nunca**, solo las guarda y las devuelve; el dia que se rompa esa
 * regla, el port se rompe con ella.
 */
interface Biblioteca {

    /**
     * Abre una carpeta. [docId] null significa la raiz.
     *
     * Solo UN nivel: la navegacion por carpetas no necesita mas y asi entrar en
     * una carpeta es instantaneo aunque tengas mil comics.
     */
    suspend fun abrir(raiz: String, docId: String?, ruta: String = ""): Contenido

    /** Todos los comics de una carpeta y sus subcarpetas. Para el indice. */
    suspend fun todosBajo(raiz: String, docId: String?, ruta: String = ""): List<Comic>
}
