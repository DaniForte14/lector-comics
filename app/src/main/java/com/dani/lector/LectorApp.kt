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

        // EL TRABAJO DIARIO NO PUEDE COSTAR EL ARRANQUE, y hasta hoy lo costaba.
        //
        // `WorkManager.getInstance()` monta una base de datos Room la primera
        // vez que se le llama, y esto estaba aqui a pelo: en `onCreate` de la
        // Application, o sea **en el hilo principal y antes de que exista
        // siquiera la Activity**. Todo lo que tarde se lo come el arranque
        // entero, y en el rastro se veian 147-250 ms entre "la app arranca" y
        // ON_CREATE que no los explicaba nada mas.
        //
        // Nada de esto tiene que estar hecho para pintar la primera pantalla:
        // el canal solo hace falta cuando se notifica, y el trabajo es DIARIO.
        // Va con KEEP, asi que si el proceso muere antes de programarlo, se
        // programa en el siguiente arranque y no se pierde nada.
        //
        // Un Thread suelto y no una corrutina: la Application no tiene ningun
        // ambito propio, y montar uno para dos llamadas seria mas aparato que
        // arreglo. Las dos son seguras fuera del hilo principal.
        //
        // Se cronometra porque **esto es la hipotesis, no la conclusion**: el
        // numero del rastro es el que dice si esa ventana era esto.
        Thread {
            val t0 = System.currentTimeMillis()
            Vigilante.crearCanal(this)
            val tCanal = System.currentTimeMillis()
            Vigilante.programar(this)
            Rastro.apunta(this, "  arranque: canal ${tCanal - t0} ms, " +
                "trabajo diario ${System.currentTimeMillis() - tCanal} ms")
        }.start()
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
