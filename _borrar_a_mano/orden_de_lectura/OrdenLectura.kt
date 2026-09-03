package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto

/**
 * El orden de lectura de un personaje, intercalando sus series.
 *
 * DE DONDE SALE EL ORDEN, que es lo unico que hace falta entender de aqui:
 * Comic Vine da la FECHA DE PORTADA de cada numero. Ordenando por esa fecha
 * los numeros de todas las series de un personaje, el intercalado sale solo,
 * porque es literalmente como se publicaron para leerse. No hace falta que
 * nadie opine, y eso saca esta funcion del terreno del modelo y la mete en el
 * de los datos, que es el principio rector del proyecto.
 *
 * POR QUE SE AGRUPA POR MES Y NO EN UNA LISTA SEGUIDA. Esto se escribio primero
 * como una lista plana ordenada por fecha, con desempate por serie para que los
 * numeros de una misma serie salieran seguidos. Al comprobar el primer test a
 * mano se vio que ese desempate no hace lo que promete: dos series mensuales
 * que corren a la vez comparten mes uno a uno, asi que la lista sale alternando
 * —Corps #1, GL #6, Corps #2, GL #7...— y agruparla en tramos daba quince
 * tramos de uno. El desempate solo ordenaba DENTRO de cada mes, y eso no junta
 * nada entre meses.
 *
 * Y la alternancia no es un fallo: es lo que significa el orden de publicacion.
 * Green Lantern #6 y Corps #1 estuvieron en la tienda el mismo mes y no hay
 * ningun dato que diga cual va antes. Asi que en vez de fabricar una secuencia
 * exacta que el dato no respalda, se agrupa POR MES y dentro del mes se juntan
 * los numeros de cada serie. Lo que se ve es lo que se sabe: "en agosto de 2005
 * salieron estos", y dentro de un mes da igual por cual empieces.
 *
 * LO QUE ESTO NO ES, y la pantalla lo dice: orden de publicacion no es orden de
 * lectura perfecto. Clava el 90%, pero hay tie-ins que van antes o despues de
 * lo que su fecha sugiere. Es una aproximacion buena que sabe decir de donde
 * sale cada dato, no una guia curada de foro.
 *
 * Funcion PURA, como [Huecos] y [EstadoSerie]: entra lo que tienes y lo que la
 * fuente dice que existe, sale la lista. Ni red, ni reloj, ni Android.
 */
object OrdenLectura {

    /**
     * Una serie tuya ya vinculada a la fuente.
     *
     * [mios] son los numeros que tienes en esa carpeta. Se pasa como conjunto y
     * no como lista de ficheros porque aqui no interesa el fichero: interesa si
     * el numero esta o no.
     */
    data class Entrada(
        val serie: String,
        val ruta: String,
        val numeros: List<NumeroRemoto>,
        val mios: Set<Int>
    )

    data class Numero(
        val serie: String,
        val ruta: String,
        val etiqueta: String,
        val numero: Int?,
        val fecha: String?,
        val titulo: String,
        /**
         * null NO es "no lo tienes": es "no se sabe".
         *
         * Un "Annual 2" o un "1.MU" no se puede cruzar con el numero que el
         * parser saca de tus ficheros, asi que decir que falta seria decirte
         * que te falta algo que no sabemos ni que es. Misma regla que en
         * [EstadoSerie], donde esos se quedan fuera del conteo.
         */
        val tienes: Boolean?
    )

    /** Numeros de la MISMA serie dentro de un mes. Casi siempre uno. */
    data class Tramo(
        val serie: String,
        val ruta: String,
        val numeros: List<Numero>
    ) {
        val tienes: Int get() = numeros.count { it.tienes == true }
        val faltan: Int get() = numeros.count { it.tienes == false }

        /** "#1–5", "#7", "Annual 2". Para el titulo de la fila. */
        val rango: String get() {
            val pri = numeros.first().etiqueta
            val ult = numeros.last().etiqueta
            val alm = if (pri.firstOrNull()?.isDigit() == true) "#" else ""
            return if (numeros.size == 1) "$alm$pri" else "$alm$pri–$ult"
        }
    }

    /** [clave] es "aaaa-mm", que ordena bien como texto y sirve de identidad. */
    data class Mes(
        val clave: String,
        val tramos: List<Tramo>
    ) {
        val etiqueta: String get() = mes(clave)
        val cuantos: Int get() = tramos.sumOf { it.numeros.size }
        val faltan: Int get() = tramos.sumOf { it.faltan }
    }

    /**
     * [sinFecha] son los numeros que la fuente no fecha. NO se colocan en
     * ningun sitio: sin fecha no hay forma de saber donde van, y ponerlos al
     * principio o al final por comodidad seria inventarse el dato. Van aparte
     * y la pantalla los enseña como lo que son.
     */
    data class Resultado(
        val meses: List<Mes>,
        val sinFecha: List<Numero>,
        val series: Int,
        val total: Int,
        val tienes: Int
    )

    fun de(entradas: List<Entrada>): Resultado {
        val todos = entradas.flatMap { e ->
            e.numeros.map { n ->
                Numero(
                    serie = e.serie,
                    ruta = e.ruta,
                    etiqueta = n.etiqueta,
                    numero = n.numero,
                    fecha = n.fecha?.takeIf { it.isNotBlank() },
                    titulo = n.nombre,
                    tienes = n.numero?.let { it in e.mios }
                )
            }
        }

        val (fechados, sinFecha) = todos.partition { it.fecha != null }

        val meses = fechados
            .groupBy { it.fecha!!.take(7) }
            .toSortedMap()
            .map { (clave, delMes) ->
                // Dentro del mes: por serie y por numero. El orden entre series
                // de un mismo mes es ARBITRARIO —alfabetico— y no se disimula:
                // la cabecera del mes esta justamente para que se vea que ahi
                // no hay secuencia que respetar.
                val ordenados = delMes.sortedWith(
                    compareBy({ it.serie }, { it.numero ?: Int.MAX_VALUE }, { it.etiqueta })
                )
                val tramos = mutableListOf<Tramo>()
                var actual = mutableListOf<Numero>()
                for (n in ordenados) {
                    if (actual.isNotEmpty() && actual.last().ruta != n.ruta) {
                        tramos.add(Tramo(actual.first().serie, actual.first().ruta, actual.toList()))
                        actual = mutableListOf()
                    }
                    actual.add(n)
                }
                if (actual.isNotEmpty())
                    tramos.add(Tramo(actual.first().serie, actual.first().ruta, actual.toList()))
                Mes(clave, tramos)
            }

        return Resultado(
            meses = meses,
            sinFecha = sinFecha,
            series = entradas.size,
            total = todos.size,
            tienes = todos.count { it.tienes == true }
        )
    }

    /** "agosto de 2007", para la cabecera del mes. Acepta "aaaa-mm" y la fecha entera. */
    fun mes(fecha: String?): String {
        val f = fecha ?: return "sin fecha"
        val a = f.substringBefore('-')
        val m = f.substringAfter('-', "").substringBefore('-').toIntOrNull() ?: return a
        val nombres = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        return "${nombres.getOrElse(m - 1) { "" }} de $a".trim()
    }
}
