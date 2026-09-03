package com.dani.lector.datos

import kotlinx.datetime.Clock

/**
 * Cuentas sobre lo que llevas leido.
 *
 * TODO SALE DE TUS FICHEROS. Hasta el 02/09/2026 la mitad salia de las listas
 * —el catalogo de todo lo publicado de un personaje— y eso daba cifras como
 * "0 de 2411 numeros" de Batman, que ni son tuyas ni se pueden completar nunca.
 * Al quitar las listas, esto pasa a contar lo unico que la app sabe de verdad:
 * los comics que tienes y por cuales has pasado.
 *
 * El efecto de lado es que ya no hay dos numeros que no cuadran y que habia que
 * explicar en pantalla: aqui todo se cuenta contra la misma cosa.
 *
 * Funcion PURA: entran el progreso y la lista de comics, sale el resumen. El
 * reloj por parametro, igual que en [Racha].
 */
object Estadisticas {

    /**
     * Cuanto llevas de un trozo de tu biblioteca, contando SOLO lo que tienes.
     *
     * "87 de 120" es una cifra que se puede terminar. "15 de 796" no.
     *
     * [ruta] es la carpeta que representa, para poder bajar un nivel. [hoja] es
     * que ahi dentro ya no hay mas carpetas: es una serie y no hay a donde
     * bajar.
     */
    data class Avance(
        val nombre: String,
        val ruta: String,
        val leidos: Int,
        val total: Int,
        val hoja: Boolean
    ) {
        val porcentaje get() = if (total <= 0) 0 else leidos * 100 / total
    }

    data class Resumen(
        val terminados: Int,
        val empezados: Int,
        val paginas: Int,
        val comics: Int,
        /** Carpetas con comics dentro: cada una es una serie tuya. */
        val series: Int,
        val seriesCompletas: Int,
        val racha: Int,
        val dias: Int,
        val avance: List<Avance>
    )

    /**
     * El avance de cada carpeta que cuelga de [base], un nivel por debajo.
     *
     * SE NAVEGA POR NIVELES Y NO SE ADIVINA LA ESTRUCTURA. La primera version
     * cogia el primer tramo de la ruta y lo llamaba "personaje", dando por
     * hecho el arbol Personaje / Serie / numeros. En la biblioteca de Dani el
     * primer tramo resulta ser la EDITORIAL —"DC Comics", "Marvel"— asi que la
     * pantalla decia "DC Comics: 15 de 208", que es cierto y no sirve de nada.
     *
     * Con base = "" salen las carpetas de arriba; tocando una, se vuelve a
     * llamar con su ruta y salen las de dentro. Asi vale para el arbol que
     * tenga cada uno hoy y para el que monte mañana, que es el requisito que ya
     * puso Dani para lo de Comic Vine: o vale para cualquiera, o no vale.
     *
     * Los comics que estan SUELTOS en [base] se agrupan aparte en vez de
     * mezclarse con las carpetas: no son una serie y meterlos en cualquiera de
     * las otras seria mentir.
     */
    fun avance(
        progreso: Map<String, Marca>,
        comics: List<Comic>,
        base: String = ""
    ): List<Avance> {
        val raiz = base.trim('/')
        val dentro = comics.filter {
            val c = it.carpeta.trim('/')
            raiz.isBlank() || c == raiz || c.startsWith("$raiz/")
        }
        return dentro.groupBy { c ->
            val resto = c.carpeta.trim('/').removePrefix(raiz).trim('/')
            resto.substringBefore('/')            // "" si el comic esta suelto aqui
        }.map { (tramo, suyos) ->
            val ruta = if (tramo.isBlank()) raiz
                       else if (raiz.isBlank()) tramo else "$raiz/$tramo"
            Avance(
                nombre = tramo.ifBlank { "Sueltos aquí" },
                ruta = ruta,
                leidos = suyos.count { progreso[it.uri]?.terminado == true },
                total = suyos.size,
                // Hoja: todos sus comics estan directamente en su carpeta, o
                // sea que no hay ningun nivel mas al que bajar.
                hoja = tramo.isBlank() || suyos.all { it.carpeta.trim('/') == ruta }
            )
        }.sortedWith(
            // Por porcentaje y a igualdad por tamaño: con cuatro carpetas al 0%
            // el orden tiene que ser estable o la lista baila sola.
            compareByDescending<Avance> { it.porcentaje }.thenByDescending { it.total }
        )
    }

    fun calcular(
        progreso: Map<String, Marca>,
        comics: List<Comic>,
        ahora: Long = Clock.System.now().toEpochMilliseconds()
    ): Resumen {
        // Solo el progreso de comics que SIGUES teniendo. El fichero de
        // progreso guarda cosas de ficheros que has borrado, y contarlas daria
        // "142 leidos" en una biblioteca de 90.
        val mios = comics.map { it.uri }.toSet()
        val vivas = progreso.filterKeys { it in mios }

        val terminados = vivas.values.count { it.terminado }
        val empezados = vivas.values.count { !it.terminado && it.pagina > 0 }

        // De un comic a medias cuentan las paginas por las que has pasado, no
        // las que tiene: si no, empezar uno de cien sumaria cien.
        val paginas = vivas.values.sumOf {
            if (it.terminado) it.paginas else it.pagina + 1
        }

        val porCarpeta = comics.groupBy { it.carpeta.trim('/') }
        val completas = porCarpeta.count { (_, deLaCarpeta) ->
            deLaCarpeta.isNotEmpty() && deLaCarpeta.all { vivas[it.uri]?.terminado == true }
        }

        // La racha sale de TODO el progreso, tambien de comics que ya no
        // tienes: haber leido ese dia es un hecho y borrar el fichero despues
        // no deshace el dia.
        val tiempos = progreso.values.map { it.cuando }

        return Resumen(
            terminados = terminados,
            empezados = empezados,
            paginas = paginas,
            comics = comics.size,
            series = porCarpeta.size,
            seriesCompletas = completas,
            racha = Racha.de(tiempos, ahora),
            dias = Racha.diasTotales(tiempos),
            avance = avance(vivas, comics)
        )
    }
}
