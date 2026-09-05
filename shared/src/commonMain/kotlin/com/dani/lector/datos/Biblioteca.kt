package com.dani.lector.datos

/** Lo que hay dentro de una carpeta concreta: subcarpetas y comics. */
data class Contenido(val carpetas: List<Carpeta>, val comics: List<Comic>)

/**
 * La carpeta que ninguna [Biblioteca] debe enseñar, la lea quien la lea.
 *
 * La crea `ConversorCarpeta` al pasar un CBR a CBZ, y guarda dentro el fichero
 * viejo. Si se escaneara, **verias cada comic dos veces** —el CBZ nuevo y el CBR
 * guardado— y la siguiente conversion volveria a convertir los originales. Es la
 * papelera de la app, no biblioteca.
 *
 * VIVE AQUI Y NO EN `ConversorCarpeta` PORQUE LA REGLA ES DE QUIEN LEE, NO DE
 * QUIEN CONVIERTE. El conversor es de Android —en el iPad no hay motor de RAR—
 * pero la carpeta viaja con los ficheros: si Dani copia la biblioteca al iPad,
 * `_cbr_originales` va dentro. Dejando la cadena alli, iOS tendria que
 * repetirla, y **dos copias que hay que acordarse de cambiar a la vez acaban no
 * cambiandose a la vez**: es exactamente el fallo por el que existe [Imagenes].
 */
const val CARPETA_ORIGINALES = "_cbr_originales"

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
