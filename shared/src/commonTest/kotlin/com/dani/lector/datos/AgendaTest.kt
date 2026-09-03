package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlinx.datetime.LocalDate

/** La agenda de "qué sale próximamente" y cómo se dice la fecha. */
class AgendaTest {

    private val hoy: LocalDate = LocalDate(2026, 9, 3)

    /** Con fecha de venta REAL, que es la que no se estima. */
    private fun num(etiqueta: String, venta: String?) =
        NumeroRemoto(etiqueta = etiqueta, numero = etiqueta.toIntOrNull(),
                     fecha = null, venta = venta)

    private fun ficha(nombre: String, nums: List<NumeroRemoto>, seguida: Boolean = true) =
        Ficha(
            ruta = nombre, volumenId = "1", nombre = nombre, anio = 2024,
            numeros = nums, cuando = 0L, seguida = seguida)

    @Test fun `lo que antes llega, primero, mezclando series`() {
        val l = Novedades.agenda(listOf(
            ficha("Batman", listOf(num("12", "2026-10-20"), num("13", "2026-11-20"))),
            ficha("Flash", listOf(num("7", "2026-09-30")))
        ), hoy)
        assertEquals(listOf("Flash" to "#7", "Batman" to "#12", "Batman" to "#13"),
            l.map { it.serie to it.etiqueta })
    }

    @Test fun `las series que no sigues no salen`() {
        val l = Novedades.agenda(listOf(
            ficha("Batman", listOf(num("12", "2026-10-20"))),
            ficha("Flash", listOf(num("7", "2026-09-30")), seguida = false)
        ), hoy)
        assertEquals(listOf("Batman"), l.map { it.serie })
    }

    @Test fun `lo que ya ha salido no es lo que viene`() {
        val l = Novedades.agenda(listOf(
            ficha("Batman", listOf(num("11", "2026-08-01"), num("12", "2026-10-20")))
        ), hoy)
        assertEquals(listOf("#12"), l.map { it.etiqueta })
    }

    /** Sin fecha no se puede decir cuándo sale, y colocarlo sería inventárselo. */
    @Test fun `sin fecha, fuera`() {
        val l = Novedades.agenda(listOf(
            ficha("Batman", listOf(num("12", null), num("13", "2026-10-20")))
        ), hoy)
        assertEquals(listOf("#13"), l.map { it.etiqueta })
    }

    @Test fun `el tope corta por el final, que es lo mas lejano`() {
        val l = Novedades.agenda(listOf(
            ficha("Batman", listOf(
                num("1", "2026-10-01"), num("2", "2026-10-08"), num("3", "2026-10-15")))
        ), hoy, tope = 2)
        assertEquals(listOf("#1", "#2"), l.map { it.etiqueta })
    }

    /** Sin desempate, dos del mismo día pueden salir en otro orden cada vez. */
    @Test fun `a igual fecha, por nombre de serie`() {
        val l = Novedades.agenda(listOf(
            ficha("Zatanna", listOf(num("3", "2026-10-07"))),
            ficha("Aquaman", listOf(num("4", "2026-10-07")))
        ), hoy)
        assertEquals(listOf("Aquaman", "Zatanna"), l.map { it.serie })
    }

    /** Un annual no lleva número: la etiqueta va tal cual, sin almohadilla. */
    @Test fun `lo que no empieza por cifra no lleva almohadilla`() {
        val l = Novedades.agenda(listOf(
            ficha("Batman", listOf(num("Annual 2", "2026-10-20")))
        ), hoy)
        assertEquals(listOf("Annual 2"), l.map { it.etiqueta })
    }

    // ───────────────────── cómo se dice la fecha ─────────────────────

    @Test fun `lo cercano en dias y lo lejano en fecha`() {
        assertEquals("mañana", Novedades.cuandoSale(LocalDate(2026, 9, 4), hoy))
        assertEquals("en 3 días", Novedades.cuandoSale(LocalDate(2026, 9, 6), hoy))
        assertEquals("en 6 días", Novedades.cuandoSale(LocalDate(2026, 9, 9), hoy))
        assertEquals("el 10 de septiembre", Novedades.cuandoSale(LocalDate(2026, 9, 10), hoy))
    }

    /** El año solo cuando no es el de este año. */
    @Test fun `el año solo cuando hace falta`() {
        assertEquals("el 15 de enero de 2027",
            Novedades.cuandoSale(LocalDate(2027, 1, 15), hoy))
    }
}
