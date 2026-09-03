package com.dani.lector.datos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda los TODO y lo que llevas marcado.
 *
 * Dos ficheros separados a proposito: las listas se pueden regenerar pidiendolas
 * otra vez, pero lo que has leido no se recupera de ningun sitio.
 */
class Listas(private val ctx: Context) {

    private val ficheroListas get() = File(ctx.filesDir, "listas.json")
    private val ficheroMarcas get() = File(ctx.filesDir, "marcado.json")

    // ─────────────────────────── LISTAS ───────────────────────────

    private var cache: MutableList<Lista>? = null

    /** Tira la copia en memoria. Hay que llamarla si otro escribe el fichero. */
    fun recargar() { cache = null; marcas = null }

    fun todas(): List<Lista> {
        cache?.let { return it }
        val out = mutableListOf<Lista>()
        runCatching {
            if (ficheroListas.exists()) {
                val arr = JSONArray(ficheroListas.readText())
                for (i in 0 until arr.length()) out.add(desdeJson(arr.getJSONObject(i)))
            }
        }
        cache = out
        return out
    }

    fun de(personaje: String): Lista? =
        todas().firstOrNull { Parser.normalizar(it.personaje) == Parser.normalizar(personaje) }

    fun guardar(lista: Lista) {
        val otras = todas().filterNot {
            Parser.normalizar(it.personaje) == Parser.normalizar(lista.personaje)
        }
        escribir(otras + lista)
    }

    fun borrar(personaje: String) {
        escribir(todas().filterNot {
            Parser.normalizar(it.personaje) == Parser.normalizar(personaje)
        })
    }

    /**
     * Añade una serie a mano a la lista de un personaje.
     * Devuelve false si ya estaba: no se duplica ni se pisa lo que hubiera.
     */
    fun anadirSerie(personaje: String, serie: Serie): Boolean {
        val l = de(personaje) ?: return false
        if (l.series.any { it.id == serie.id }) return false
        guardar(l.copy(series = l.series + serie))
        return true
    }

    /** Cambia una serie ya guardada: sirve para rellenar el criterio despues. */
    fun actualizarSerie(personaje: String, serieId: String, cambio: (Serie) -> Serie) {
        val l = de(personaje) ?: return
        guardar(l.copy(series = l.series.map { if (it.id == serieId) cambio(it) else it }))
    }

    /**
     * Recoloca la lista por peso y año.
     *
     * Hace falta porque el orden se fija al crear la lista, y añadir algo a
     * mano puede cambiar los pesos: si la serie nueva se lleva el "empieza
     * aqui", tiene que subir arriba y no quedarse donde cayo.
     */
    fun reordenar(personaje: String) {
        val l = de(personaje) ?: return
        guardar(l.copy(series = l.series.sortedWith(
            compareBy({ GeneradorLista.ordenPeso(it.peso) }, { it.anio ?: 9999 })
        )))
    }

    /** Quita de golpe todas las series de una etapa. Devuelve cuantas se han ido. */
    fun quitarPorEra(personaje: String, era: String): Int {
        val l = de(personaje) ?: return 0
        val quedan = l.series.filterNot { it.era == era }
        val fuera = l.series.size - quedan.size
        if (fuera > 0) guardar(l.copy(series = quedan))
        return fuera
    }

    /** Quita una serie de la lista. */
    fun quitarSerie(personaje: String, serieId: String) {
        val l = de(personaje) ?: return
        guardar(l.copy(series = l.series.filterNot { it.id == serieId }))
    }

    /** Vincula una carpeta a una serie: habilita el marcado automatico. */
    fun vincular(personaje: String, serieId: String, carpeta: String?) {
        val l = de(personaje) ?: return
        guardar(l.copy(series = l.series.map {
            if (it.id == serieId) it.copy(carpeta = carpeta) else it
        }))
    }

    private fun escribir(listas: List<Lista>) {
        cache = listas.toMutableList()
        runCatching {
            val arr = JSONArray()
            listas.forEach { l ->
                arr.put(JSONObject().apply {
                    put("personaje", l.personaje)
                    put("editorial", l.editorial ?: "")
                    put("creada", l.creada)
                    put("series", JSONArray().apply {
                        l.series.forEach { s ->
                            put(JSONObject().apply {
                                put("id", s.id); put("nombre", s.nombre)
                                put("anio", s.anio ?: 0); put("anioFin", s.anioFin ?: 0)
                                put("numeros", s.numeros); put("peso", s.peso)
                                put("contexto", s.contexto); put("carpeta", s.carpeta ?: "")
                                put("editorial", s.editorial ?: "")
                                put("era", s.era ?: "")
                                put("volumen", s.volumen ?: 0)
                                put("noEncontrada", s.noEncontrada)
                            })
                        }
                    })
                })
            }
            ficheroListas.writeText(arr.toString())
        }
    }

    private fun desdeJson(o: JSONObject): Lista {
        val ar = o.optJSONArray("series")
        val series = (0 until (ar?.length() ?: 0)).map { i ->
            val s = ar!!.getJSONObject(i)
            Serie(
                id = s.getString("id"),
                nombre = s.getString("nombre"),
                anio = s.optInt("anio").takeIf { it > 0 },
                anioFin = s.optInt("anioFin").takeIf { it > 0 },
                numeros = s.optInt("numeros"),
                peso = s.optString("peso", "OPCIONAL"),
                contexto = s.optString("contexto"),
                carpeta = s.optString("carpeta").ifBlank { null },
                editorial = s.optString("editorial").ifBlank { null },
                era = s.optString("era").ifBlank { null },
                volumen = s.optInt("volumen").takeIf { it > 0 },
                // Por defecto false: las listas guardadas antes de esto no
                // llevan el campo y tienen que seguir cargando igual.
                noEncontrada = s.optBoolean("noEncontrada", false)
            )
        }
        return Lista(
            personaje = o.getString("personaje"),
            editorial = o.optString("editorial").ifBlank { null },
            series = series,
            creada = o.optLong("creada")
        )
    }

    // ─────────────────────────── MARCADO ───────────────────────────

    /** clave "serieId:numero" -> cuando lo marcaste */
    private var marcas: MutableMap<String, Long>? = null

    private fun cargarMarcas(): MutableMap<String, Long> {
        marcas?.let { return it }
        val m = mutableMapOf<String, Long>()
        runCatching {
            if (ficheroMarcas.exists()) {
                val o = JSONObject(ficheroMarcas.readText())
                o.keys().forEach { m[it] = o.getLong(it) }
            }
        }
        marcas = m
        return m
    }

    private fun guardarMarcas() {
        val m = marcas ?: return
        runCatching {
            val o = JSONObject()
            m.forEach { (k, v) -> o.put(k, v) }
            ficheroMarcas.writeText(o.toString())
        }
    }

    private fun clave(serieId: String, numero: Int) = "$serieId:$numero"

    fun leido(serieId: String, numero: Int) = cargarMarcas().containsKey(clave(serieId, numero))

    fun marcar(serieId: String, numero: Int, leido: Boolean) {
        val m = cargarMarcas()
        if (leido) m[clave(serieId, numero)] = System.currentTimeMillis()
        else m.remove(clave(serieId, numero))
        guardarMarcas()
    }

    fun marcarSerie(serie: Serie, leido: Boolean) {
        val m = cargarMarcas()
        (1..serie.numeros).forEach { n ->
            if (leido) m[clave(serie.id, n)] = System.currentTimeMillis()
            else m.remove(clave(serie.id, n))
        }
        guardarMarcas()
    }

    fun leidosDe(serie: Serie): Int =
        cargarMarcas().keys.count { it.startsWith("${serie.id}:") }

    // ─────────────────── COPIA DE SEGURIDAD ───────────────────

    /** Las listas tal cual, mas lo que llevas marcado de cada serie. */
    fun exportar(): Pair<JSONArray, JSONObject> {
        val listas = runCatching {
            if (ficheroListas.exists()) JSONArray(ficheroListas.readText()) else JSONArray()
        }.getOrDefault(JSONArray())
        val marcado = JSONObject()
        cargarMarcas().forEach { (k, v) -> marcado.put(k, v) }
        return listas to marcado
    }

    /**
     * Mete una copia sin pisar lo que ya tienes.
     *
     * Las listas que ya existen NO se sobrescriben: si has estado tocando la de
     * Batman, una copia de hace un mes no puede borrarte el trabajo. Solo se
     * añaden las que faltan. El marcado si se suma entero, porque marcar de mas
     * es recuperable y desmarcar no.
     */
    fun importar(listasCopia: JSONArray, marcadoCopia: JSONObject): Pair<Int, Int> {
        val mias = todas()
        val nuevas = mutableListOf<Lista>()
        for (i in 0 until listasCopia.length()) {
            val l = runCatching { desdeJson(listasCopia.getJSONObject(i)) }.getOrNull() ?: continue
            if (mias.none { Parser.normalizar(it.personaje) == Parser.normalizar(l.personaje) })
                nuevas.add(l)
        }
        if (nuevas.isNotEmpty()) escribir(mias + nuevas)

        val m = cargarMarcas()
        var marcas = 0
        marcadoCopia.keys().forEach { k ->
            if (!m.containsKey(k)) { m[k] = marcadoCopia.optLong(k); marcas++ }
        }
        guardarMarcas()
        return nuevas.size to marcas
    }

    /**
     * Marca automatica: te has leido un fichero de una carpeta vinculada.
     * Devuelve true si ha marcado algo.
     */
    fun marcarPorCarpeta(carpeta: String, numero: Int?): Boolean {
        if (numero == null) return false
        val serie = todas().flatMap { it.series }.firstOrNull { it.carpeta == carpeta }
            ?: return false
        marcar(serie.id, numero, true)
        return true
    }
}
