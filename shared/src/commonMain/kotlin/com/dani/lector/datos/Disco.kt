package com.dani.lector.datos

/**
 * Donde guarda la app sus ficheros. Lo unico que cambia entre Android e iOS.
 *
 * POR QUE UNA INTERFAZ Y NO UN `expect class` CON UN GLOBAL. La alternativa era
 * un objeto de plataforma que supiera solo la carpeta, pero en Android eso exige
 * un `Context` guardado en una variable global e inicializado al arrancar: un
 * sitio mas donde algo puede estar a null cuando no toca.
 *
 * Y ADEMAS ESTO SE PUEDE PROBAR. Los cuatro almacenes —progreso, marcadores,
 * sesiones y series— necesitaban `Context` y por eso **ninguno tenia prueba de
 * su guardado y su lectura**, que es justo donde se pierden los datos. Con esto,
 * una implementacion de mentira en memoria basta para probarlos enteros.
 *
 * Es la misma jugada que [com.dani.lector.red.FuenteComics]: lo de fuera detras
 * de una interfaz, y una linea decide cual entra.
 */
interface Disco {
    /** El contenido del fichero, o null si no existe. Nunca lanza. */
    fun leer(nombre: String): String?

    /** Escribe el fichero entero, creandolo si hacia falta. */
    fun escribir(nombre: String, texto: String)

    fun borrar(nombre: String)
}

/**
 * Un disco de mentira, en memoria. Para las pruebas.
 *
 * Vive en el codigo de produccion y no en el de pruebas a proposito: asi lo
 * puede usar tambien commonTest de otro modulo, y sobre todo, **es la prueba de
 * que la interfaz es de verdad pequeña**. Si algun dia hace falta algo mas que
 * leer, escribir y borrar, esto deja de caber en diez lineas y salta el aviso.
 */
class DiscoEnMemoria(private val ficheros: MutableMap<String, String> = mutableMapOf()) : Disco {
    override fun leer(nombre: String): String? = ficheros[nombre]
    override fun escribir(nombre: String, texto: String) { ficheros[nombre] = texto }
    override fun borrar(nombre: String) { ficheros.remove(nombre) }
}
