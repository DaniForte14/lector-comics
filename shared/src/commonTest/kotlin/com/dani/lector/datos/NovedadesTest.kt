package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class NovedadesTest {

    private val AHORA = 1_000_000_000_000L
    private val CORTE = "2026-05-01"
    private val VIEJA = AHORA - Novedades.ESPERA - 1
    private val HOY: LocalDate = LocalDate.parse("2026-09-02")

    private fun cand(
        ruta: String,
        ultima: String? = "2026-08-01",
        revisada: Long = VIEJA,
        id: String = "id-$ruta",
        seguida: Boolean = false
    ) = Novedades.Candidata(ruta, ruta, id, ultima, revisada, seguida)

    /** Un numero con fecha de PORTADA y, si se le pasa, la de venta de verdad. */
    private fun num(etiqueta: String, fecha: String?, venta: String? = null) =
        NumeroRemoto(etiqueta, etiqueta.toIntOrNull(), fecha, "", venta)

    // ─────────────────────────── A QUIEN PREGUNTAR ───────────────────────────

    @Test fun `una serie terminada no se pregunta`() {
        assertTrue(Novedades.aRevisar(listOf(cand("v", ultima = "1994-06-01")), AHORA, CORTE)
            .isEmpty())
    }

    @Test fun `una serie viva y sin mirar hace dias si`() {
        assertEquals(listOf("viva"),
            Novedades.aRevisar(listOf(cand("viva")), AHORA, CORTE).map { it.ruta })
    }

    /** Recien mirada no se vuelve a preguntar: seria tirar una peticion. */
    @Test fun `una recien mirada se salta`() {
        assertTrue(Novedades.aRevisar(listOf(cand("viva", revisada = AHORA - 1000)), AHORA, CORTE)
            .isEmpty())
    }

    /** Sin id no se puede preguntar: los numeros se piden por id. */
    @Test fun `sin id no entra`() {
        assertTrue(Novedades.aRevisar(listOf(cand("viva", id = "")), AHORA, CORTE).isEmpty())
    }

    /**
     * El tope y el orden juntos son lo que hace que TODAS se acaben repasando:
     * la mas vieja primero, tres por pasada.
     */
    @Test fun `van por turnos empezando por la mas vieja`() {
        val cs = listOf(
            cand("a", revisada = VIEJA - 30),
            cand("b", revisada = VIEJA - 10),
            cand("c", revisada = VIEJA - 20),
            cand("d", revisada = VIEJA - 40)
        )
        assertEquals(listOf("d", "a", "c"),
            Novedades.aRevisar(cs, AHORA, CORTE, tope = 3).map { it.ruta })
    }

    /** Si el tope corta, que corte por donde menos duele: las seguidas primero. */
    @Test fun `las seguidas van delante aunque sean mas recientes`() {
        val cs = listOf(
            cand("vieja", revisada = VIEJA - 999),
            cand("seguida", revisada = VIEJA - 1, seguida = true)
        )
        assertEquals(listOf("seguida", "vieja"),
            Novedades.aRevisar(cs, AHORA, CORTE, tope = 2).map { it.ruta })
    }

    @Test fun `el modo de segundo plano solo mira las seguidas`() {
        val cs = listOf(cand("normal"), cand("seguida", seguida = true))
        assertEquals(listOf("seguida"),
            Novedades.aRevisar(cs, AHORA, CORTE, tope = 12, soloSeguidas = true).map { it.ruta })
    }

    /**
     * Una seguida se mira a diario; una normal, cada tres dias. Con un dia justo
     * de espera, solo la seguida entra.
     */
    @Test fun `la seguida se mira a diario y la normal no`() {
        val hace25h = AHORA - 25L * 60 * 60 * 1000
        val cs = listOf(
            cand("normal", revisada = hace25h),
            cand("seguida", revisada = hace25h, seguida = true)
        )
        assertEquals(listOf("seguida"), Novedades.aRevisar(cs, AHORA, CORTE).map { it.ruta })
    }

    // ─────────────────────────── DE QUE AVISAR ───────────────────────────

    /**
     * El caso que motiva todo: Comic Vine da de alta el numero meses antes de
     * que llegue a la tienda. Portada de diciembre = venta en octubre, y hoy es
     * septiembre: se sabe que existe, pero no se avisa todavia.
     */
    @Test fun `un numero anunciado y no salido queda pendiente`() {
        val r = Novedades.aAvisar(listOf(num("12", "2026-12-01")), emptySet(), HOY)
        assertTrue(r.avisar.isEmpty())
        assertTrue(r.callar.isEmpty())
    }

    /** Portada de octubre = venta el 2 de agosto. Hoy es 2 de septiembre: fuera. */
    @Test fun `un numero que ya deberia estar en la tienda se avisa`() {
        val r = Novedades.aAvisar(listOf(num("11", "2026-10-01")), emptySet(), HOY)
        assertEquals(listOf("11"), r.avisar.map { it.etiqueta })
    }

    @Test fun `lo ya avisado no se repite`() {
        val r = Novedades.aAvisar(listOf(num("11", "2026-10-01")), setOf("11"), HOY)
        assertTrue(r.avisar.isEmpty())
        assertTrue(r.callar.isEmpty())
    }

    /** Un numero viejo dado de alta tarde no es novedad: se marca y se calla. */
    @Test fun `un numero viejo se da por visto sin avisar`() {
        val r = Novedades.aAvisar(listOf(num("3", "2019-04-01")), emptySet(), HOY)
        assertTrue(r.avisar.isEmpty())
        assertEquals(listOf("3"), r.callar.map { it.etiqueta })
    }

    /** Sin fecha no se sabe cuando sale: ni se avisa ni se da por visto. */
    @Test fun `sin fecha queda pendiente`() {
        val r = Novedades.aAvisar(listOf(num("Annual 2", null)), emptySet(), HOY)
        assertTrue(r.avisar.isEmpty())
        assertTrue(r.callar.isEmpty())
    }

    /** Un especial con fecha si se avisa: lo que importa es la fecha, no el numero. */
    @Test fun `un especial con fecha se avisa igual`() {
        val r = Novedades.aAvisar(listOf(num("Annual 2", "2026-10-01")), emptySet(), HOY)
        assertEquals(listOf("Annual 2"), r.avisar.map { it.etiqueta })
    }

    @Test fun `varios a la vez salen ordenados por fecha`() {
        val r = Novedades.aAvisar(
            listOf(num("12", "2026-10-01"), num("11", "2026-09-01")), emptySet(), HOY)
        assertEquals(listOf("11", "12"), r.avisar.map { it.etiqueta })
    }

    /** Sin store_date se estima: sesenta dias antes de la de portada. */
    @Test fun `sin fecha de venta se estima desde la de portada`() {
        assertEquals(LocalDate.parse("2026-08-02"), Novedades.venta(num("1", "2026-10-01")))
        assertTrue(Novedades.estimada(num("1", "2026-10-01")))
        assertEquals(null, Novedades.venta(num("1", null)))
        assertEquals(null, Novedades.venta(num("1", "no es una fecha")))
    }

    /**
     * Y con store_date manda esa: un dato real siempre gana a una convencion.
     * Aqui las dos se contradicen a proposito —la estimacion daria el 2 de
     * agosto— para que se vea cual de las dos usa.
     */
    @Test fun `con fecha de venta manda esa y no la estimacion`() {
        val n = num("1", "2026-10-01", venta = "2026-08-19")
        assertEquals(LocalDate.parse("2026-08-19"), Novedades.venta(n))
        assertFalse(Novedades.estimada(n))
    }

    /** Una fecha de venta rota no se cuela: se cae al respaldo. */
    @Test fun `una fecha de venta ilegible se ignora`() {
        val n = num("1", "2026-10-01", venta = "proximamente")
        assertEquals(LocalDate.parse("2026-08-02"), Novedades.venta(n))
        assertTrue(Novedades.estimada(n))
    }

    /**
     * La estimacion puede caer del otro lado del corte que la real: portada de
     * noviembre da venta estimada el 2 de septiembre —hoy, o sea que avisaria—
     * pero la de verdad es del 30 de septiembre y todavia no ha salido.
     */
    @Test fun `la fecha de venta real puede frenar un aviso que la estimacion daria`() {
        val r = Novedades.aAvisar(
            listOf(num("21", "2026-11-01", venta = "2026-09-30")), emptySet(), HOY)
        assertTrue(r.avisar.isEmpty())
        assertTrue(r.callar.isEmpty())
    }

    /** Y al reves: la de verdad puede adelantar un aviso que la estimacion retrasaria. */
    @Test fun `la fecha de venta real puede adelantar un aviso`() {
        val r = Novedades.aAvisar(
            listOf(num("21", "2026-12-01", venta = "2026-08-26")), emptySet(), HOY)
        assertEquals(listOf("21"), r.avisar.map { it.etiqueta })
    }

    /** La frase del aviso dice si la fecha es la de verdad o una cuenta. */
    @Test fun `la frase distingue el dato de la estimacion`() {
        assertEquals("ya está en tiendas",
            Novedades.fraseVenta(listOf(num("1", "2026-10-01", venta = "2026-08-19"))))
        assertEquals("ya debería estar en tiendas",
            Novedades.fraseVenta(listOf(num("1", "2026-10-01"))))
        // Con uno estimado entre varios, se baja al lenguaje del mas debil.
        assertEquals("ya debería estar en tiendas",
            Novedades.fraseVenta(listOf(
                num("1", "2026-10-01", venta = "2026-08-19"), num("2", "2026-11-01"))))
    }

    /**
     * La linea de salida: al empezar a seguir, todo lo que ya existe se da por
     * visto. Sin esto, seguir una serie de sesenta numeros suelta sesenta
     * notificaciones.
     */
    @Test fun `la linea de salida tapa todo lo que ya hay`() {
        val nums = listOf(num("1", "2020-01-01"), num("2", "2020-02-01"))
        val r = Novedades.aAvisar(nums, Novedades.etiquetasDe(nums), HOY)
        assertTrue(r.avisar.isEmpty())
        assertTrue(r.callar.isEmpty())
    }

    /**
     * La regla que impide que un 420 de Comic Vine deje una serie a cero: una
     * respuesta vacia o mas corta que lo que ya teniamos no se guarda.
     */
    @Test fun `una respuesta vacia o mas corta no se guarda`() {
        val antes = (1..10).map { num("$it", "2026-01-01") }
        assertFalse(Novedades.fiable(antes, emptyList()))
        assertFalse(Novedades.fiable(antes, (1..9).map { num("$it", "2026-01-01") }))
        assertTrue(Novedades.fiable(antes, antes))
        assertTrue(Novedades.fiable(antes, antes + num("11", "2026-02-01")))
    }

    // ─────────────────────── CUANDO SALE EL SIGUIENTE ───────────────────────

    /** El que antes salga de los que quedan, no el de numero mas alto. */
    @Test fun `el proximo es el que llega antes`() {
        val nums = listOf(
            num("20", "2026-08-01"),                       // ya salio
            num("22", "2026-11-01", venta = "2026-10-15"),
            num("21", "2026-11-01", venta = "2026-09-30")
        )
        assertEquals("21", Novedades.proximo(nums, HOY)?.etiqueta)
    }

    /** Sin fecha no se puede decir cuando sale, asi que no compite. */
    @Test fun `un numero sin fecha no puede ser el proximo`() {
        val nums = listOf(num("21", null), num("22", "2026-12-01"))
        assertEquals("22", Novedades.proximo(nums, HOY)?.etiqueta)
    }

    @Test fun `sin nada por salir no hay proximo`() {
        assertNull(Novedades.proximo(listOf(num("1", "2026-01-01")), HOY))
    }

    @Test fun `la fecha en cristiano`() {
        assertEquals("el 2 de septiembre",
            Novedades.enCristiano(LocalDate.parse("2026-09-02"), HOY))
        // El año solo cuando no es el de este año.
        assertEquals("el 14 de marzo de 2027",
            Novedades.enCristiano(LocalDate.parse("2027-03-14"), HOY))
    }

    /** "Sale" con la fecha de verdad, "debería salir" con la estimada. */
    @Test fun `la frase del proximo distingue el dato de la cuenta`() {
        assertEquals("El siguiente, el #21, sale el 30 de septiembre.",
            Novedades.fraseProximo(num("21", "2026-11-01", venta = "2026-09-30"), HOY))
        assertEquals("El siguiente, el #21, debería salir el 2 de octubre.",
            Novedades.fraseProximo(num("21", "2026-12-01"), HOY))
        assertNull(Novedades.fraseProximo(num("21", null), HOY))
    }

    @Test fun `el texto del aviso`() {
        val n = { e: String -> num(e, "2026-01-01") }
        assertEquals("Green Lantern #12", Novedades.texto("Green Lantern", listOf(n("12"))))
        assertEquals("Green Lantern #12 y #13",
            Novedades.texto("Green Lantern", listOf(n("12"), n("13"))))
        assertEquals("Daredevil #1, #2 y Annual 1",
            Novedades.texto("Daredevil", listOf(n("1"), n("2"), n("Annual 1"))))
    }
}
