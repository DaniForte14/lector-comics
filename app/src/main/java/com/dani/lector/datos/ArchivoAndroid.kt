package com.dani.lector.datos

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap

/**
 * ANDROID — El [Archivo] de Android. Delega en [ComicZip], que es quien sabe.
 *
 * ES UN ENVOLTORIO FINO Y NO UNA MUDANZA, a proposito. `ComicZip` son 330 lineas
 * con tres caches, el respaldo de junrar y la conversion de RAR5, y cada una de
 * esas decisiones costo un cierre de la app en su dia. Meterle una interfaz por
 * dentro seria tocar todo eso para no ganar nada: lo unico que hacia falta era
 * **quitarle el `Context` a quien lo llama**, y eso se consigue guardandolo aqui.
 */
class ArchivoAndroid(private val ctx: Context) : Archivo {

    override fun paginas(uri: String): Paginas = ComicZip.paginas(ctx, uri)

    override fun pagina(
        uri: String, nombre: String, anchoMax: Int, recortar: Boolean
    ): ImageBitmap? = ComicZip.pagina(ctx, uri, nombre, anchoMax, recortar)

    override fun precargar(
        uri: String, nombres: List<String>, actual: Int, anchoMax: Int, recortar: Boolean
    ) = ComicZip.precargar(ctx, uri, nombres, actual, anchoMax, recortar)
}
