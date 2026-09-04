package com.dani.lector.datos

import platform.Foundation.NSUserDefaults

/**
 * iOS — Las [Preferencias] de iOS: `NSUserDefaults`.
 *
 * ES EL EQUIVALENTE EXACTO DE `SharedPreferences`: un almacen de pares clave y
 * valor que gestiona el sistema, se respalda con el dispositivo y no hay que
 * abrir ni cerrar.
 *
 * LA TRAMPA ESTA EN LOS VALORES POR DEFECTO, y es la razon de que las tres
 * lecturas miren `objectForKey` antes. `boolForKey` de una clave que no existe
 * devuelve **false**, y `integerForKey` devuelve **0**; no hay forma de
 * distinguir "no guardado" de "guardado en false". Leyendolos a pelo,
 * `recortar` y `autoConvertir` —que van encendidos de serie— **aparecerian
 * apagados la primera vez que se abre la app en el iPad**, y nadie sabria por
 * que. Se mira si la clave esta, y si no esta manda el valor por defecto.
 *
 * ESCRITO Y SIN COMPILAR. Desde Windows no hay Kotlin/Native para iOS; esto lo
 * ve por primera vez el runner macOS del CI.
 */
class PreferenciasIOS : Preferencias {

    private val d = NSUserDefaults.standardUserDefaults

    override fun texto(clave: String): String? = d.stringForKey(clave)

    override fun ponTexto(clave: String, valor: String?) {
        if (valor == null) d.removeObjectForKey(clave) else d.setObject(valor, clave)
    }

    override fun si(clave: String, pordefecto: Boolean) =
        if (d.objectForKey(clave) == null) pordefecto else d.boolForKey(clave)

    override fun ponSi(clave: String, valor: Boolean) { d.setBool(valor, clave) }

    override fun entero(clave: String, pordefecto: Int) =
        if (d.objectForKey(clave) == null) pordefecto else d.integerForKey(clave).toInt()

    override fun ponEntero(clave: String, valor: Int) { d.setInteger(valor.toLong(), clave) }

    override fun largo(clave: String, pordefecto: Long) =
        if (d.objectForKey(clave) == null) pordefecto else d.integerForKey(clave)

    override fun ponLargo(clave: String, valor: Long) { d.setInteger(valor, clave) }
}
