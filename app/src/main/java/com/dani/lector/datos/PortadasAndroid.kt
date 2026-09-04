package com.dani.lector.datos

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap

/**
 * ANDROID — Las [Portadas] de Android. Delega en [Miniaturas].
 *
 * Envoltorio fino, como [ArchivoAndroid] y [BibliotecaAndroid]: lo unico que
 * hacia falta era quitarle el `Context` a quien lo llama. Lo de dentro
 * —`LruCache` medido por tamaño, la poda del disco a 150 MB, el cronometro de
 * portadas y el `Throwable` que impide que un fichero raro tumbe la app— se
 * queda donde esta, que es donde se gano.
 */
class PortadasAndroid(private val ctx: Context) : Portadas {

    override suspend fun obtener(uri: String): ImageBitmap? = Miniaturas.obtener(ctx, uri)

    override fun enMemoria(uri: String): ImageBitmap? = Miniaturas.enMemoria(uri)

    override fun tamano(): Long = Miniaturas.tamano(ctx)

    override fun limpiar() = Miniaturas.limpiar(ctx)
}
