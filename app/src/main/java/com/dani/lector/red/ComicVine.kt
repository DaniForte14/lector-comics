package com.dani.lector.red

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente de Comic Vine. Aporta los datos duros del TODO.
 *
 * Es lento (unos 10 segundos por peticion) y exige un User-Agent propio: con el
 * de por defecto responde 403.
 */
class ComicVine(private val apiKey: String) : FuenteComics {

    override val nombre = "COMIC VINE"

    @Volatile private var fallo: String? = null
    override fun ultimoFallo() = fallo

    private val base = "https://comicvine.gamespot.com/api"
    private val cache = mutableMapOf<String, List<VolumenRemoto>>()
    private val recuentos = mutableMapOf<String, Pair<Int, Int>>()
    private val cacheVolumen = mutableMapOf<String, List<VolumenRemoto>>()
    private val numerosCache = mutableMapOf<String, List<NumeroRemoto>>()

    /** Tope de numeros que se traen de una serie. Action Comics pasa de 1000. */
    private val TOPE_NUMEROS = 1000

    @Volatile private var recuento: Pair<Int, Int>? = null
    override fun ultimoRecuento() = recuento

    /**
     * Todos los volumenes cuyo nombre contenga [personaje].
     * Aqui interesa la coincidencia parcial: para el TODO de Green Lantern
     * queremos ver tambien "Green Lantern Corps" y "Green Lantern: Rebirth".
     */
    override suspend fun volumenesDe(personaje: String): List<VolumenRemoto> =
        withContext(Dispatchers.IO) {
            val clave = Parser0.norm(personaje)
            cache[clave]?.let {
                recuento = recuentos[clave]
                return@withContext it
            }

            val out = mutableListOf<VolumenRemoto>()
            var total = 0
            var offset = 0
            // OJO: esto es un tope de lectura, no "todo lo que hay". Batman
            // devuelve 2217 volumenes y aqui se leen los primeros TOPE. Lo que
            // quede fuera se cuenta y se avisa; callarselo hacia parecer que la
            // lista estaba completa cuando le faltaba el 90%.
            while (offset < TOPE) {
                val j = get("$base/volumes/?filter=name:${enc(personaje)}" +
                            "&field_list=id,name,start_year,count_of_issues,publisher" +
                            "&limit=100&offset=$offset") ?: break
                total = j.optInt("number_of_total_results", total)
                val res = j.optJSONArray("results") ?: break
                if (res.length() == 0) break
                out.addAll(leer(res))
                offset += res.length()
                if (offset >= total) break
            }
            val limpio = out.filter { it.numeros > 0 }
                .distinctBy { "${it.nombre}|${it.anio}" }
                .sortedBy { it.anio ?: 9999 }
            cache[clave] = limpio
            val r = (if (total > 0) total else out.size) to out.size
            recuento = r
            recuentos[clave] = r
            limpio
        }

    private companion object {
        /** Cuantos volumenes se leen como mucho. 100 por peticion, ~10 s cada una. */
        const val TOPE = 200

        /**
         * Candidatos que se piden a /search/ para elegir uno.
         * Con 20 ya bastaba en las pruebas; se cogen mas por margen, pero no
         * 100: sin `field_list` cada resultado trae su descripcion en HTML y
         * quince URLs de imagen, y esto va a un movil.
         */
        const val CANDIDATOS = 50
    }

    /**
     * Un volumen concreto, por nombre y año.
     *
     * Va primero a [buscarPorRelevancia] y solo cae al filtro de nombre de
     * siempre si de alli no sale candidato. En la mayoria de los casos se
     * resuelve en UNA peticion, y el respaldo cubre lo que aquel camino ya
     * encontraba, asi que este cambio no puede empeorar ningun caso que
     * funcionara antes.
     *
     * AVISO sobre lo que ponia aqui antes: decia que se pedia
     * `name:X,start_year:Y` "por si el servidor respeta el año". **No lo
     * respeta.** `name:Green Lantern,start_year:2021` devuelve 303 resultados
     * y ni uno solo es de 2021; salen 1960, 1981, 1983... El `sort` tampoco
     * hace nada. En `/volumes/` lo unico que se mueve es el `offset`.
     */
    override suspend fun volumen(nombre: String, anio: Int?): VolumenRemoto? =
        withContext(Dispatchers.IO) {
            val clave = Parser0.norm(nombre) + "|" + (anio ?: 0)
            cacheVolumen[clave]?.let { return@withContext it.firstOrNull() }

            var elegido = elegirVolumen(buscarPorRelevancia(nombre), nombre, anio)

            // El respaldo solo sale si /search/ LLEGO a responder. Si nos han
            // cortado, el null no significa "no esta": significa que no hay
            // respuesta, y esta peticion se comeria otro 420. Sin esta guarda,
            // a partir del corte cada serie costaba DOS peticiones en vez de
            // una y el bucle aceleraba justo al chocar contra el limite.
            //
            // Sin año no se filtra por año: da igual, porque el servidor lo
            // ignora de todas formas y la eleccion se hace aqui.
            if (elegido == null && fallo == null) {
                elegido = elegirVolumen(buscar("name:${enc(nombre)}"), nombre, anio)
            }
            // Solo se cachea lo que ha ido bien. Cachear un fallo aqui es
            // veneno: al pasarse del limite de peticiones, Comic Vine devuelve
            // 420 y la serie se quedaba con cero numeros PARA SIEMPRE, porque
            // el siguiente intento leia el cero de la cache y no volvia a
            // preguntar. Es el reves de la leccion de cachear los fallos al
            // abrir ficheros: alli el fallo es permanente, aqui es pasajero.
            if (elegido != null) cacheVolumen[clave] = listOf(elegido)
            elegido
        }

    /**
     * Busqueda libre para añadir una serie a mano.
     *
     * Se quitan las de cero numeros y se ordenan por año descendente: quien
     * añade algo a mano suele venir a por lo reciente, y ademas las reediciones
     * viejas con el mismo nombre quedan abajo.
     */
    override suspend fun buscarSeries(texto: String): List<VolumenRemoto> =
        withContext(Dispatchers.IO) {
            if (texto.isBlank()) return@withContext emptyList()
            // Por relevancia tambien aqui: con el filtro de nombre, buscar
            // "batman" a mano devolvia los volumenes mas VIEJOS que contienen
            // esa subcadena, que casi nunca es lo que viene a buscar quien
            // añade una serie suelta.
            buscarPorRelevancia(texto.trim())
                .filter { it.numeros > 0 }
                .distinctBy { "${it.nombre}|${it.anio}" }
                .sortedByDescending { it.anio ?: 0 }
        }

    /**
     * Candidatos por BUSQUEDA, que es un endpoint distinto y arregla lo que
     * `/volumes/?filter=name:` no podia.
     *
     * Las dos diferencias, las dos comprobadas contra el servidor:
     *
     *  - **Busca por palabras, no por subcadena.** `query=Ion` devuelve OCHO
     *    resultados. El filtro de nombre devolvia miles, porque le casaban
     *    Act-ion, Leg-ion, Champ-ion, Rebell-ion...
     *  - **Ordena por relevancia, no por `id` ascendente.** Aquel orden ponia
     *    siempre lo mas antiguo primero y, como [buscar] lee UNA pagina, las
     *    series modernas sencillamente no existian para la app: Green Lantern
     *    (2021) y (2023) estaban pasada la posicion 100 de 303, y por eso se
     *    quedaban a cero mientras la de 2005 si aparecia.
     *
     * Comprobado: `query=Green Lantern` trae las de 2021 y 2023 entre las 20
     * primeras, y `query=Ion` pone el Ion de 2006 el primero de todos.
     *
     * Se piden [CANDIDATOS] y elige [elegirVolumen] como siempre: aqui solo
     * hace falta que el bueno entre en la lista, no que salga el primero. Si
     * el ranking cambiara algun dia, la eleccion no se ve afectada.
     *
     * El `field_list` va sin comprobar: si Comic Vine lo respeta, la respuesta
     * baja de megas a kilobytes; si lo ignora, viene gorda pero se lee igual,
     * porque solo se sacan los cuatro campos de siempre.
     */
    private fun buscarPorRelevancia(texto: String): List<VolumenRemoto> {
        val j = get("$base/search/?resources=volume&query=${encQuery(texto)}" +
                    "&field_list=id,name,start_year,count_of_issues,publisher" +
                    "&limit=$CANDIDATOS")
            ?: return emptyList()
        return leer(j.optJSONArray("results"))
    }

    private fun buscar(filtro: String): List<VolumenRemoto> {
        val j = get("$base/volumes/?filter=$filtro" +
                    "&field_list=id,name,start_year,count_of_issues,publisher&limit=100")
            ?: return emptyList()
        return leer(j.optJSONArray("results"))
    }

    private fun leer(res: org.json.JSONArray?): List<VolumenRemoto> {
        if (res == null) return emptyList()
        val out = mutableListOf<VolumenRemoto>()
        for (i in 0 until res.length()) {
            val v = res.getJSONObject(i)
            out.add(VolumenRemoto(
                nombre = v.optString("name"),
                anio = v.optString("start_year").toIntOrNull(),
                numeros = v.optInt("count_of_issues"),
                editorial = v.optJSONObject("publisher")?.optString("name"),
                id = v.optInt("id").takeIf { it > 0 }?.toString().orEmpty()
            ))
        }
        return out
    }

    /**
     * Todos los numeros de un volumen, ordenados por fecha de portada.
     *
     * PAGINA, y no es opcional: Comic Vine devuelve 100 por peticion y hay
     * series de 300 numeros. Sin paginar, "Detective Comics" diria que tiene
     * 100 y la mitad de la app se creeria que esta completa con 100.
     *
     * TOPE DE PAGINAS a proposito: una serie de mas de mil numeros existe
     * —Action Comics— y con esto se traen los primeros mil. Se prefiere cortar
     * a un numero conocido y decirlo que dejar una peticion abierta que puede
     * tirarse minutos.
     */
    override suspend fun numerosDe(volumenId: String): List<NumeroRemoto> =
        withContext(Dispatchers.IO) {
            if (volumenId.isBlank()) return@withContext emptyList()
            numerosCache[volumenId]?.let { return@withContext it }

            val out = mutableListOf<NumeroRemoto>()
            var offset = 0
            var total = -1

            while (offset < TOPE_NUMEROS) {
                val j = get("$base/issues/?filter=volume:$volumenId" +
                            // store_date es la fecha de venta DE VERDAD. Si no
                            // se pide, Comic Vine no la manda —la misma trampa
                            // que ya costo el id de los volumenes— y hay que
                            // estimarla restandole dos meses a la de portada.
                            "&field_list=issue_number,cover_date,store_date,name" +
                            "&sort=cover_date:asc&limit=100&offset=$offset")
                    ?: break

                if (total < 0) total = j.optInt("number_of_total_results", 0)
                val res = j.optJSONArray("results") ?: break
                if (res.length() == 0) break

                for (i in 0 until res.length()) {
                    val n = res.getJSONObject(i)
                    val etiqueta = n.optString("issue_number").trim()
                    out.add(NumeroRemoto(
                        etiqueta = etiqueta,
                        numero = numeroDeEtiqueta(etiqueta),
                        // optString devuelve "null" en texto cuando el campo es
                        // nulo, y una fecha "null" ordenaria como cualquier otra
                        // cadena y se colaria en medio de la lista.
                        fecha = n.optString("cover_date").takeIf {
                            it.isNotBlank() && it != "null"
                        },
                        nombre = n.optString("name").takeIf { it != "null" }.orEmpty(),
                        // Viene vacia en muchos numeros, sobre todo los
                        // anteriores a que Comic Vine empezara a guardarla.
                        // Ausente y nunca inventada: quien la use ya decide que
                        // hacer sin ella.
                        venta = n.optString("store_date").takeIf {
                            it.isNotBlank() && it != "null"
                        }
                    ))
                }

                offset += res.length()
                if (total in 1..offset) break
            }

            // El recuento se guarda para que quien lo use sepa si esto es TODO
            // lo que hay o solo lo que ha cabido, igual que en volumenesDe.
            recuento = (if (total < 0) out.size else total) to out.size
            if (out.isNotEmpty()) numerosCache[volumenId] = out
            out
        }

    /**
     * "12" -> 12. "1.MU", "0", "-1", "Annual 2" -> lo que se pueda.
     *
     * Comic Vine guarda el numero como TEXTO porque hay numeros que no son
     * numeros: decimales de eventos, negativos de "Zero Hour", anuales. Se coge
     * la parte entera del principio y si no hay nada, null. Un numero que no se
     * entiende NO se convierte en cero: un cero de mentira abre un hueco falso.
     */
    private fun numeroDeEtiqueta(e: String): Int? =
        Regex("""^-?\d+""").find(e.trim())?.value?.toIntOrNull()

    /** Prueba de conexion para la pantalla de ajustes. */
    suspend fun probar(): String = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val j = get("$base/volumes/?filter=name:Daredevil&field_list=name,start_year&limit=1")
        val ms = System.currentTimeMillis() - t0
        if (j == null) "FALLA: ${fallo ?: "sin respuesta"} ($ms ms)"
        else {
            val r = j.optJSONArray("results")?.optJSONObject(0)
            "OK en $ms ms · ${r?.optString("name")} (${r?.optString("start_year")})"
        }
    }

    private fun get(url: String, reintento: Int = 0): JSONObject? = try {
        val sep = if (url.contains("?")) "&" else "?"
        val c = (URL("$url${sep}api_key=$apiKey&format=json")
            .openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "LectorComics/1.0 (lector personal de comics)")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        val codigo = c.responseCode
        if (codigo !in 200..299) {
            fallo = when (codigo) {
                401, 403 -> "$codigo: clave rechazada o User-Agent bloqueado"
                420 -> "420: has superado el limite de peticiones"
                else -> "$codigo: error del servidor"
            }
            null
        } else {
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            // Comic Vine devuelve 200 aunque haya error: hay que mirar status_code
            val estado = j.optInt("status_code", 1)
            if (estado != 1) { fallo = "status_code $estado: ${j.optString("error")}"; null }
            else { fallo = null; j }
        }
    } catch (e: java.net.SocketTimeoutException) {
        if (reintento < 2) { Thread.sleep(2000); get(url, reintento + 1) }
        else { fallo = "agotado el tiempo de espera despues de 3 intentos"; null }
    } catch (e: Exception) {
        fallo = "sin conexion: ${e.javaClass.simpleName}: ${e.message ?: ""}"
        null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * Igual que [enc] pero con el espacio como `%20` en vez de `+`.
     *
     * `URLEncoder` es codificacion de FORMULARIO y ahi el espacio es `+`. En
     * `/volumes/?filter=` se sabe que Comic Vine lo acepta, porque la app
     * lleva funcionando asi. En `/search/?query=` NO se sabe: las pruebas se
     * hicieron desde el navegador, que manda `%20`. Si ahi el `+` no se
     * interpretara como espacio, se estaria buscando la cadena literal
     * "Green+Lantern" y no encontraria nada nunca.
     *
     * `%20` vale en los dos casos y es lo unico comprobado, asi que se usa eso
     * y no se depende de averiguarlo.
     */
    private fun encQuery(s: String) = enc(s).replace("+", "%20")

    /** Copia minima del normalizador para no depender del paquete de datos. */
    private object Parser0 {
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    }
}
