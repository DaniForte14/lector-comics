package com.dani.lector

import android.app.Application
import android.content.Context
import com.dani.lector.red.ComicVine
import com.dani.lector.red.FuenteComics
import com.dani.lector.red.FuenteVacia
import com.dani.lector.datos.Rastro
import com.dani.lector.datos.Vigilante

class LectorApp : Application() {

    /**
     * El canal de notificaciones y el trabajo diario se dejan listos al
     * arrancar, aunque todavia no sigas ninguna serie.
     *
     * Crear el canal es idempotente y cuesta nada, y hacerlo aqui evita el fallo
     * clasico: pedir el permiso o notificar antes de que el canal exista, con lo
     * que la notificacion se descarta en silencio y parece que el aviso no
     * funciona. El trabajo va con KEEP, asi que reprogramarlo en cada arranque
     * no reinicia la cuenta.
     */
    override fun onCreate() {
        super.onCreate()
        // Lo PRIMERO, antes que nada: si algo revienta al arrancar, queremos
        // que quede apuntado.
        Rastro.instalar(this)
        Rastro.apunta(this, "── la app arranca ──")
        Vigilante.crearCanal(this)
        Vigilante.programar(this)
    }

    /**
     * UNICO SITIO donde se decide de donde salen los datos.
     * Las claves salen de local.properties (via BuildConfig) o de los ajustes.
     * Nunca se escriben aqui ni acaban en git.
     */
    @Volatile private var cacheFuente: FuenteComics? = null

    val fuente: FuenteComics get() = cacheFuente ?: crearFuente().also { cacheFuente = it }

    private fun prefs() = getSharedPreferences("lector", Context.MODE_PRIVATE)

    private fun crearFuente(): FuenteComics {
        val k = BuildConfig.COMICVINE_CLAVE.ifBlank {
            prefs().getString("cv_clave", "").orEmpty()
        }
        return if (k.isNotBlank()) ComicVine(k) else FuenteVacia
    }

    fun claveComicVine(): String = prefs().getString("cv_clave", "").orEmpty()

    fun guardarClave(cv: String) {
        prefs().edit().putString("cv_clave", cv).apply()
        cacheFuente = null
    }
}
