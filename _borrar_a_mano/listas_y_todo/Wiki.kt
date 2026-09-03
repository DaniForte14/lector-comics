package com.dani.lector.red

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Un volumen tal y como lo numera la wiki: "Daredevil Vol 6" es el volumen 6,
 * empezo en 2019 y acabo en 2022.
 */
data class VolumenWiki(
    val serie: String,
    val volumen: Int,
    val anio: Int?,
    val anioFin: Int?,
    val titulo: String,
    /** "marvel" o "dc". */
    val wiki: String
)

/**
 * Una serie del indice de un personaje.
 *
 * `eras` solo lleva las etiquetas verificadas; el resto de categorias que
 * describen la serie (y no la pagina) van en `grupos` sin clasificar, para no
 * perder una era que todavia no conozcamos.
 */
data class SerieWiki(
    val titulo: String,
    val nombre: String,
    val volumen: Int,
    val anio: Int?,
    val anioFin: Int?,
    val enCurso: Boolean,
    val eras: List<String>,
    val grupos: List<String>,
    val wiki: String
)

/**
 * De donde sale la traduccion "vol.N -> año".
 *
 * Existe por una razon concreta: la numeracion de volumenes NO esta en ninguna
 * base de datos de comics. "Vol. 6" es una convencion de aficionados. Comic Vine
 * solo dice el año, y deducir el ordinal ordenando las series por fecha falla,
 * porque la base de datos incluye reediciones con el mismo nombre y los
 * ordinales se desplazan.
 *
 * Las wikis de Marvel y DC si tienen paginas tituladas literalmente
 * "Daredevil Vol 6", y se consultan por la API de MediaWiki sin clave.
 */
interface FuenteVolumenes {
    val nombre: String get() = "desconocida"
    val disponible: Boolean get() = true
    fun ultimoFallo(): String? = null

    suspend fun volumenesDe(serie: String, editorial: String?): List<VolumenWiki>

    /**
     * El indice curado de series de un personaje. Vacio si esta fuente no lo
     * tiene, y entonces se sigue por el camino de siempre.
     */
    suspend fun seriesDe(personaje: String, editorial: String? = null): List<SerieWiki> =
        emptyList()

    /** Lo unico que necesita el vinculador: volumen -> año. */
    suspend fun mapaDe(serie: String, editorial: String? = null): Map<Int, Int> =
        volumenesDe(serie, editorial)
            .mapNotNull { v -> v.anio?.let { v.volumen to it } }
            .toMap()
}

object VolumenesVacio : FuenteVolumenes {
    override val nombre = "ninguna"
    override val disponible = false
    override suspend fun volumenesDe(serie: String, editorial: String?) = emptyList<VolumenWiki>()
}

/**
 * Cliente de las wikis de Marvel y DC (MediaWiki, sin clave de API).
 *
 * DOS COSAS QUE PARECEN BUENA IDEA Y NO LO SON, comprobadas contra el servidor:
 *
 * 1. Buscar por prefijo (`list=allpages&apprefix=Daredevil Vol `) NO sirve:
 *    devuelve tambien la pagina de cada grapa ("Daredevil Vol 1 100") y los
 *    primeros 50 resultados se los come el volumen 1 entero. Como los titulos
 *    son deterministas, se piden directamente del 1 al [MAX_VOLUMENES] y los
 *    que no existan vuelven marcados como `missing`.
 *
 * 2. El año NO esta en la ficha de la pagina. La plantilla de volumen no tiene
 *    ningun campo de fecha. Donde si esta es en las CATEGORIAS que genera:
 *    "Category:2019 Volume Debuts". Ojo, que en esa misma lista conviven
 *    "Category:2022 Eisner Awards" y demas: el patron va anclado al principio
 *    y al final, o se cuela el año de un premio.
 *
 * Y ojo con dar por buena una wiki mirando la otra: NO usan las mismas
 * categorias. Marvel dice "2019 Volume Debuts" y "2022 Volume Ends"; DC dice
 * "2016 Comic Debuts" y "2026 Last Issues". Lo unico que comparten es
 * "Category:Volumes".
 *
 * Total: una peticion por serie (dos si la wiki pagina las categorias).
 */
class Wiki : FuenteVolumenes {

    override val nombre = "WIKIS MARVEL/DC"

    @Volatile private var fallo: String? = null
    override fun ultimoFallo() = fallo

    private val marvel = "https://marvel.fandom.com/api.php"
    private val dc = "https://dc.fandom.com/api.php"

    private val cache = mutableMapOf<String, List<VolumenWiki>>()
    private val cacheIndice = mutableMapOf<String, List<SerieWiki>>()

    override suspend fun volumenesDe(serie: String, editorial: String?): List<VolumenWiki> =
        withContext(Dispatchers.IO) {
            val clave = norm(serie)
            cache[clave]?.let { return@withContext it }

            // Se prueba primero la wiki de su editorial. Si no da nada, la otra:
            // Comic Vine a veces deja la editorial vacia o pone la del recopilatorio.
            var out = emptyList<VolumenWiki>()
            for ((id, api) in porEditorial(editorial)) {
                out = deWiki(id, api, serie)
                if (out.isNotEmpty()) break
            }
            cache[clave] = out
            out
        }

    private fun porEditorial(editorial: String?): List<Pair<String, String>> {
        val e = editorial.orEmpty().lowercase()
        return when {
            e.contains("dc") -> listOf("dc" to dc, "marvel" to marvel)
            else -> listOf("marvel" to marvel, "dc" to dc)
        }
    }

    private fun deWiki(id: String, api: String, serie: String): List<VolumenWiki> {
        val titulos = variantes(serie).flatMap { n -> (1..MAX_VOLUMENES).map { "$n Vol $it" } }
        return interpretar(serie, id, categoriasDe(api, titulos))
    }

    /**
     * Los nombres con los que puede estar titulada la pagina.
     *
     * Comic Vine dice "The Amazing Spider-Man" y la wiki titula
     * "Amazing Spider-Man Vol 5": comprobado, con el articulo delante la pagina
     * no existe. Las dos formas se piden en la MISMA peticion (titles admite
     * cincuenta titulos) en vez de gastar otro viaje cuando falla la primera.
     */
    private fun variantes(serie: String): List<String> {
        val base = titulizar(serie)
        val pelado = base.removePrefix("The ").trim()
        return if (pelado.isNotBlank() && pelado != base) listOf(base, pelado) else listOf(base)
    }

    /**
     * De categorias a volumenes. Esta separado de la red a proposito: es la
     * parte que tiene reglas, asi que conviene poder probarla sola.
     */
    internal fun interpretar(
        serie: String,
        id: String,
        cats: Map<String, List<String>>
    ): List<VolumenWiki> {
        val aceptados = variantes(serie).map { norm(it) }.toSet()

        val out = mutableListOf<VolumenWiki>()
        for ((titulo, lista) in cats) {
            val m = RE_TITULO.find(titulo) ?: continue
            // el titulo que devuelve MediaWiki viene normalizado: se confirma
            if (norm(m.groupValues[1]) !in aceptados) continue
            // sin esta categoria no es la pagina de un volumen: sera una
            // redireccion o una pagina de otra cosa que se llama parecido
            if (lista.none { it == "Category:Volumes" }) continue

            out.add(VolumenWiki(
                serie = serie,
                volumen = m.groupValues[2].toIntOrNull() ?: continue,
                anio = lista.firstNotNullOfOrNull { anioDe(RE_DEBUT, it) },
                anioFin = lista.firstNotNullOfOrNull { anioDe(RE_FIN, it) },
                titulo = titulo,
                wiki = id
            ))
        }
        // si existieran las dos formas del titulo, un volumen saldria dos veces
        return out.sortedBy { it.volumen }.distinctBy { it.volumen }
    }

    /**
     * Todas las series de un personaje, segun el indice curado de la wiki.
     *
     * Esto es lo que Comic Vine no sabe dar: buscar "Batman" por nombre alli
     * devuelve 2217 resultados con tomos recopilatorios, ediciones francesas y
     * one-shots sueltos. La wiki tiene una categoria por personaje con SUS
     * SERIES, y ademas curada:
     *   DC      "Category:Batman Titles"
     *   Marvel  "Category:Daredevil Comic Books"
     * Y mete lo que toca aunque no lleve el nombre: Detective Comics Vol 1 esta
     * en "Batman Titles", que es justo lo que una busqueda por texto no hace.
     */
    override suspend fun seriesDe(personaje: String, editorial: String?): List<SerieWiki> =
        withContext(Dispatchers.IO) {
            val clave = norm(personaje)
            cacheIndice[clave]?.let { return@withContext it }

            var out = emptyList<SerieWiki>()
            for ((id, api) in porEditorial(editorial)) {
                val titulos = indiceDe(api, personaje)
                if (titulos.isEmpty()) continue
                out = interpretarIndice(id, titulos, categoriasDe(api, titulos))
                if (out.isNotEmpty()) break
            }
            cacheIndice[clave] = out
            out
        }

    /**
     * Cada wiki llama distinto a lo mismo y ademas hay que acertar con las
     * mayusculas.
     *
     * MediaWiki solo pone en mayuscula la PRIMERA letra del titulo; el resto
     * tiene que coincidir tal cual. Escribiendo "Green lantern" se pedia
     * "Category:Green lantern Titles", que no existe, y salia que la wiki no
     * conocia al personaje. Por eso se prueba tambien palabra a palabra.
     */
    private fun indiceDe(api: String, personaje: String): List<String> {
        for (p in formasDelNombre(personaje)) {
            for (cat in listOf("Category:$p Titles", "Category:$p Comic Books")) {
                val t = miembrosDe(api, cat)
                if (t.isNotEmpty()) return t
            }
        }
        return emptyList()
    }

    /** "green lantern" y "GREEN LANTERN" tienen que llegar a "Green Lantern". */
    internal fun formasDelNombre(personaje: String): List<String> {
        val palabras = personaje.trim().split(" ").filter { it.isNotBlank() }
        if (palabras.isEmpty()) return emptyList()
        val palabraAPalabra = palabras.joinToString(" ") { p ->
            p.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
        return listOf(palabraAPalabra, titulizar(palabras.joinToString(" "))).distinct()
    }

    private fun miembrosDe(api: String, categoria: String): List<String> {
        val out = mutableListOf<String>()
        var seguir: String? = null
        var vueltas = 0
        do {
            val extra = seguir?.let { "&cmcontinue=${enc(it)}" }.orEmpty()
            val j = get("$api?action=query&list=categorymembers&cmnamespace=0" +
                        "&cmtitle=${enc(categoria)}&cmlimit=500&format=json$extra") ?: break
            val arr = j.optJSONObject("query")?.optJSONArray("categorymembers") ?: break
            for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).optString("title"))
            seguir = j.optJSONObject("continue")?.optString("cmcontinue")?.ifBlank { null }
            vueltas++
        } while (seguir != null && vueltas < 6)
        return out.filter { RE_TITULO.matches(it) }
    }

    /** Separado de la red para poder probarlo con respuestas reales guardadas. */
    internal fun interpretarIndice(
        id: String,
        titulos: List<String>,
        cats: Map<String, List<String>>
    ): List<SerieWiki> {
        val out = mutableListOf<SerieWiki>()
        for (titulo in titulos) {
            val m = RE_TITULO.find(titulo) ?: continue
            val lista = cats[titulo] ?: continue
            if (lista.none { it == "Category:Volumes" }) continue
            out.add(SerieWiki(
                titulo = titulo,
                nombre = m.groupValues[1],
                volumen = m.groupValues[2].toIntOrNull() ?: continue,
                anio = lista.firstNotNullOfOrNull { anioDe(RE_DEBUT, it) },
                anioFin = lista.firstNotNullOfOrNull { anioDe(RE_FIN, it) },
                enCurso = lista.any { it == "Category:Volumes Currently in Publication" },
                eras = lista.mapNotNull { ERAS[it] },
                grupos = lista.filter { esGrupo(it) }.map { it.removePrefix("Category:") },
                wiki = id
            ))
        }
        return out.sortedWith(compareBy({ it.anio ?: 9999 }, { it.nombre }))
    }

    /** Lo que no es ni era conocida ni fontaneria de la wiki. */
    private fun esGrupo(categoria: String): Boolean =
        categoria !in ERAS && RUIDO.none { it.matches(categoria) }

    private fun anioDe(reglas: List<Regex>, categoria: String): Int? =
        reglas.firstNotNullOfOrNull { re ->
            re.find(categoria)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1930..2100 }
        }

    /**
     * Las categorias de varias paginas de golpe.
     *
     * MediaWiki corta por `cllimit` contando categorias de TODAS las paginas
     * juntas, no por pagina, asi que con quince volumenes se puede pasar del
     * tope y hay que seguir por `clcontinue`.
     */
    private fun categoriasDe(api: String, titulos: List<String>): Map<String, List<String>> {
        val out = mutableMapOf<String, MutableList<String>>()
        // `titles` admite 50 por peticion y el indice de un personaje grande
        // trae cientos: hay que trocear o la wiki devuelve un error de limite.
        for (trozo in titulos.chunked(50)) unTrozo(api, trozo, out)
        return out
    }

    private fun unTrozo(api: String, titulos: List<String>, out: MutableMap<String, MutableList<String>>) {
        var seguir: String? = null
        var vueltas = 0
        do {
            val extra = seguir?.let { "&clcontinue=${enc(it)}" }.orEmpty()
            val j = get("$api?action=query&prop=categories&cllimit=500&format=json" +
                        "&titles=${enc(titulos.joinToString("|"))}$extra") ?: break
            val paginas = j.optJSONObject("query")?.optJSONObject("pages") ?: break

            for (k in paginas.keys()) {
                val p = paginas.optJSONObject(k) ?: continue
                if (p.has("missing")) continue          // ese volumen no existe
                val acumulado = out.getOrPut(p.optString("title")) { mutableListOf() }
                val cats = p.optJSONArray("categories") ?: continue
                for (i in 0 until cats.length()) acumulado.add(cats.getJSONObject(i).optString("title"))
            }

            seguir = j.optJSONObject("continue")?.optString("clcontinue")?.ifBlank { null }
            vueltas++
        } while (seguir != null && vueltas < 4)
    }

    /**
     * Prueba de conexion para Ajustes. No solo dice si responde: contrasta el
     * resultado contra numeraciones verificadas a mano, UNA POR WIKI, porque
     * cada una usa sus propias categorias y que funcione Marvel no dice nada
     * de DC. Si alguna cambia, esto lo canta en vez de devolver años
     * silenciosamente equivocados.
     */
    suspend fun probar(): List<String> = withContext(Dispatchers.IO) {
        listOf(
            prueba("marvel", marvel, "Daredevil", CONTROL_MARVEL),
            prueba("dc", dc, "Batman", CONTROL_DC)
        )
    }

    private fun prueba(id: String, api: String, serie: String, control: Map<Int, Int>): String {
        val t0 = System.currentTimeMillis()
        val v = deWiki(id, api, serie)
        val ms = System.currentTimeMillis() - t0
        val eti = id.uppercase()
        if (v.isEmpty()) return "$eti FALLA: ${fallo ?: "no ha devuelto volumenes"} ($ms ms)"

        val sale = v.filter { it.volumen in control.keys }.associate { it.volumen to it.anio }
        val detalle = v.joinToString(" ") { "v${it.volumen}=${it.anio ?: "?"}" }
        return if (sale == control) "$eti OK · $serie en $ms ms · $detalle"
        else "$eti FALLA: la numeración no cuadra ($ms ms) · $detalle · esperaba " +
             control.entries.joinToString(" ") { "v${it.key}=${it.value}" }
    }

    private fun get(url: String, reintento: Int = 0): JSONObject? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            // Fandom rechaza el User-Agent por defecto de Java, igual que Comic Vine
            setRequestProperty("User-Agent", "LectorComics/1.0 (lector personal de comics)")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val codigo = c.responseCode
        if (codigo !in 200..299) {
            fallo = when (codigo) {
                403 -> "403: User-Agent bloqueado o wiki detras de anti-bots"
                404 -> "404: esa wiki no tiene api.php"
                429 -> "429: demasiadas peticiones seguidas"
                else -> "$codigo: error del servidor"
            }
            null
        } else {
            fallo = null
            JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        }
    } catch (e: java.net.SocketTimeoutException) {
        if (reintento < 1) { Thread.sleep(1500); get(url, reintento + 1) }
        else { fallo = "agotado el tiempo de espera"; null }
    } catch (e: Exception) {
        fallo = "sin conexion: ${e.javaClass.simpleName}: ${e.message ?: ""}"
        null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** MediaWiki pone la primera letra en mayuscula siempre; se la damos ya puesta. */
    private fun titulizar(s: String) =
        s.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun norm(s: String) = s.lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
        .replace(Regex("[^a-z0-9]"), "")

    private companion object {
        /** Ningun personaje llega a tantos volumenes; sobra de largo. */
        const val MAX_VOLUMENES = 15

        val RE_TITULO = Regex("""^(.+) Vol (\d{1,2})$""")

        // Anclados a los dos extremos: asi "2022 Eisner Awards" no puede colarse
        // y "August Comic Debuts", que no lleva año, tampoco.
        // La primera regla es la de Marvel y la segunda la de DC. Se prueban las
        // dos en las dos wikis: no chocan entre si, y si una cambia de nombres
        // la otra sigue funcionando.
        val RE_DEBUT = listOf(
            Regex("""^Category:(\d{4}) Volume Debuts$"""),
            Regex("""^Category:(\d{4}) Comic Debuts$""")
        )
        val RE_FIN = listOf(
            Regex("""^Category:(\d{4}) Volume Ends$"""),
            Regex("""^Category:(\d{4}) Last Issues$""")
        )

        /**
         * Eras VERIFICADAS, y solo esas.
         *
         * Una categoria de era no se distingue de un indice de personaje por el
         * nombre: "DC Rebirth Titles" y "Catwoman Titles" tienen la misma pinta,
         * y "The New 52" ni siquiera acaba en "Titles". Asi que aqui solo entra
         * lo comprobado contra el servidor. Lo que no este sigue saliendo, en
         * `grupos`, solo que sin la etiqueta de era: no se pierde nada y no se
         * inventa nada.
         */
        val ERAS = mapOf(
            "Category:The New 52" to "The New 52",
            "Category:DC Rebirth Titles" to "DC Rebirth",
            "Category:Absolute Universe Titles" to "Absolute Universe"
        )

        /** Fontaneria de la wiki: no describe la serie, describe la pagina. */
        val RUIDO = listOf(
            Regex("""^Category:\d{4} (Comic|Volume) (Debuts|Ends)$"""),
            Regex("""^Category:\d{4} Last Issues$"""),
            Regex("""^Category:(January|February|March|April|May|June|July|August|September|October|November|December) .*$"""),
            Regex("""^Category:Volumes.*$"""),
            Regex("""^Category:Volume Status Needed$"""),
            Regex("""^Category:Comics by Title$"""),
            Regex("""^Category:(Ongoing|Limited|Solo) (Series|Volumes)$"""),
            Regex("""^Category:Finished Volumes$"""),
            Regex("""^Category:Super Hero Genre$"""),
            Regex("""^Category:Pages using .*$"""),
            Regex("""^Category:.*Eisner.*$"""),
            Regex("""^Category:(Marvel|DC) Comics$"""),
            Regex("""^Category:Outdated Fields/Links$"""),
            Regex("""^Category:References With Internal Links$""")
        )

        /** Daredevil en la wiki de Marvel, comprobado a mano el 24/08/2026. */
        val CONTROL_MARVEL = mapOf(
            1 to 1964, 2 to 1998, 3 to 2011, 4 to 2014,
            5 to 2016, 6 to 2019, 7 to 2022, 8 to 2023
        )

        /** Batman en la wiki de DC. Solo el vol.3, que es el que esta verificado. */
        val CONTROL_DC = mapOf(3 to 2016)
    }
}
