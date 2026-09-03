package com.dani.lector.red

/** Un volumen tal como lo lista la base de datos. */
data class VolumenRemoto(
    val nombre: String,
    val anio: Int?,
    val numeros: Int,
    val editorial: String?,
    /**
     * Id en la fuente. Va con valor por defecto porque hasta ahora no hacia
     * falta: para el TODO bastaba el nombre y el recuento. Hace falta desde que
     * se piden los NUMEROS de una serie, que se piden por id y no por nombre.
     */
    val id: String = ""
)

/**
 * Un numero suelto de una serie.
 *
 * [etiqueta] es lo que dice la fuente tal cual —"1", "0", "1.MU", "Annual 2"—
 * y [numero] es esa etiqueta como entero cuando se puede. Se guardan las dos
 * porque hacen falta para cosas distintas: el entero para cruzarlo con lo que
 * tienes en la carpeta, y la etiqueta para poder enseñartela sin mentir.
 *
 * [fecha] es la de PORTADA, en "aaaa-mm-dd". Es la que ordena una lectura
 * intercalada entre series: los crossovers se publicaron para leerse asi.
 *
 * [venta] es el dia que salio a la venta de verdad, tambien "aaaa-mm-dd".
 *
 * SON DOS FECHAS DISTINTAS Y HACE FALTA GUARDAR LAS DOS. La de portada va dos o
 * tres meses por delante de la de venta —herencia de cuando le decia al
 * quiosquero hasta cuando dejar la grapa en el expositor— asi que para ORDENAR
 * vale la de portada, que la tienen todos los numeros, y para AVISAR de que ha
 * salido vale la de venta, que es la de verdad pero viene vacia a menudo:
 * Comic Vine empezo a guardarla tarde y en los numeros viejos suele faltar.
 *
 * Mezclarlas en un solo campo habria sido peor que no tener la segunda: la
 * lista de lectura saltaria dos meses cada vez que un numero tuviera una y el
 * siguiente la otra.
 */
data class NumeroRemoto(
    val etiqueta: String,
    val numero: Int?,
    val fecha: String?,
    val nombre: String = "",
    val venta: String? = null
)

/**
 * De donde salen los datos duros del TODO: que series existen, de que año son
 * y cuantos numeros tienen. Es una interfaz para poder cambiar de proveedor
 * sin tocar el resto de la app.
 */
interface FuenteComics {
    val nombre: String get() = "desconocida"
    val disponible: Boolean get() = true
    fun ultimoFallo(): String? = null

    /**
     * Cuantos volumenes dijo la fuente que habia y cuantos se han leido de
     * verdad, de la ultima consulta. Existe para que un tope de lectura no
     * pueda pasar por "esto es todo lo que hay": Batman devuelve 2217.
     */
    fun ultimoRecuento(): Pair<Int, Int>? = null

    suspend fun volumenesDe(personaje: String): List<VolumenRemoto>

    /**
     * Un volumen concreto, cuando ya sabemos cual queremos porque nos lo ha
     * dicho la wiki. Es lo que permite no barrer las 2217 series de Batman:
     * eliges una era y solo se pregunta por las suyas.
     */
    suspend fun volumen(nombre: String, anio: Int?): VolumenRemoto? = null

    /**
     * Buscar series por texto, para añadir una a mano.
     *
     * Una sola pagina de resultados: aqui no se busca completitud sino que
     * encuentres lo que ya sabes que quieres.
     */
    suspend fun buscarSeries(texto: String): List<VolumenRemoto> = emptyList()

    /**
     * Todos los numeros de un volumen, con su fecha de portada.
     *
     * Es la peticion que hace falta para saber si tienes una serie entera, si
     * sigue en emision y en que orden se intercala con las demas. Se pide por
     * id, asi que solo sirve para volumenes que hayan venido de esta fuente.
     */
    suspend fun numerosDe(volumenId: String): List<NumeroRemoto> = emptyList()
}

object FuenteVacia : FuenteComics {
    override val nombre = "ninguna"
    override val disponible = false
    override suspend fun volumenesDe(personaje: String) = emptyList<VolumenRemoto>()
}

/**
 * Cual de los candidatos es LA serie que buscamos.
 *
 * Esta fuera del cliente HTTP a proposito: es la parte con reglas y conviene
 * poder probarla con respuestas reales guardadas.
 *
 * Comprobado con "Absolute Batman", que devuelve 17 resultados: catorce son
 * tomos recopilatorios y ediciones sueltas cuyo nombre NO es exactamente el
 * buscado, uno es la edicion francesa de Urban Comics, y quedan los dos de DC.
 *
 * Las reglas, por orden:
 *  1. el nombre tiene que ser exactamente el mismo, normalizado
 *  2. solo la editorial mayoritaria, que echa fuera las ediciones extranjeras
 *  3. el año exacto; si no hay, un año de margen, porque Comic Vine fecha por
 *     portada y las wikis por publicacion y a fin de año no coinciden
 *  4. a igualdad, la que mas numeros tenga: entre la serie y un especial con
 *     el mismo nombre y año, queremos la serie
 */
fun elegirVolumen(candidatos: List<VolumenRemoto>, nombre: String, anio: Int?): VolumenRemoto? {
    val buscado = normalizar(nombre)
    val mismos = candidatos.filter { normalizar(it.nombre) == buscado }
    if (mismos.isEmpty()) return null

    val principal = mismos.mapNotNull { it.editorial }
        .groupingBy { it }.eachCount()
        .maxByOrNull { it.value }?.key
    val limpios = mismos
        .filter { principal == null || it.editorial == principal }
        .ifEmpty { mismos }

    if (anio == null) return limpios.maxByOrNull { it.numeros }

    return limpios.filter { it.anio == anio }.maxByOrNull { it.numeros }
        ?: limpios.filter { v ->
            val a = v.anio ?: return@filter false
            kotlin.math.abs(a - anio) <= 1
        }.maxByOrNull { it.numeros }
}

private fun normalizar(s: String) = s.lowercase()
    .replace("á", "a").replace("é", "e").replace("í", "i")
    .replace("ó", "o").replace("ú", "u").replace("ü", "u")
    .replace(Regex("[^a-z0-9]"), "")
