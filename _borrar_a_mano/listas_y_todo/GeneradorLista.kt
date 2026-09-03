package com.dani.lector.datos

import android.content.Context
import com.dani.lector.red.Enriquecedor
import com.dani.lector.red.FuenteComics
import com.dani.lector.red.FuenteVolumenes
import com.dani.lector.red.SerieWiki
import com.dani.lector.red.VolumenRemoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Crea el TODO de un personaje.
 *
 * Reparto de trabajo:
 *  - COMIC VINE pone los datos: que series existen, de que año y cuantos numeros
 *  - GEMINI pone el criterio: cuales importan y por que
 *
 * Y al final se comprueba: si el modelo nombra una serie que no estaba en la
 * lista, se descarta. Asi no puede colar nada inventado.
 */
object GeneradorLista {

    data class Resultado(val lista: Lista?, val mensaje: String)

    /**
     * Las eras de un personaje, para elegir antes de crear nada.
     *
     * Sale del indice curado de la wiki. Si la wiki no conoce al personaje esto
     * viene vacio y queda el camino de siempre, [crear], que barre Comic Vine
     * por nombre.
     */
    suspend fun eras(wiki: FuenteVolumenes, personaje: String): List<OpcionEra> =
        withContext(Dispatchers.IO) { Eras.de(wiki.seriesDe(personaje)) }

    /**
     * Crea el TODO a partir de las series que hayas elegido.
     *
     * La diferencia con [crear] no es de estilo, es de escala: alli se le pide
     * a Comic Vine "todo lo que se llame Batman" y son 2217 volumenes, 23
     * peticiones y cuatro minutos. Aqui se le pregunta SOLO por las series
     * elegidas, una peticion cada una, porque la wiki ya nos ha dicho cuales
     * son y de que año.
     */
    suspend fun crearDeEras(
        fuente: FuenteComics,
        enriquecedor: Enriquecedor,
        listas: Listas,
        personaje: String,
        elegidas: List<SerieWiki>,
        avance: (String) -> Unit = {}
    ): Resultado = withContext(Dispatchers.IO) {

        if (elegidas.isEmpty())
            return@withContext Resultado(null, "No has elegido ninguna serie.")

        val conNumeros = mutableListOf<Triple<SerieWiki, VolumenRemoto?, Boolean>>()
        elegidas.forEachIndexed { i, s ->
            avance("Buscando números · ${i + 1} de ${elegidas.size} · ${s.nombre}")
            val v = if (fuente.disponible)
                runCatching { fuente.volumen(s.nombre, s.anio) }.getOrNull() else null
            // Si la fuente respondio y aun asi no hay volumen, es que no la
            // tiene. Se apunta ya, aqui, para que el boton de reparar no vuelva
            // a preguntar por ella en cada pasada.
            val descartada = v == null && fuente.disponible && fuente.ultimoFallo() == null
            conNumeros.add(Triple(s, v, descartada))
        }

        val lineas = conNumeros.map { (s, v, _) ->
            "${s.nombre} | ${s.anio ?: "sin año"} | ${v?.numeros ?: 0} números"
        }
        val recibidos = if (enriquecedor.disponible) {
            avance("Pidiendo criterio a Gemini · ${lineas.size} series")
            enriquecedor.valorar(personaje, lineas) { avance(it) }
        } else emptyList()

        val porNombre = recibidos.groupBy { Parser.normalizar(it.nombre) }

        val series = conNumeros.map { (s, v, descartada) ->
            val c = porNombre[Parser.normalizar(s.nombre)]?.minByOrNull {
                kotlin.math.abs((it.anio ?: 0) - (s.anio ?: 0))
            }
            Serie(
                id = Parser.normalizar(personaje) + "-" +
                     Parser.normalizar(s.nombre) + "-" + (s.anio ?: 0),
                nombre = s.nombre,
                anio = s.anio,
                anioFin = s.anioFin,
                numeros = v?.numeros ?: 0,
                peso = c?.peso ?: "OPCIONAL",
                contexto = c?.contexto ?: "",
                editorial = v?.editorial,
                // la etiqueta de la wiki manda; si no la hay, la edad por año
                era = s.eras.firstOrNull() ?: Edades.de(s.anio),
                volumen = s.volumen,
                noEncontrada = descartada
            )
        }.sortedWith(compareBy({ ordenPeso(it.peso) }, { it.anio ?: 9999 }))

        // SUMA, no reemplaza: esta misma pantalla sirve para crear la lista y
        // para añadirle una etapa mas despues, y lo segundo no puede borrar lo
        // que ya tenias marcado.
        val previas = listas.de(personaje)?.series.orEmpty()
        val nuevas = series.filterNot { n -> previas.any { it.id == n.id } }
        val todas = (previas + nuevas)
            .sortedWith(compareBy({ ordenPeso(it.peso) }, { it.anio ?: 9999 }))

        val lista = Lista(
            personaje.trim(),
            todas.firstNotNullOfOrNull { it.editorial },
            todas
        )
        listas.guardar(lista)

        val sinNumeros = nuevas.count { it.numeros == 0 }
        Resultado(lista, buildString {
            append(if (previas.isEmpty()) "Lista creada: ${nuevas.size} series"
                   else "Añadidas ${nuevas.size} series de ${series.size}")
            if (sinNumeros > 0) {
                append(", $sinNumeros sin número de grapas")
                // distinguir "no existe" de "me han cortado el grifo" importa:
                // lo primero no tiene arreglo y lo segundo se arregla esperando
                val f = fuente.ultimoFallo()
                append(when {
                    f == null -> " (Comic Vine no las encuentra por nombre y año)"
                    f.startsWith("420") -> " porque Comic Vine ha cortado por " +
                        "exceso de peticiones. Espera un rato y usa «buscar los " +
                        "números que faltan» en la lista"
                    else -> " ($f)"
                })
            }
            when {
                !enriquecedor.disponible ->
                    append(". Sin clave de Gemini todas salen como OPCIONAL.")
                recibidos.isEmpty() ->
                    append(". Gemini no ha devuelto criterio (" +
                           "${enriquecedor.ultimoFallo() ?: "sin motivo"}).")
                else -> append(".")
            }
        })
    }

    suspend fun crear(
        ctx: Context,
        fuente: FuenteComics,
        enriquecedor: Enriquecedor,
        listas: Listas,          // el MISMO almacen que usa la pantalla
        personaje: String,
        avance: (String) -> Unit = {}
    ): Resultado = withContext(Dispatchers.IO) {

        if (!fuente.disponible)
            return@withContext Resultado(null, "Falta la clave de Comic Vine en los ajustes.")

        avance("Buscando series de $personaje...")
        val vols = fuente.volumenesDe(personaje)
        if (vols.isEmpty())
            return@withContext Resultado(null,
                "No he encontrado ninguna serie de $personaje. " +
                (fuente.ultimoFallo()?.let { "Motivo: $it" } ?: "Prueba con el nombre en inglés."))

        // Gemini tarda bastante con listas largas y sin avisos parece colgado.
        avance("${vols.size} series encontradas")

        // Si la fuente tenia mas de las que ha podido leer, hay que decirlo:
        // con un personaje grande faltan justo las mas recientes.
        val (hay, leidas) = fuente.ultimoRecuento() ?: (0 to 0)
        val truncado = hay > leidas

        val lineas = vols.map { v ->
            "${v.nombre} | ${v.anio ?: "sin año"} | ${v.numeros} números" +
            (v.editorial?.let { " | $it" } ?: "")
        }
        val recibidos = if (enriquecedor.disponible) {
            avance("Pidiendo criterio a Gemini · ${lineas.size} series")
            val r = enriquecedor.valorar(personaje, lineas) { texto -> avance(texto) }
            avance("Recibidas ${r.size} valoraciones")
            r
        } else emptyList()

        // Dos indices: uno exacto (nombre + año) y otro solo por nombre. El
        // modelo a veces devuelve el año mal o lo omite, y con un solo indice
        // esas series se quedaban sin criterio y salian todas como OPCIONAL.
        val porNombreAnio = recibidos.associateBy { Parser.normalizar(it.nombre) + (it.anio ?: "") }
        val porNombre = recibidos.groupBy { Parser.normalizar(it.nombre) }

        // Se construye sobre los volumenes REALES, no sobre lo que dijo el
        // modelo: si el modelo se invento una serie, aqui no aparece.
        val series = vols.map { v ->
            val clave = Parser.normalizar(v.nombre)
            val c = porNombreAnio[clave + (v.anio ?: "")]
                ?: porNombreAnio[clave + ""]
                // si solo hay una valoracion con ese nombre, es esa
                ?: porNombre[clave]?.singleOrNull()
                // si hay varias, la del año mas cercano
                ?: porNombre[clave]?.minByOrNull {
                    kotlin.math.abs((it.anio ?: 0) - (v.anio ?: 0))
                }
            Serie(
                id = Parser.normalizar(personaje) + "-" +
                     Parser.normalizar(v.nombre) + "-" + (v.anio ?: 0),
                nombre = v.nombre,
                anio = v.anio,
                anioFin = null,
                numeros = v.numeros,
                peso = c?.peso ?: "OPCIONAL",
                contexto = c?.contexto ?: "",
                editorial = v.editorial
            )
        }.sortedWith(compareBy({ ordenPeso(it.peso) }, { it.anio ?: 9999 }))

        avance("Montando la lista...")
        val editorial = vols.firstNotNullOfOrNull { it.editorial }
        val lista = Lista(personaje.trim(), editorial, series)
        // OJO: tiene que ser el almacen del ViewModel. Con una instancia nueva
        // el fichero se escribe pero la pantalla sigue viendo su copia vieja
        // en memoria, y parece que no ha pasado nada.
        listas.guardar(lista)

        val sinCriterio = series.count { it.contexto.isBlank() }
        Resultado(lista, buildString {
            append("Lista creada: ${series.size} series")
            if (truncado) append(" de las $hay que tiene Comic Vine: se leen las " +
                                 "$leidas primeras, así que pueden faltar las más nuevas")
            when {
                !enriquecedor.disponible ->
                    append(". Sin clave de Gemini no hay criterio: todas salen como OPCIONAL.")
                recibidos.isEmpty() ->
                    append(". Gemini no ha devuelto criterio (${enriquecedor.ultimoFallo() ?: "sin motivo"}), " +
                           "así que todas salen como OPCIONAL. Prueba otra vez.")
                sinCriterio > 0 ->
                    append(", $sinCriterio sin explicación de ${recibidos.size} valoraciones recibidas.")
                else -> append(", todas valoradas.")
            }
        })
    }

    /** Interno y no privado porque [Listas.reordenar] tambien recoloca por peso. */
    internal fun ordenPeso(p: String) = when (p.uppercase()) {
        "EMPIEZA AQUI", "EMPIEZA AQUÍ" -> 0
        "IMPRESCINDIBLE" -> 1
        "RECOMENDABLE" -> 2
        "OPCIONAL" -> 3
        else -> 4
    }
}
