package com.dani.lector.datos

/**
 * Los ajustes sueltos de la app: la carpeta elegida, los interruptores del
 * visor, el orden de la biblioteca y cuatro marcas de tiempo.
 *
 * ES LA MISMA JUGADA QUE [Disco], y por la misma razon: `VistaModelo` pedia un
 * `Context` para una cosa mas —guardar diez valores pequeños— y eso lo ataba a
 * Android entero. Con la interfaz delante, quien guarda es de cada plataforma y
 * lo que se guarda es de las dos.
 *
 * SON LOS DIEZ VALORES QUE HAY, no un almacen general. Si algun dia esto crece
 * hasta necesitar tipos raros o listas anidadas, es que el dato no era un ajuste
 * y le toca un fichero JSON de los de [Disco].
 *
 * **Cada lectura lleva su valor por defecto en el sitio de la llamada**, y no
 * hay un mapa de defectos aqui: el defecto de `recortar` es parte de por que
 * ese ajuste existe, y vive donde esta explicado.
 */
interface Preferencias {

    /** null si nunca se ha guardado. */
    fun texto(clave: String): String?

    /** Con null borra la clave, que no es lo mismo que guardar "". */
    fun ponTexto(clave: String, valor: String?)

    fun si(clave: String, pordefecto: Boolean): Boolean
    fun ponSi(clave: String, valor: Boolean)

    fun entero(clave: String, pordefecto: Int): Int
    fun ponEntero(clave: String, valor: Int)

    fun largo(clave: String, pordefecto: Long): Long
    fun ponLargo(clave: String, valor: Long)
}
