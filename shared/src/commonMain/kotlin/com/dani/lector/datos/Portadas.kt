package com.dani.lector.datos

import androidx.compose.ui.graphics.ImageBitmap

/**
 * La cache de portadas. Lo que hace que la rejilla no abra treinta ficheros de
 * 35 MB cada vez que haces scroll.
 *
 * Detras hay dos niveles —memoria y disco— y en Android la de memoria se mide
 * con un `LruCache` y un techo sacado de `Runtime.maxMemory()`. **Eso no existe
 * fuera de la JVM**, asi que en iOS el techo sera un numero fijo y prudente: el
 * iPad Air tiene 4 GB y **iOS mata la app sin avisar** cuando se pasa de su
 * cuota, sin ningun `OutOfMemoryError` que atrapar.
 *
 * POR QUE [enMemoria] EXISTE Y NO ES UN CAPRICHO. Es la unica que **no
 * suspende**. La version suspendida salta a un hilo de IO aunque la respuesta
 * este ahi mismo, y ese salto son uno o dos fotogramas con la carta gris; al
 * volver a entrar en pantalla, lo normal es que la portada ya este puesta.
 * Quitarla parece una simplificacion y es un parpadeo en cada scroll.
 */
interface Portadas {

    /** La portada, sacandola del comic si hace falta. Suspende. */
    suspend fun obtener(uri: String): ImageBitmap?

    /** La portada SOLO si ya esta en memoria. No suspende, no toca disco. */
    fun enMemoria(uri: String): ImageBitmap?

    /** Lo que ocupan las guardadas, para poder enseñarlo en Ajustes. */
    fun tamano(): Long

    fun limpiar()
}
