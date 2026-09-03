package com.dani.lector.datos

/**
 * Que numeros te faltan en una serie, mirando SOLO lo que tienes.
 *
 * POR QUE ES UNA FUNCION PURA Y NO PREGUNTA A NADIE
 *
 * Lo primero que quiere saber cualquiera de una serie es si la tiene entera.
 * Y para los huecos de EN MEDIO no hace falta internet ni permiso de nadie: si
 * tienes el 11 y el 14, te faltan el 12 y el 13, y eso es verdad hoy y dentro
 * de diez años. Sale de la lista de numeros y ya esta.
 *
 * LO QUE A PROPOSITO NO HACE, que es tan importante como lo que hace:
 *
 * NO dice que te falten los de antes del primero ni los de despues del ultimo.
 * Si tienes del 5 al 20 no afirma que te falten el 1, 2, 3 y 4: puede que la
 * serie empiece en el 5, o en el 0, o que continue la numeracion de otra. Y no
 * tiene ni idea de si acaba en el 20 o en el 62.
 *
 * Eso hay que preguntarlo fuera, y por eso va aparte: aqui esta lo que se sabe
 * SEGURO, y encima de esto se pone lo que diga Comic Vine cuando lo diga.
 * Mezclar las dos cosas seria dar el mismo credito a un dato tuyo que a una
 * suposicion, que es como se acaba diciendo tonterias con mucho aplomo.
 */
object Huecos {

    data class Estado(
        /** Cuantos numeros distintos tienes. */
        val tienes: Int,
        /** El mas bajo y el mas alto, o null si no hay ninguno. */
        val primero: Int?,
        val ultimo: Int?,
        /** Los tramos que faltan ENTRE el primero y el ultimo. */
        val faltan: List<IntRange>
    ) {
        val cuantosFaltan: Int get() = faltan.sumOf { it.last - it.first + 1 }

        /** Seguida de principio a fin de lo que tienes. */
        val seguida: Boolean get() = tienes > 0 && faltan.isEmpty()
    }

    /**
     * [numeros] puede venir con repetidos y desordenado: es lo que sale de leer
     * los nombres de una carpeta, donde puede haber dos ficheros del mismo
     * numero. Un numero repetido no tapa ningun hueco, asi que cuenta una vez.
     */
    fun de(numeros: List<Int>): Estado {
        val n = numeros.toSortedSet().toList()
        if (n.isEmpty()) return Estado(0, null, null, emptyList())

        val faltan = mutableListOf<IntRange>()
        for (i in 0 until n.size - 1) {
            if (n[i + 1] - n[i] > 1) faltan.add((n[i] + 1)..(n[i + 1] - 1))
        }
        return Estado(n.size, n.first(), n.last(), faltan)
    }

    /** Cuantos tramos se enumeran antes de resumir. */
    private const val TRAMOS = 3

    /**
     * Los huecos en cristiano: "el 12, el 13 y del 50 al 58".
     *
     * Se enumeran tres tramos como mucho. Una serie a la que le falta media
     * coleccion daria una lista de treinta numeros que nadie va a leer, y lo
     * util ahi es el total.
     */
    fun texto(e: Estado): String {
        if (e.faltan.isEmpty()) return ""

        val trozos = e.faltan.take(TRAMOS).map {
            if (it.first == it.last) "el ${it.first}" else "del ${it.first} al ${it.last}"
        }
        val resto = e.faltan.size - trozos.size

        val cola = if (resto > 0) "$resto tramo${if (resto > 1) "s" else ""} más" else null
        val todos = trozos + listOfNotNull(cola)

        return buildString {
            append(todos.dropLast(1).joinToString(", "))
            if (todos.size > 1) append(" y ")
            append(todos.last())
        }
    }
}
