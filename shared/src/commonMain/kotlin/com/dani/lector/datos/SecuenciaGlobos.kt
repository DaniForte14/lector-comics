package com.dani.lector.datos

/** A donde lleva un toque, o una tecla de volumen, con los bocadillos encendidos. */
sealed class Paso {
    /** Seguir en la pagina, enseñando el globo [globo] o, si es null, la pagina entera. */
    data class EnPagina(val globo: Int?) : Paso()
    data object PaginaSiguiente : Paso()
    data object PaginaAnterior : Paso()
}

/**
 * La secuencia globo a globo, decidida por Dani el 11/09/2026 (`DISENO.md` §24):
 *
 * - Una pagina nueva se ve ENTERA primero, para situarse, como en Play Books.
 * - Adelante: pagina entera -> globo 0 -> ... -> el ultimo -> pagina siguiente.
 * - Atras: globo i -> i-1; globo 0 -> pagina entera; pagina entera -> pagina
 *   anterior.
 * - Una pagina sin globos, o sin calcular todavia, pasa como si no hubiera
 *   bocadillos.
 *
 * UN GLOBO QUE YA NO EXISTE cuenta como "pasado el ultimo". Pasa si la pagina
 * se recalcula con menos globos mientras se lee (`actual >= total`): adelante
 * lleva a la pagina siguiente, que es lo que habria pasado tras el ultimo, y
 * atras al ultimo que queda —o a la pagina entera si no queda ninguno—. Asi
 * no revienta, ningun indice apunta fuera de la lista, y un toque nunca se
 * salta mas de una pagina.
 */
object SecuenciaGlobos {

    /**
     * @param actual el globo que se ve, o null si se ve la pagina entera.
     * @param total cuantos globos tiene la pagina.
     */
    fun paso(actual: Int?, total: Int, adelante: Boolean): Paso {
        // La pagina entera es la posicion -1, los globos 0..total-1, y un
        // indice fuera de rango se queda en `total`, justo detras del ultimo.
        val i = if (actual == null) -1 else minOf(actual, total)
        return if (adelante) {
            if (i + 1 < total) Paso.EnPagina(i + 1) else Paso.PaginaSiguiente
        } else when {
            i < 0 -> Paso.PaginaAnterior
            i == 0 -> Paso.EnPagina(null)
            else -> Paso.EnPagina(i - 1)
        }
    }
}
