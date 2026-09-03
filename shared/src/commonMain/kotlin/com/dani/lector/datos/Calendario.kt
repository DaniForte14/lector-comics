package com.dani.lector.datos

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Que leiste cada dia del mes.
 *
 * DE DONDE SALE Y QUE NO PUEDE SABER. La unica fecha que guarda la app es
 * [Marca.cuando], que es **la ultima vez que tocaste ese comic**. Asi que un
 * tomo que te has leido en tres tardes aparece solo el ultimo dia, no los tres.
 * Para lo otro habria que guardar una fila por sesion de lectura, que es otro
 * fichero y otra decision; esto se hace con lo que ya hay.
 *
 * Se dice aqui y no en la pantalla: el calendario se entiende solo, y la app no
 * esta para explicar como funciona por dentro (ver las reglas del texto en
 * LECTOR-COMICS-DISENO.md).
 *
 * Funcion PURA, como todo lo que decide algo en este proyecto. El calendario
 * entra por parametro; el reloj no se toca aqui dentro.
 */
object Calendario {

    /**
     * Un comic y que leiste de el ese dia.
     *
     * Se guardan las paginas Y el tramo: "13 paginas" dice cuanto, "de la 4 a
     * la 16" dice DONDE, y eso es lo que deja reconstruir la sesion mirando el
     * calendario. Los numeros salen en base 1, como los ve el lector.
     */
    data class Leido(val comic: Comic, val paginas: Int, val desde: Int, val hasta: Int) {
        /** "págs. 4-16", o "pág. 4" si ese dia solo caiste una. */
        val tramo: String get() =
            if (desde == hasta) "pág. ${desde + 1}" else "págs. ${desde + 1}-${hasta + 1}"
    }

    /**
     * Lo que leiste cada dia de ese mes, por numero de dia.
     *
     * SALE DEL DIARIO ([Sesiones]) Y NO DE [Marca]. La marca guarda una sola
     * fecha por comic —la ultima vez que lo tocaste— asi que un tomo leido
     * lunes, miercoles y viernes salia solo el viernes. El diario guarda una
     * fila por comic y dia, que es justo lo que hace falta para que salga en
     * los tres.
     *
     * Solo entran los comics que SIGUES teniendo: el diario guarda sesiones de
     * ficheros borrados, y pintar la portada de algo que ya no esta es enseñar
     * un hueco gris sin explicacion.
     *
     * Dentro de cada dia, lo ultimo primero: la portada de la casilla es lo
     * ultimo que leiste ese dia.
     */
    fun porDia(
        sesiones: List<Sesion>,
        comics: List<Comic>,
        anio: Int,
        mes: Int
    ): Map<Int, List<Leido>> {
        val porUri = comics.associateBy { it.uri }
        val prefijo = "${dosDigitos(anio, 4)}-${dosDigitos(mes)}-"
        return sesiones
            .filter { it.dia.startsWith(prefijo) }
            .mapNotNull { s -> porUri[s.uri]?.let { s to it } }
            .sortedByDescending { (s, _) -> s.cuando }
            .groupBy({ (s, _) -> s.dia.substringAfterLast('-').toInt() },
                     { (s, c) -> Leido(c, s.paginas, s.desde, s.hasta) })
    }

    /** El dia del mes como fecha completa: "2026-09-03". */
    fun clave(anio: Int, mes: Int, dia: Int) =
        "${dosDigitos(anio, 4)}-${dosDigitos(mes)}-${dosDigitos(dia)}"

    /**
     * Rellenar con ceros a mano. `String.format` es de la JVM y no existe en
     * Kotlin/Native, y esto es una clave de fichero: si un dia sale "2026-9-3"
     * en vez de "2026-09-03", el calendario deja de encontrar lo leido y no da
     * ningun error.
     */
    private fun dosDigitos(n: Int, ancho: Int = 2) = n.toString().padStart(ancho, '0')

    /** La fecha española de un instante. Misma zona que todo lo demas. */
    fun fecha(cuando: Long): LocalDate =
        Instant.fromEpochMilliseconds(cuando).toLocalDateTime(Novedades.ZONA).date

    /**
     * Las semanas del mes como filas de siete, con null en los huecos.
     *
     * EMPIEZA EN LUNES, que es como se lee un calendario en España. Va aqui y no
     * en la pantalla porque es aritmetica con dos casos de borde —el mes que
     * empieza en domingo y el que necesita seis filas— y eso se prueba mejor
     * fuera de Compose.
     */
    fun semanas(anio: Int, mes: Int): List<List<Int?>> {
        val primero = LocalDate(anio, mes, 1)
        // kotlinx-datetime no tiene lengthOfMonth: el ultimo dia del mes es
        // el dia anterior al primero del siguiente.
        val dias = primero.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
        // getValue() da 1 el lunes y 7 el domingo, que es justo lo que hace
        // falta: los huecos de delante son ese valor menos uno.
        val huecos = primero.dayOfWeek.isoDayNumber - 1
        val celdas = List(huecos) { null } + (1..dias).toList()
        return celdas.chunked(7).map { fila -> fila + List(7 - fila.size) { null } }
    }

    val DIAS = listOf("L", "M", "X", "J", "V", "S", "D")

    private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** "Septiembre" con mayuscula, para el titulo del mes. */
    fun nombreMes(mes: Int): String =
        MESES.getOrElse(mes - 1) { "" }.replaceFirstChar { it.uppercase() }
}
