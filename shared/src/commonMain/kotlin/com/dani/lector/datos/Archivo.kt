package com.dani.lector.datos

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Abrir un comic y sacarle paginas. Lo unico que cambia entre Android e iOS.
 *
 * En Android hay detras `java.util.zip` para los CBZ, junrar y un motor nativo
 * de 7-Zip para los CBR, y `BitmapFactory` para decodificar. **Nada de eso corre
 * en Kotlin/Native**: junrar es Java y 7-Zip-JBinding es JVM mas una libreria
 * nativa, asi que iOS necesita su propio motor. Lo que NO cambia es el camino
 * —detectar formato, listar paginas, decodificar una— y eso es esto.
 *
 * DEVUELVE `ImageBitmap` Y NO EL BITMAP DE CADA PLATAFORMA: es el tipo que
 * entiende Compose en las dos, y desde la tanda 15 es lo que sale ya de
 * decodificar.
 *
 * SOLO TRES METODOS, Y ES A PROPOSITO. Es lo que `VistaModelo` necesita para
 * mostrar un comic. `ComicZip` sabe ademas decir el formato de un fichero y por
 * que no ha podido con el, pero eso solo se lo preguntan el conversor y las
 * miniaturas, que son de Android y se quedan alli. **Una interfaz con lo que
 * hace falta hoy y no con todo lo que la implementacion sabe hacer**: lo otro
 * se añade el dia que alguien portable lo pida.
 */
interface Archivo {

    /** Los nombres de las paginas, en orden, o el motivo de no poder leerlo. */
    fun paginas(uri: String): Paginas

    /**
     * Una pagina decodificada a como mucho [anchoMax] de ancho, o null.
     *
     * [anchoMax] no es un capricho de quien llama: decide en que cache entra
     * —miniatura, pagina o detalle de zoom— y con que profundidad de color se
     * decodifica. Una pagina a tamaño de zoom puede pesar veinte megas.
     */
    fun pagina(
        uri: String, nombre: String, anchoMax: Int, recortar: Boolean = false
    ): ImageBitmap?

    /** Deja listas las de alrededor para que pasar pagina sea instantaneo. */
    fun precargar(
        uri: String, nombres: List<String>, actual: Int,
        anchoMax: Int, recortar: Boolean = false
    )
}
