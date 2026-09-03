package com.dani.lector.datos

import com.dani.lector.red.getJSONObject
import com.dani.lector.red.jsonLaxo
import com.dani.lector.red.length
import com.dani.lector.red.optInt
import com.dani.lector.red.optJSONObject
import com.dani.lector.red.optLong
import com.dani.lector.red.optString
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Un punto guardado dentro de un comic. */
data class Marcador(val uri: String, val pagina: Int, val cuando: Long)

/**
 * Los marcapaginas.
 *
 * Fichero propio, como el progreso y por la misma razon: el indice de la
 * biblioteca se borra entero al reescanear y esto no puede irse con el.
 */
class Marcadores(private val disco: Disco) {

    private val fichero = "marcadores.json"
    private var cache: MutableList<Marcador>? = null

    fun recargar() { cache = null }

    private fun cargar(): MutableList<Marcador> {
        cache?.let { return it }
        val out = mutableListOf<Marcador>()
        runCatching {
            disco.leer(fichero)?.let { texto ->
                val a = jsonLaxo.parseToJsonElement(texto) as? JsonArray
                for (i in 0 until (a?.length() ?: 0)) {
                    val o = a!!.getJSONObject(i)
                    out.add(Marcador(o.optString("uri"), o.optInt("pag"), o.optLong("cuando")))
                }
            }
        }
        // Si otro hilo termino de cargar mientras este parseaba, manda el suyo:
        // lo que ya este en la cache puede llevar cambios hechos entretanto, y
        // pisarlo con esta copia recien leida del disco los perderia.
        return cache ?: out.also { cache = it }
    }

    private fun guardar() {
        val m = cache ?: return
        runCatching {
            val a = buildJsonArray {
                m.forEach {
                    add(buildJsonObject {
                        put("uri", it.uri); put("pag", it.pagina); put("cuando", it.cuando)
                    })
                }
            }
            disco.escribir(fichero, a.toString())
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
                     else { m.add(Marcador(uri, pagina, Clock.System.now().toEpochMilliseconds())); true }
        guardar()
        return puesta
    }

    // ─────────────────── COPIA DE SEGURIDAD ───────────────────

    /** Por carpeta y nombre, igual que el progreso: las uris no sobreviven. */
    fun exportar(): JsonArray = buildJsonArray {
        cargar().forEach {
            add(buildJsonObject {
                put("clave", Progreso.clave(it.uri))
                put("pag", it.pagina)
                put("cuando", it.cuando)
            })
        }
    }

    fun importar(a: JsonArray, urisActuales: Map<String, String>): Int {
        val m = cargar()
        var puestos = 0
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
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
