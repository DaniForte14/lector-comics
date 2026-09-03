package com.dani.lector.datos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Que lo guardado se vuelve a leer igual.
 *
 * ESTO NO SE PODIA PROBAR HASTA HOY. Los cuatro almacenes pedian un `Context` de
 * Android en el constructor, asi que **el guardado y la lectura —justo donde se
 * pierden los datos— no tenian ni una prueba**. Con [Disco] detras de una
 * interfaz basta un disco de mentira en memoria.
 *
 * Lo que se comprueba es la vuelta entera: escribir, tirar la instancia, y leer
 * con otra nueva sobre el mismo disco. Un fallo de serializacion no se ve de
 * otra forma: la instancia viva responde bien desde su cache aunque lo que haya
 * escrito en el fichero sea basura.
 */
class AlmacenesTest {

    /** Un disco que ademas cuenta las escrituras, para lo de las tandas. */
    private class DiscoContador : Disco {
        val ficheros = mutableMapOf<String, String>()
        var escrituras = 0
        override fun leer(nombre: String): String? = ficheros[nombre]
        override fun escribir(nombre: String, texto: String) {
            escrituras++; ficheros[nombre] = texto
        }
        override fun borrar(nombre: String) { ficheros.remove(nombre) }
    }

    // ─────────────────────────── PROGRESO ───────────────────────────

    @Test fun `una marca guardada se vuelve a leer igual`() {
        val disco = DiscoEnMemoria()
        Progreso(disco).marcar("uri://a", pagina = 7, paginas = 23)

        val leido = Progreso(disco).de("uri://a")
        assertEquals(7, leido?.pagina)
        assertEquals(23, leido?.paginas)
        assertTrue((leido?.cuando ?: 0) > 0, "la fecha tiene que guardarse")
    }

    @Test fun `lo olvidado no vuelve`() {
        val disco = DiscoEnMemoria()
        Progreso(disco).apply { marcar("uri://a", 7, 23); olvidar("uri://a") }
        assertNull(Progreso(disco).de("uri://a"))
    }

    @Test fun `un disco vacio no revienta`() {
        assertNull(Progreso(DiscoEnMemoria()).de("uri://loquesea"))
        assertEquals(0, Progreso(DiscoEnMemoria()).todas().size)
    }

    // Un fichero a medio escribir, o de otra version, no puede tirar la app:
    // sin datos legibles es como no tener datos.
    @Test fun `un json roto se lee como vacio`() {
        val disco = DiscoEnMemoria(mutableMapOf("progreso.json" to "{esto no es json"))
        assertEquals(0, Progreso(disco).todas().size)
    }

    // ─────────────────────────── LAS TANDAS ───────────────────────────

    /**
     * La razon de que exista `tanda`: marcar una carpeta reescribia el fichero
     * ENTERO una vez por comic. Treinta comics eran treinta reescrituras.
     */
    @Test fun `una tanda escribe una sola vez`() {
        val disco = DiscoContador()
        val p = Progreso(disco)
        p.tanda { repeat(30) { i -> p.marcar("uri://$i", 1, 10) } }
        assertEquals(1, disco.escrituras)
        assertEquals(30, Progreso(disco).todas().size)
    }

    @Test fun `sin tanda escribe una vez por marca`() {
        val disco = DiscoContador()
        val p = Progreso(disco)
        repeat(3) { i -> p.marcar("uri://$i", 1, 10) }
        assertEquals(3, disco.escrituras)
    }

    // ─────────────────────────── MARCADORES ───────────────────────────

    @Test fun `un marcapaginas guardado se vuelve a leer`() {
        val disco = DiscoEnMemoria()
        assertTrue(Marcadores(disco).alternar("uri://a", 12))
        assertEquals(setOf(12), Marcadores(disco).de("uri://a"))
    }

    @Test fun `alternar dos veces lo quita del fichero`() {
        val disco = DiscoEnMemoria()
        Marcadores(disco).apply { alternar("uri://a", 12); alternar("uri://a", 12) }
        assertEquals(emptySet(), Marcadores(disco).de("uri://a"))
    }

    // ─────────────────────────── SESIONES ───────────────────────────

    @Test fun `una sesion guardada se vuelve a leer igual`() {
        val disco = DiscoEnMemoria()
        Sesiones(disco).apuntar("uri://a", dia = "2026-09-03", pagina = 5, ahora = 1_000L)

        val leidas = Sesiones(disco).de("2026-09-03")
        assertEquals(1, leidas.size)
        assertEquals("uri://a", leidas[0].uri)
        assertEquals(5, leidas[0].hasta)
    }

    // ─────────────────────── SERIES REMOTAS ───────────────────────

    /**
     * La ficha es la unica con campos opcionales y una lista anidada dentro,
     * asi que es la que mas tiene que perder al escribirse y volverse a leer.
     */
    @Test fun `una ficha con numeros sobrevive a la ida y vuelta`() {
        val disco = DiscoEnMemoria()
        SeriesRemotas(disco).guardar(
            Ficha(
                ruta = "DC/Green lantern/Absolute",
                volumenId = "150172",
                nombre = "Absolute Green Lantern",
                anio = 2025,
                numeros = listOf(
                    com.dani.lector.red.NumeroRemoto("1", 1, "2025-04-02", "El primero", "2025-04-01"),
                    // Sin fecha ni venta: los opcionales tienen que poder faltar.
                    com.dani.lector.red.NumeroRemoto("2", 2, null, "", null)
                ),
                cuando = 999L,
                seguida = true,
                avisados = setOf("1")
            )
        )

        val f = SeriesRemotas(disco).de("DC/Green lantern/Absolute")
        assertEquals("Absolute Green Lantern", f?.nombre)
        assertEquals(2025, f?.anio)
        assertEquals(true, f?.seguida)
        assertEquals(setOf("1"), f?.avisados)
        assertEquals(2, f?.numeros?.size)
        assertEquals("2025-04-02", f?.numeros?.get(0)?.fecha)
        assertNull(f?.numeros?.get(1)?.fecha)
        assertNull(f?.numeros?.get(1)?.venta)
    }

    // El ano ausente y el ano cero son cosas distintas: 0 es un ano valido de
    // grapa en otros campos y aqui significaria "no lo sabemos".
    @Test fun `una ficha sin anio se relee sin anio`() {
        val disco = DiscoEnMemoria()
        SeriesRemotas(disco).guardar(
            Ficha("r", "1", "n", anio = null, numeros = emptyList(), cuando = 1L))
        assertNull(SeriesRemotas(disco).de("r")?.anio)
    }
}
