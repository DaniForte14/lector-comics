package com.dani.lector.datos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lo que leiste de un comic en un dia concreto. */
data class Sesion(
    val uri: String,
    /** "aaaa-mm-dd", en la zona de la app. Ordena bien como texto. */
    val dia: String,
    /** Por que pagina ibas al empezar ese dia. */
    val desde: Int,
    /** La mas lejos que llegaste ese dia. */
    val hasta: Int,
    /** Paginas nuevas vistas ese dia. */
    val paginas: Int,
    val cuando: Long
)

/**
 * La regla de que cuenta como leer, separada del almacen para poder probarla.
 *
 * POR QUE HACE FALTA ESTO Y NO BASTABA [Marca]. La marca guarda UNA fecha por
 * comic: la ultima vez que lo tocaste. Con eso, un tomo leido lunes, miercoles y
 * viernes sale solo el viernes, y no hay forma de saber cuanto leiste cada dia.
 * Estaba escrito como limitacion del calendario y Dani pidio justo lo que
 * faltaba: "si leo lunes miercoles y viernes un mismo comic que salga en los 3
 * dias".
 *
 * UNA FILA POR COMIC Y DIA, no por pagina: es lo que hace que el fichero no
 * crezca sin control leyendo, y sigue contestando las dos preguntas —que dias
 * lei y cuanto— sin guardar un registro por pasada de pagina.
 */
object Lectura {

    /**
     * La sesion de hoy despues de ver [pagina].
     *
     * SE CUENTAN LAS PAGINAS NUEVAS, no las llamadas. El visor avisa en cada
     * cambio de pagina, asi que contar llamadas daria numeros disparatados solo
     * con pasar adelante y atras mirando una viñeta. Se suma la DIFERENCIA
     * contra lo mas lejos que habias llegado, que ademas cuadra sola en el modo
     * de dos paginas: alli cada pasada avanza dos y suma dos.
     *
     * IR HACIA ATRAS NO RESTA NI SUMA. Releer una pagina no es leer una pagina
     * nueva, pero tampoco deshace lo leido; el dia sigue contando como leido y
     * solo se refresca la hora.
     */
    fun registrar(actual: Sesion?, uri: String, dia: String, pagina: Int, ahora: Long): Sesion =
        when {
            actual == null || actual.dia != dia ->
                Sesion(uri, dia, desde = pagina, hasta = pagina, paginas = 1, cuando = ahora)
            pagina > actual.hasta ->
                actual.copy(
                    hasta = pagina,
                    paginas = actual.paginas + (pagina - actual.hasta),
                    cuando = ahora
                )
            else -> actual.copy(cuando = ahora)
        }
}

/**
 * El diario de lectura: que leiste, que dia y cuanto.
 *
 * Fichero propio, como el progreso y los marcapaginas, y por la misma razon: el
 * indice de la biblioteca se borra al reescanear y esto no puede irse con el.
 */
class Sesiones(private val ctx: Context) {

    /**
     * Tope de filas. Con cinco comics al dia son unos tres años de historial, y
     * un calendario no se mira mas atras. Un diario sin tope es una fuga lenta,
     * que es la leccion que ya costo 3,78 GB de cache.
     */
    private val TOPE = 6000

    private val fichero get() = File(ctx.filesDir, "sesiones.json")
    private var cache: MutableList<Sesion>? = null

    fun recargar() { cache = null }

    private fun cargar(): MutableList<Sesion> {
        cache?.let { return it }
        val out = mutableListOf<Sesion>()
        runCatching {
            if (fichero.exists()) {
                val a = JSONArray(fichero.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(Sesion(
                        uri = o.optString("uri"),
                        dia = o.optString("dia"),
                        desde = o.optInt("desde"),
                        hasta = o.optInt("hasta"),
                        paginas = o.optInt("pag"),
                        cuando = o.optLong("cuando")
                    ))
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
            // Se poda por lo mas VIEJO, no por el final de la lista: el orden
            // del fichero es de escritura, no cronologico.
            if (m.size > TOPE) {
                val vivas = m.sortedByDescending { it.dia }.take(TOPE)
                m.clear(); m.addAll(vivas)
            }
            val a = JSONArray()
            m.forEach {
                a.put(JSONObject().apply {
                    put("uri", it.uri); put("dia", it.dia)
                    put("desde", it.desde); put("hasta", it.hasta)
                    put("pag", it.paginas); put("cuando", it.cuando)
                })
            }
            fichero.writeText(a.toString())
        }
    }

    fun apuntar(uri: String, dia: String, pagina: Int, ahora: Long) {
        val m = cargar()
        val i = m.indexOfFirst { it.uri == uri && it.dia == dia }
        val nueva = Lectura.registrar(m.getOrNull(i), uri, dia, pagina, ahora)
        if (i >= 0) m[i] = nueva else m.add(nueva)
        guardar()
    }

    fun todas(): List<Sesion> = cargar()

    /** Lo leido un dia, lo ultimo primero. */
    fun de(dia: String): List<Sesion> =
        cargar().filter { it.dia == dia }.sortedByDescending { it.cuando }

    // ─────────────────── COPIA DE SEGURIDAD ───────────────────

    /** Por carpeta y nombre, igual que el progreso: las uris no sobreviven. */
    fun exportar(): JSONArray {
        val a = JSONArray()
        cargar().forEach {
            a.put(JSONObject().apply {
                put("clave", Progreso.clave(it.uri))
                put("dia", it.dia); put("desde", it.desde)
                put("hasta", it.hasta); put("pag", it.paginas); put("cuando", it.cuando)
            })
        }
        return a
    }

    /**
     * Se suma, no se pisa: si la misma sesion viene en las dos, gana la que mas
     * lejos llego. Marcar de mas es recuperable; perder un dia de lectura, no.
     */
    fun importar(a: JSONArray, urisActuales: Map<String, String>): Int {
        val m = cargar()
        var puestas = 0
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val uri = urisActuales[o.optString("clave")] ?: continue
            val dia = o.optString("dia")
            val nueva = Sesion(uri, dia, o.optInt("desde"), o.optInt("hasta"),
                o.optInt("pag"), o.optLong("cuando"))
            val j = m.indexOfFirst { it.uri == uri && it.dia == dia }
            if (j < 0) { m.add(nueva); puestas++ }
            else if (nueva.hasta > m[j].hasta) { m[j] = nueva; puestas++ }
        }
        guardar()
        return puestas
    }
}
