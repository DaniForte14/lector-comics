package com.dani.lector.datos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un punto guardado dentro de un comic. */
data class Marcador(val uri: String, val pagina: Int, val cuando: Long)

/**
 * Los marcapaginas.
 *
 * Fichero propio, como el progreso y por la misma razon: el indice de la
 * biblioteca se borra entero al reescanear y esto no puede irse con el.
 */
class Marcadores(private val ctx: Context) {

    private val fichero get() = File(ctx.filesDir, "marcadores.json")
    private var cache: MutableList<Marcador>? = null

    fun recargar() { cache = null }

    private fun cargar(): MutableList<Marcador> {
        cache?.let { return it }
        val out = mutableListOf<Marcador>()
        runCatching {
            if (fichero.exists()) {
                val a = JSONArray(fichero.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(Marcador(o.optString("uri"), o.optInt("pag"), o.optLong("cuando")))
                }
            }
        }
        cache = out
        return out
    }

    private fun guardar() {
        val m = cache ?: return
        runCatching {
            val a = JSONArray()
            m.forEach {
                a.put(JSONObject().apply {
                    put("uri", it.uri); put("pag", it.pagina); put("cuando", it.cuando)
                })
            }
            fichero.writeText(a.toString())
        }
    }

    /** Las paginas marcadas de un comic. */
    fun de(uri: String): Set<Int> =
        cargar().filter { it.uri == uri }.map { it.pagina }.toSet()

    fun todos(): List<Marcador> = cargar().sortedByDescending { it.cuando }

    /** Pone o quita. Devuelve true si ha quedado marcada. */
    fun alternar(uri: String, pagina: Int): Boolean {
        val m = cargar()
        val i = m.indexOfFirst { it.uri == uri && it.pagina == pagina }
        val puesta = if (i >= 0) { m.removeAt(i); false }
                     else { m.add(Marcador(uri, pagina, System.currentTimeMillis())); true }
        guardar()
        return puesta
    }

    // ─────────────────── COPIA DE SEGURIDAD ───────────────────

    /** Por carpeta y nombre, igual que el progreso: las uris no sobreviven. */
    fun exportar(): JSONArray {
        val a = JSONArray()
        cargar().forEach {
            a.put(JSONObject().apply {
                put("clave", Progreso.clave(it.uri))
                put("pag", it.pagina)
                put("cuando", it.cuando)
            })
        }
        return a
    }

    fun importar(a: JSONArray, urisActuales: Map<String, String>): Int {
        val m = cargar()
        var puestos = 0
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val uri = urisActuales[o.optString("clave")] ?: continue
            val pag = o.optInt("pag")
            if (m.none { it.uri == uri && it.pagina == pag }) {
                m.add(Marcador(uri, pag, o.optLong("cuando")))
                puestos++
            }
        }
        guardar()
        return puestos
    }
}
