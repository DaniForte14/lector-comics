package com.dani.lector.datos

import android.content.Context

/**
 * ANDROID — Las [Preferencias] de Android: las `SharedPreferences` de siempre.
 *
 * **EL MISMO FICHERO Y LAS MISMAS CLAVES QUE HASTA AHORA** (`"lector"`), asi que
 * lo que Dani ya tiene guardado en el movil se sigue leyendo igual: la carpeta
 * elegida, sus interruptores del visor y su orden de la biblioteca. Esto no
 * migra nada ni cambia nada de sitio; solo pone una interfaz por delante.
 *
 * CON EL `Editor` A PELO Y NO CON EL `edit { }` DE KTX: ese azucar viene de
 * `androidx.core:core-ktx`, que es dependencia de `:app` y no de `:shared`, y no
 * merece una dependencia nueva en el modulo comun. `apply()` es justo lo que
 * hace el de ktx por dentro: escribe en memoria ya y al disco en segundo plano.
 *
 * POR LAZY Y NO POR `get()`: con `get()`, cada uno de los ~20 accesos llamaba a
 * `getSharedPreferences`, y alguno —el orden— se lee dentro de la lista de la
 * biblioteca, o sea por recomposicion. Esa linea ya costo una pasada de
 * rendimiento; se queda como estaba.
 */
class PreferenciasAndroid(ctx: Context) : Preferencias {

    private val p by lazy { ctx.getSharedPreferences("lector", Context.MODE_PRIVATE) }

    override fun texto(clave: String): String? = p.getString(clave, null)

    override fun ponTexto(clave: String, valor: String?) {
        val e = p.edit()
        if (valor == null) e.remove(clave) else e.putString(clave, valor)
        e.apply()
    }

    override fun si(clave: String, pordefecto: Boolean) = p.getBoolean(clave, pordefecto)
    override fun ponSi(clave: String, valor: Boolean) {
        p.edit().putBoolean(clave, valor).apply()
    }

    override fun entero(clave: String, pordefecto: Int) = p.getInt(clave, pordefecto)
    override fun ponEntero(clave: String, valor: Int) {
        p.edit().putInt(clave, valor).apply()
    }

    override fun largo(clave: String, pordefecto: Long) = p.getLong(clave, pordefecto)
    override fun ponLargo(clave: String, valor: Long) {
        p.edit().putLong(clave, valor).apply()
    }
}
