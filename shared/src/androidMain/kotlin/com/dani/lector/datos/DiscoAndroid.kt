package com.dani.lector.datos

import android.content.Context
import java.io.File

/**
 * ANDROID — El [Disco] de Android: la carpeta privada de la app (`filesDir`).
 *
 * Es la misma carpeta de siempre, asi que **los ficheros que ya tiene Dani en el
 * movil se siguen leyendo igual**: esto no migra nada ni cambia de sitio nada.
 */
class DiscoAndroid(private val ctx: Context) : Disco {

    private fun f(nombre: String) = File(ctx.filesDir, nombre)

    // runCatching y no try/catch a secas: un fichero corrupto o un permiso raro
    // no puede tirar la app al arrancar. Sin fichero es sin datos, no un error.
    override fun leer(nombre: String): String? =
        runCatching { f(nombre).takeIf { it.exists() }?.readText() }.getOrNull()

    override fun escribir(nombre: String, texto: String) {
        runCatching { f(nombre).writeText(texto) }
    }

    override fun borrar(nombre: String) { runCatching { f(nombre).delete() } }
}
