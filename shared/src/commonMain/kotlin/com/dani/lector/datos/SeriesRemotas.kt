package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto
import com.dani.lector.red.getJSONObject
import com.dani.lector.red.has
import com.dani.lector.red.jsonLaxo
import com.dani.lector.red.length
import com.dani.lector.red.optBoolean
import com.dani.lector.red.optInt
import com.dani.lector.red.optJSONArray
import com.dani.lector.red.optLong
import com.dani.lector.red.optString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Que serie de Comic Vine es cada carpeta tuya, y que numeros tiene.
 *
 * POR QUE SE GUARDA EN DISCO Y NO SE PREGUNTA CADA VEZ
 *
 * Traer los numeros de una serie es una peticion por cada cien numeros, y Comic
 * Vine tarda unos diez segundos en responder. Con sesenta carpetas eso son diez
 * minutos de espera cada vez que abres la app, y una cuota de peticiones que
 * tiene limite diario. Se pregunta UNA vez por serie y se guarda.
 *
 * EL VINCULO SE GUARDA APARTE DE LOS NUMEROS, y eso es lo importante del
 * diseño: [volumenId] es una decision —"esta carpeta ES esta serie"— que puede
 * estar MAL, porque elegir el volumen bueno entre los candidatos de Comic Vine
 * falla de vez en cuando y ya esta documentado que falla. Los numeros, en
 * cambio, son un dato que se puede volver a pedir sin perder nada.
 *
 * Separarlos permite corregir el vinculo sin tirar lo demas, y volver a pedir
 * los numeros de una serie en emision sin tocar el vinculo.
 */
class SeriesRemotas(private val disco: Disco) {


    private val fichero = "series_remotas.json"

    private var cache: MutableMap<String, Ficha>? = null

    fun recargar() { cache = null }

    private fun cargar(): MutableMap<String, Ficha> {
        cache?.let { return it }
        val out = mutableMapOf<String, Ficha>()
        runCatching {
            disco.leer(fichero)?.let { texto ->
                val arr = jsonLaxo.parseToJsonElement(texto) as? JsonArray
                for (i in 0 until (arr?.length() ?: 0)) {
                    val o = arr!!.getJSONObject(i)
                    val nums = mutableListOf<NumeroRemoto>()
                    val an = o.optJSONArray("numeros")
                    for (j in 0 until (an?.length() ?: 0)) {
                        val n = an!!.getJSONObject(j)
                        nums.add(NumeroRemoto(
                            etiqueta = n.optString("e"),
                            // has() y no optInt: un numero que no se entiende se
                            // guarda como ausente, y optInt lo devolveria como 0,
                            // que ES un numero valido de grapa.
                            numero = if (n.has("n")) n.optInt("n") else null,
                            fecha = n.optString("f").takeIf { it.isNotBlank() },
                            nombre = n.optString("t"),
                            venta = n.optString("v").takeIf { it.isNotBlank() }
                        ))
                    }
                    // Los campos nuevos con optBoolean y sin exigir que
                    // esten: el JSON escrito antes de que existieran tiene que
                    // seguir cargando sin tocar nada. Misma regla que
                    // Serie.noEncontrada en Listas.
                    val vistos = mutableSetOf<String>()
                    val av = o.optJSONArray("avisados")
                    for (j in 0 until (av?.length() ?: 0)) vistos.add(av!!.optString(j))
                    val f = Ficha(
                        ruta = o.optString("ruta"),
                        volumenId = o.optString("id"),
                        nombre = o.optString("nombre"),
                        anio = if (o.has("anio")) o.optInt("anio") else null,
                        numeros = nums,
                        cuando = o.optLong("cuando"),
                        seguida = o.optBoolean("seguida", false),
                        avisados = vistos
                    )
                    if (f.ruta.isNotBlank()) out[clave(f.ruta)] = f
                }
            }
        }
        // Si otro hilo termino de cargar mientras este parseaba, manda el suyo:
        // lo que ya este en la cache puede llevar cambios hechos entretanto, y
        // pisarlo con esta copia recien leida del disco los perderia.
        return cache ?: out.also { cache = it }
    }

    /** Las rutas vienen a veces con barra delante o detras segun quien las pase. */
    private fun clave(ruta: String) = ruta.trim('/')

    fun de(ruta: String): Ficha? = cargar()[clave(ruta)]

    fun todas(): List<Ficha> = cargar().values.toList()

    fun guardar(f: Ficha) {
        val m = cargar()
        m[clave(f.ruta)] = f
        escribir(m)
    }

    fun olvidar(ruta: String) {
        val m = cargar()
        m.remove(clave(ruta))
        escribir(m)
    }

    private fun escribir(m: Map<String, Ficha>) {
        val arr = buildJsonArray {
            m.values.forEach { f ->
                add(buildJsonObject {
                    put("ruta", f.ruta)
                    put("id", f.volumenId)
                    put("nombre", f.nombre)
                    f.anio?.let { put("anio", it) }
                    put("cuando", f.cuando)
                    if (f.seguida) put("seguida", true)
                    if (f.avisados.isNotEmpty())
                        put("avisados", buildJsonArray { f.avisados.forEach { add(JsonPrimitive(it)) } })
                    put("numeros", buildJsonArray {
                        f.numeros.forEach { n ->
                            add(buildJsonObject {
                                put("e", n.etiqueta)
                                n.numero?.let { put("n", it) }
                                n.fecha?.let { put("f", it) }
                                n.venta?.let { put("v", it) }
                                if (n.nombre.isNotBlank()) put("t", n.nombre)
                            })
                        }
                    })
                })
            }
        }
        runCatching { disco.escribir(fichero, arr.toString()) }
    }
}
