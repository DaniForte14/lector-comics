package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto

/**
 * Lo tuyo cruzado con lo que la fuente dice que existe.
 *
 * QUE APORTA SOBRE [Huecos], que ya mira lo que tienes: los extremos. Huecos
 * sabe que entre tu 11 y tu 14 faltan dos, pero no sabe si la serie empieza en
 * el 1 ni si acaba en el 62. Con la lista de la fuente se sabe **exactamente**
 * que falta, y eso incluye la cola: tienes hasta el 58 de una serie de 62.
 *
 * Sigue siendo una funcion PURA, y el tiempo entra por parametro igual que en
 * [Racha]: una funcion que llama al reloj no se puede probar dos veces con el
 * mismo resultado.
 */
object EstadoSerie {

    data class Resumen(
        /** Cuantos numeros de la serie tienes. */
        val tienes: Int,
        /** Cuantos dice la fuente que hay. */
        val total: Int,
        /** Los que faltan, en orden. */
        val faltan: List<Int>,
        /** Si la serie sigue publicandose. */
        val enEmision: Boolean,
        /** La portada mas reciente que conoce la fuente, "aaaa-mm-dd". */
        val ultima: String?
    ) {
        val completa: Boolean get() = total > 0 && faltan.isEmpty()
    }

    /**
     * [fechaCorte] en "aaaa-mm-dd": si el ultimo numero es de despues, la serie
     * cuenta como viva. Va por parametro y no se calcula aqui por lo dicho
     * arriba, y ademas asi el umbral se decide en un solo sitio.
     *
     * Se compara como TEXTO a proposito: una fecha ISO ordena igual de bien
     * alfabeticamente que como fecha, y asi esto no depende de ninguna
     * libreria de calendario ni de la zona horaria del movil.
     */
    fun de(
        mios: List<Int>,
        remotos: List<NumeroRemoto>,
        fechaCorte: String
    ): Resumen {
        // Solo los que tienen numero entendible. Un "1.MU" o un "Annual 2" no
        // se puede cruzar con el numero de un fichero tuyo, y contarlo como
        // que falta seria decirte que te falta algo que no sabemos ni que es.
        // distinct().sorted() y no toSortedSet(), que es de la JVM. Se usa
        // para contar, filtrar y saber el tamaño: una lista ordenada sin
        // repetidos vale igual.
        val esperados = remotos.mapNotNull { it.numero }.distinct().sorted()
        val tuyos = mios.toSet()

        val ultima = remotos.mapNotNull { it.fecha }.maxOrNull()

        return Resumen(
            tienes = esperados.count { it in tuyos },
            total = esperados.size,
            faltan = esperados.filter { it !in tuyos },
            enEmision = ultima != null && ultima > fechaCorte,
            ultima = ultima
        )
    }

    /**
     * Los que faltan, en cristiano, agrupando lo seguido.
     *
     * Se reaprovecha [Huecos.texto] en vez de escribir otra vez la misma
     * prosa: los numeros sueltos que faltan son tramos igual que los huecos.
     */
    fun texto(r: Resumen): String {
        if (r.faltan.isEmpty()) return ""
        val tramos = mutableListOf<IntRange>()
        var i = 0
        while (i < r.faltan.size) {
            var j = i
            while (j + 1 < r.faltan.size && r.faltan[j + 1] == r.faltan[j] + 1) j++
            tramos.add(r.faltan[i]..r.faltan[j])
            i = j + 1
        }
        return Huecos.texto(Huecos.Estado(0, null, null, tramos))
    }
}
