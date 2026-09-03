package com.dani.lector.red

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lo que el modelo aporta sobre una serie: cuanto importa y por que. */
data class Criterio(
    val nombre: String,
    val anio: Int?,
    val peso: String,
    val contexto: String
)

/**
 * El modelo NO pone datos, solo criterio.
 *
 * Los volumenes, años y numeros salen de Comic Vine. Al modelo se le da esa
 * lista y se le pide unicamente que diga cuales importan y por que. Asi no
 * puede inventarse cifras, que es justo lo que recuerda mal.
 */
interface Enriquecedor {
    val disponible: Boolean get() = true
    fun ultimoFallo(): String? = null
    fun modeloActual(): String = "-"

    suspend fun valorar(
        personaje: String,
        volumenes: List<String>,
        avance: (String) -> Unit = {}
    ): List<Criterio>

    /**
     * Cual de dos series es mejor puerta de entrada. Devuelve "A" o "B".
     *
     * Es una COMPARACION a proposito, no una nota. Preguntarle a un modelo
     * "¿esta serie es la puerta de entrada?" le obliga a inventarse un umbral
     * absoluto sobre una bibliografia que no tiene delante; preguntarle cual de
     * dos entra mejor es una pregunta que si puede responder.
     */
    suspend fun mejorEntrada(personaje: String, opcionA: String, opcionB: String): String? = null

    /**
     * Criterio de UNA serie suelta, para las que añades a mano.
     *
     * No es lo mismo que [valorar] con una sola: aquel reparte un unico
     * "EMPIEZA AQUI" entre todas las series del personaje, y si solo ve una la
     * corona a ella por descarte. Aqui se le prohibe expresamente ese peso,
     * porque decidir cual es la puerta de entrada exige ver la bibliografia
     * entera y aqui solo hay una serie delante.
     */
    suspend fun valorarUna(personaje: String, volumen: String): Criterio? = null

    /**
     * Si es buen momento para empezar a leer por cada etapa. Devuelve
     * etapa -> una frase.
     *
     * Esto es OPINION y en la pantalla va etiquetado como tal, separado de lo
     * que se puede contar (cuantas series, si todas empiezan en el numero 1).
     * Al modelo se le prohibe expresamente citar numeros, fechas o contar la
     * trama: solo puede decir si se entra bien o mal y por que.
     */
    suspend fun veredictoEras(personaje: String, eras: List<String>): Map<String, String> =
        emptyMap()
}

object EnriquecedorVacio : Enriquecedor {
    override val disponible = false
    override suspend fun valorar(
        personaje: String, volumenes: List<String>, avance: (String) -> Unit
    ) = emptyList<Criterio>()
}

class Gemini(
    private val apiKey: String,
    private val modelo: String = "gemini-flash-latest"
) : Enriquecedor {

    private val TANDA = 25

    @Volatile private var fallo: String? = null
    override fun ultimoFallo() = fallo
    override fun modeloActual() = modelo

    /**
     * Se trocea la peticion: con 198 series de golpe la respuesta se corta por
     * longitud y no llega NADA, asi que todas acababan como "OPCIONAL".
     * En tandas de 25 cabe de sobra.
     */
    override suspend fun valorar(
        personaje: String, volumenes: List<String>, avance: (String) -> Unit
    ): List<Criterio> = withContext(Dispatchers.IO) {
        if (volumenes.isEmpty()) return@withContext emptyList()
        if (volumenes.size > TANDA) {
            val tandas = volumenes.chunked(TANDA)
            val out = mutableListOf<Criterio>()
            tandas.forEachIndexed { i, trozo ->
                avance("Tanda ${i + 1} de ${tandas.size} · ${out.size} valoradas")
                out.addAll(unaTanda(personaje, trozo, i == 0) { avance(it) })
            }
            return@withContext out
        }
        return@withContext unaTanda(personaje, volumenes, true, avance)
    }

    private suspend fun unaTanda(
        personaje: String,
        volumenes: List<String>,
        permiteEmpiezaAqui: Boolean,
        avance: (String) -> Unit
    ): List<Criterio> = withContext(Dispatchers.IO) {

        val prompt = """
            Eres un experto en comics. Te doy las series REALES de $personaje
            sacadas de una base de datos, con su año y cuantos numeros tiene cada una:

            ${volumenes.joinToString("\n") { "- $it" }}

            Para CADA UNA devuelve su importancia y una explicacion de contexto.

            La explicacion tiene que decir que pasa editorialmente y si conviene
            leerla. Ejemplo del tono que quiero:
            "Tras Crisis Infinita, DC reinicia la coleccion y Geoff Johns
            recupera a Hal Jordan. Es el mejor punto de entrada moderno: no
            necesitas nada anterior."

            Devuelve SOLO un array JSON, sin texto alrededor ni marcas de codigo.
            Un objeto por serie, con estas claves exactas:
            "nombre"    el nombre de la serie tal como te lo he dado
            "anio"      su año de inicio, numero
            "peso"      uno de: EMPIEZA AQUI, IMPRESCINDIBLE, RECOMENDABLE, OPCIONAL, SALTABLE
            "contexto"  dos o tres frases en español

            ${if (permiteEmpiezaAqui) "Solo UNA serie lleva EMPIEZA AQUI: la mejor puerta de entrada hoy."
              else "NINGUNA lleva EMPIEZA AQUI en esta tanda."}
            No te inventes series que no esten en la lista.
            Devuelve EXACTAMENTE ${volumenes.size} objetos, uno por cada serie.
        """.trimIndent()

        val texto = preguntar(prompt, avance) ?: return@withContext emptyList()
        avance("Leyendo la respuesta...")

        runCatching {
            val limpio = texto.substringAfter("[", "").substringBeforeLast("]", "")
            val arr = JSONArray("[$limpio]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Criterio(
                    nombre = o.optString("nombre"),
                    anio = o.optInt("anio").takeIf { it > 0 },
                    peso = o.optString("peso", "OPCIONAL").uppercase(),
                    contexto = o.optString("contexto")
                )
            }.filter { it.nombre.isNotBlank() }
        }.getOrElse { emptyList() }
    }

    override suspend fun veredictoEras(
        personaje: String, eras: List<String>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (eras.isEmpty()) return@withContext emptyMap()

        val prompt = """
            Eres un experto en comics. Estas son las etapas de $personaje, con
            datos sacados de una base de datos:

            ${eras.joinToString("\n") { "- $it" }}

            Para CADA UNA di si es buen momento para empezar a leer al personaje
            por ahi, y por que, en UNA sola frase.

            Reglas estrictas:
            - NO cites numeros de grapa, ni fechas, ni titulos que no te haya dado.
            - NO cuentes lo que pasa en la historia.
            - Habla solo de si se entra bien o mal: si hace falta leer algo antes,
              si arranca de cero, si es continuidad cerrada o abierta.

            Devuelve SOLO un array JSON, sin texto alrededor ni marcas de codigo,
            con estas claves exactas:
            "era"        el nombre de la etapa tal como te lo he dado
            "veredicto"  una frase en español
        """.trimIndent()

        val texto = preguntar(prompt) {} ?: return@withContext emptyMap()

        runCatching {
            val limpio = texto.substringAfter("[", "").substringBeforeLast("]", "")
            val arr = JSONArray("[$limpio]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val e = o.optString("era")
                val v = o.optString("veredicto")
                if (e.isBlank() || v.isBlank()) null else e to v
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    override suspend fun valorarUna(personaje: String, volumen: String): Criterio? =
        withContext(Dispatchers.IO) {
            val prompt = """
                Eres un experto en comics. Esta es UNA serie de $personaje, con
                datos sacados de una base de datos:

                - $volumen

                Dime su importancia dentro de la bibliografia del personaje y una
                explicacion de contexto: que pasa editorialmente en ella y si
                conviene leerla.

                Devuelve SOLO un objeto JSON, sin texto alrededor ni marcas de
                codigo, con estas claves exactas:
                "peso"      uno de: IMPRESCINDIBLE, RECOMENDABLE, OPCIONAL, SALTABLE
                "contexto"  dos o tres frases en español

                NO uses EMPIEZA AQUI: cual es la puerta de entrada solo se decide
                comparando toda la bibliografia, y aqui solo ves una serie.
                No te inventes datos que no te haya dado. Si el nombre indica que
                es un recopilatorio o una reedicion, dilo en el contexto.
            """.trimIndent()

            val texto = preguntar(prompt) {} ?: return@withContext null

            runCatching {
                val limpio = texto.substringAfter("{", "").substringBeforeLast("}", "")
                val o = JSONObject("{$limpio}")
                Criterio(
                    nombre = volumen.substringBefore("|").trim(),
                    anio = null,
                    peso = o.optString("peso", "OPCIONAL").uppercase(),
                    contexto = o.optString("contexto")
                )
            }.getOrNull()?.takeIf { it.contexto.isNotBlank() }
        }

    override suspend fun mejorEntrada(
        personaje: String, opcionA: String, opcionB: String
    ): String? = withContext(Dispatchers.IO) {
        val prompt = """
            Eres un experto en comics. Para alguien que empieza a leer a
            $personaje hoy, sin haber leido nada antes, ¿cual de estas dos entra
            mejor?

            A) $opcionA
            B) $opcionB

            Piensa en si hace falta contexto previo, si arranca de cero y si
            engancha. Responde SOLO con una letra, A o B, sin nada mas.
        """.trimIndent()

        val texto = preguntar(prompt) {} ?: return@withContext null
        texto.trim().uppercase().firstOrNull { it == 'A' || it == 'B' }?.toString()
    }

    /**
     * Una pregunta al modelo, con reintentos y cambio de modelo.
     *
     * Los modelos gratis se saturan (503) o tardan, y ademas los jubilan cada
     * pocos meses: si el preferido no responde se prueba con otro de la familia.
     */
    private suspend fun preguntar(prompt: String, avance: (String) -> Unit): String? {
        val cuerpo = JSONObject().put("contents", JSONArray().put(
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        ))
        val candidatos = listOf(modelo, "gemini-2.5-flash", "gemini-flash-lite-latest")
        for (m in candidatos.distinct()) {
            for (intento in 0..2) {
                avance(if (intento == 0) "Preguntando a $m..."
                       else "Reintento ${intento + 1} con $m (${fallo ?: "sin respuesta"})")
                val t = postear(
                    "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent",
                    cuerpo
                )
                if (t != null) return t
                val f = fallo.orEmpty()
                if (!f.startsWith("503") && !f.startsWith("429") && !f.contains("Timeout")) break
                avance("Esperando ${(2 shl intento)} s · $f")
                delay(2000L shl intento)
            }
        }
        return null
    }

    /** Que modelos acepta tu clave. Los nombres cambian cada pocos meses. */
    suspend fun modelosDisponibles(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/models")
                .openConnection() as HttpURLConnection).apply {
                setRequestProperty("x-goog-api-key", apiKey)
                connectTimeout = 15_000; readTimeout = 20_000
            }
            if (c.responseCode !in 200..299) return@runCatching emptyList<String>()
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val arr = j.optJSONArray("models") ?: return@runCatching emptyList<String>()
            (0 until arr.length()).mapNotNull { i ->
                val m = arr.getJSONObject(i)
                val g = m.optJSONArray("supportedGenerationMethods")
                val ok = g?.let { (0 until it.length()).any { k -> it.getString(k) == "generateContent" } } ?: false
                if (ok) m.optString("name").removePrefix("models/").ifBlank { null } else null
            }
        }.getOrElse { emptyList() }
    }

    private fun postear(url: String, cuerpo: JSONObject): String? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)   // cabecera, no en la URL
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        c.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
        val codigo = c.responseCode
        if (codigo !in 200..299) {
            val detalle = runCatching {
                c.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
            }.getOrNull()
            fallo = when (codigo) {
                400 -> "400: clave con formato invalido (las de AI Studio empiezan por AIza)"
                403 -> "403: clave rechazada o sin permiso"
                429 -> "429: limite de peticiones, espera un rato"
                503 -> "503: el modelo esta saturado ahora mismo"
                else -> "$codigo: ${detalle ?: "error"}"
            }
            null
        } else {
            fallo = null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            j.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text")
        }
    } catch (e: Exception) {
        fallo = "sin conexion: ${e.javaClass.simpleName}"
        null
    }
}
