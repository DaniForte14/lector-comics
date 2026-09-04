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

        // EL TRABAJO DIARIO NO CUESTA EL ARRANQUE, Y ESTA MEDIDO: 5 ms LOS DOS.
        //
        // Se sospecho de `WorkManager.getInstance()`, que monta una base de
        // datos Room la primera vez, porque estaba aqui —hilo principal, antes
        // de que exista la Activity— y en el rastro habia 147-250 ms sin
        // explicar entre "la app arranca" y ON_CREATE.
        //
        // **Falso.** Medido: `canal 3 ms, trabajo diario 2 ms`. Y la razon es
        // que WorkManager ya se ha inicializado ANTES de llegar aqui, en su
        // propio ContentProvider de arranque, asi que para cuando se le pide la
        // instancia el trabajo caro ya esta hecho.
        //
        // Se llego a mover a un hilo aparte y **se ha devuelto aqui**: cinco
        // milisegundos no pagan un hilo suelto ni el riesgo de programar el
        // trabajo con el proceso muriendose. El cronometro se queda, que cuesta
        // dos restas y es lo unico que impide volver a sospechar de esto.
        val t0 = System.currentTimeMillis()
        Vigilante.crearCanal(this)
        val tCanal = System.currentTimeMillis()
        Vigilante.programar(this)
        Rastro.apunta(this, "  arranque: canal ${tCanal - t0} ms, " +
            "trabajo diario ${System.currentTimeMillis() - tCanal} ms")
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
