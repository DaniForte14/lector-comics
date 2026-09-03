package com.dani.lector.datos

/**
 * Las edades del comic, para agrupar cuando la wiki no etiqueta eras.
 *
 * OJO CON ESTO: al reves que las eras de DC, las edades NO son un dato de
 * nadie. Son una convencion de aficionados y las fronteras se discuten: hay
 * quien pone el final del bronce en 1985 y quien lo pone en 1986, y la "edad
 * oscura" ni siquiera tiene consenso de nombre. Marvel no las etiqueta en su
 * wiki, asi que aqui se derivan del año de debut y se presentan como lo que
 * son: una manera comoda de partir el catalogo, no una verdad.
 *
 * Los tramos estan en un solo sitio para que cambiarlos sea cambiar una linea.
 */
object Edades {

    data class Tramo(val nombre: String, val desde: Int, val hasta: Int)

    val TRAMOS = listOf(
        // Empieza en 1900 y no en 1938 a proposito: la convencion arranca la
        // edad de oro en Action Comics #1 (1938), pero Detective Comics es de
        // 1937 y con el corte en 1938 se quedaba sin edad ninguna.
        Tramo("Edad de Oro", 1900, 1955),
        Tramo("Edad de Plata", 1956, 1969),
        Tramo("Edad de Bronce", 1970, 1984),
        Tramo("Edad Oscura", 1985, 1999),
        Tramo("Edad Moderna", 2000, 2100)
    )

    /** En que edad cae un año de debut. null si no hay año. */
    fun de(anio: Int?): String? {
        val a = anio ?: return null
        return TRAMOS.firstOrNull { a >= it.desde && a <= it.hasta }?.nombre
    }
}
