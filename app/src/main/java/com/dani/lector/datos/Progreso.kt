package com.dani.lector.datos

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Por donde vas en un numero concreto. */
data class Marca(val pagina: Int, val paginas: Int, val cuando: Long) {
    val terminado get() = paginas > 0 && pagina >= paginas - 1
    val porcentaje get() = if (paginas <= 1) 0 else (pagina * 100) / (paginas - 1)
}

/**
 * Recuerda por que pagina ibas de cada numero.
 *
 * Se guarda en un JSON aparte del indice de la biblioteca: el indice se borra
 * entero al reescanear y lo que has leido no debe perderse por eso.
 */
class Progreso(private val ctx: Context) {

    private val fichero get() = File(ctx.filesDir, "progreso.json")
    private var cache: MutableMap<String, Marca>? = null

    private fun cargar(): MutableMap<String, Marca> {
        cache?.let { return it }
        val m = mutableMapOf<String, Marca>()
        runCatching {
            if (fichero.exists()) {
                val o = JSONObject(fichero.readText())
                o.keys().forEach { k ->
                    val v = o.getJSONObject(k)
                    m[k] = Marca(v.optInt("pag"), v.optInt("total"), v.optLong("cuando"))
                }
            }
        }
        cache = m
        return m
    }

    /**
     * Mientras dura una [tanda] no se escribe: se escribe una vez al final.
     *
     * POR QUE HACE FALTA. `marcar` guarda el fichero ENTERO cada vez, que es lo
     * correcto para una marca suelta y es una barbaridad para treinta seguidas:
     * marcar una carpeta reescribia progreso.json una vez por comic, y deshacer
     * ese marcado, otra vez por comic.
     *
     * ponytail: bandera simple y no un lock. Las dos tandas que hay corren en
     * el mismo `viewModelScope` con `Dispatchers.IO` y no se solapan; si algun
     * dia se marcan dos carpetas a la vez, esto necesita un lock de verdad.
     */
    private var enTanda = false

    fun <T> tanda(bloque: () -> T): T {
        enTanda = true
        try {
            return bloque()
        } finally {
            enTanda = false
            guardar()
        }
    }

    private fun guardar() {
        if (enTanda) return
        val m = cache ?: return
        runCatching {
            val o = JSONObject()
            m.forEach { (k, v) ->
                o.put(k, JSONObject().apply {
                    put("pag", v.pagina); put("total", v.paginas); put("cuando", v.cuando)
                })
            }
            fichero.writeText(o.toString())
        }
    }

    fun de(uri: String): Marca? = cargar()[uri]

    /** Todo lo que hay, para las estadisticas. */
    fun todas(): Map<String, Marca> = cargar()

    fun marcar(uri: String, pagina: Int, paginas: Int) {
        val m = cargar()
        m[uri] = Marca(pagina, paginas, System.currentTimeMillis())
        guardar()
    }

    fun marcarTerminado(uri: String, paginas: Int) = marcar(uri, paginas - 1, paginas)

    /**
     * Volver a dejar una marca EXACTAMENTE como estaba, o quitarla si no habia.
     *
     * Aparte de [marcar] porque aquella pone la fecha de AHORA —es lo correcto
     * cuando estas leyendo— y deshacer no es leer: si al deshacer se refrescara
     * la fecha, los comics rescatados subirian al principio de "En curso" como
     * si acabaras de tocarlos. Deshacer tiene que dejarlo todo como estaba,
     * incluido el orden.
     */
    fun restaurar(uri: String, marca: Marca?) {
        val m = cargar()
        if (marca == null) m.remove(uri) else m[uri] = marca
        guardar()
    }

    fun olvidar(uri: String) {
        cargar().remove(uri)
        guardar()
    }

    /** Lo ultimo que tocaste, para el "Continuar leyendo". */
    fun ultimoAbierto(): Pair<String, Marca>? =
        cargar().entries
            .filter { !it.value.terminado }
            .maxByOrNull { it.value.cuando }
            ?.let { it.key to it.value }

    fun leidos(uris: Collection<String>): Int =
        cargar().filterKeys { it in uris }.count { it.value.terminado }

    // ─────────────────── COPIA DE SEGURIDAD ───────────────────

    /**
     * El progreso, guardado POR NOMBRE DE FICHERO y no por su uri.
     *
     * Las uris de SAF llevan dentro la ruta completa y el permiso concreto que
     * concediste: si reinstalas, si mueves la carpeta o si vuelves a dar
     * permiso, cambian TODAS. Una copia guardada por uri no serviria para nada
     * el dia que hace falta.
     *
     * Se usan los dos ultimos tramos —carpeta y fichero— y no solo el nombre:
     * "Batman 01.cbz" existe en cuatro carpetas distintas de cualquier
     * coleccion, pero "Batman Vol 3/Batman 01.cbz" ya no se repite.
     */
    fun exportar(): JSONObject {
        val o = JSONObject()
        cargar().forEach { (uri, m) ->
            o.put(clave(uri), JSONObject().apply {
                put("pag", m.pagina); put("total", m.paginas); put("cuando", m.cuando)
            })
        }
        return o
    }

    /**
     * Devuelve cuantas marcas se han podido recolocar.
     *
     * [urisActuales] es el mapa clave -> uri de tu biblioteca de ahora. Lo que
     * no encuentre sitio se descarta en silencio: son comics que ya no tienes.
     */
    fun importar(o: JSONObject, urisActuales: Map<String, String>): Int {
        val m = cargar()
        var puestas = 0
        o.keys().forEach { k ->
            val uri = urisActuales[k] ?: return@forEach
            val v = o.optJSONObject(k) ?: return@forEach
            val nueva = Marca(v.optInt("pag"), v.optInt("total"), v.optLong("cuando"))
            val vieja = m[uri]
            // gana la mas reciente: si has seguido leyendo desde la copia, no
            // te la puede echar atras
            if (vieja == null || nueva.cuando > vieja.cuando) { m[uri] = nueva; puestas++ }
        }
        guardar()
        return puestas
    }

    companion object {

        /**
         * Si una pagina que estas viendo debe guardarse como "por aqui vas".
         *
         * EL PROBLEMA QUE RESUELVE (Dani, 02/09/2026): "si tengo una pagina de
         * un comic que ya he leido en el marcapaginas, cuando le doy es como si
         * quisiera comenzar a leerlo y se pone en el apartado de en curso y se
         * quita el ya leido".
         *
         * Y tenia toda la razon, porque [Marca.terminado] no es un campo
         * guardado: es una CUENTA sobre la pagina por la que vas
         * (`pagina >= paginas - 1`). Asi que abrir el marcapaginas de la pagina
         * 4 de un comic de 23 guardaba "vas por la 4 de 23", y con eso el comic
         * dejaba de estar terminado, aparecia en "En curso" y encima subia al
         * principio, porque tambien se le refresca la fecha.
         *
         * LA REGLA: consultar no es leer. [techo] es la pagina por la que ibas
         * cuando entraste por un marcapaginas, y mientras no la pases, lo que
         * mires no cuenta. En cuanto la pasas, es que estas leyendo de verdad y
         * el progreso vuelve a guardarse normal.
         *
         * Arregla dos casos de la misma forma, y el segundo nadie lo habia
         * visto todavia:
         *
         *  - Un comic TERMINADO: su pagina guardada es la ultima, asi que
         *    ningun marcapaginas la pasa y no se toca nada. Sigue leido.
         *  - Un comic A MEDIAS: ibas por la 40 y el marcapaginas es la 2. Antes
         *    esto te movia a la 2 y perdias por donde ibas. Ahora no.
         *
         * [techo] a -1 significa que no entraste por un marcapaginas, y
         * entonces se guarda siempre — incluido pasar paginas hacia atras, que
         * leyendo es normal.
         *
         * LO QUE NO CUBRE, a proposito: un marcapaginas por DELANTE de donde
         * ibas (la 60 estando en la 40) si adelanta el progreso. Se acepta
         * porque un marcapaginas se pone en lo que ya has visto, y distinguir
         * ese caso pedia guardar mas cosas para un caso que casi no pasa.
         */
        fun cuenta(pagina: Int, techo: Int): Boolean = techo < 0 || pagina > techo

        /** La clave estable de un comic: su carpeta y su nombre. */
        fun clave(uri: String): String =
            android.net.Uri.decode(uri).split('/')
                .filter { it.isNotBlank() }
                .takeLast(2)
                .joinToString("/")
    }
}
