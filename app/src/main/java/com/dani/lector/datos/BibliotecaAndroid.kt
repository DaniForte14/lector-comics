package com.dani.lector.datos

import android.content.Context

/**
 * ANDROID — La [Biblioteca] de Android: SAF. Delega en [Escaner], que es quien
 * sabe de cursores, de `DocumentsContract` y de los dos cronometros que costo
 * dejar puestos.
 *
 * Envoltorio fino por lo mismo que [ArchivoAndroid]: lo unico que hacia falta
 * era **quitarle el `Context` a quien lo llama**.
 */
class BibliotecaAndroid(private val ctx: Context) : Biblioteca {

    override suspend fun abrir(raiz: String, docId: String?, ruta: String): Contenido =
        Escaner.abrir(ctx, raiz, docId, ruta)

    override suspend fun todosBajo(raiz: String, docId: String?, ruta: String): List<Comic> =
        Escaner.todosBajo(ctx, raiz, docId, ruta)
}
