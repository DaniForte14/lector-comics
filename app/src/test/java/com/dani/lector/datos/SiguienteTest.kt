package com.dani.lector.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiguienteTest {

    private fun c(n: Int) = Comic(
        uri = "u/$n", nombre = "#$n.cbz", carpeta = "s", numero = n, esEspecial = false)

    private val serie = (1..5).map { c(it) }

    /** Terminado: la pagina guardada es la ultima. */
    private fun leido(paginas: Int = 20, cuando: Long = 0) =
        Marca(pagina = paginas - 1, paginas = paginas, cuando = cuando)

    private fun aMedias(pagina: Int, cuando: Long = 0) =
        Marca(pagina = pagina, paginas = 20, cuando = cuando)

    @Test fun `sin nada leido, el primero`() {
        assertEquals("#1.cbz", Siguiente.de(serie, emptyMap())?.nombre)
    }

    @Test fun `el primero sin terminar`() {
        val m = mapOf("u/1" to leido(), "u/2" to leido())
        assertEquals("#3.cbz", Siguiente.de(serie, m)?.nombre)
    }

    /** Un hueco leido por en medio no adelanta: manda el primero sin terminar. */
    @Test fun `un hueco no adelanta`() {
        val m = mapOf("u/1" to leido(), "u/3" to leido(), "u/4" to leido())
        assertEquals("#2.cbz", Siguiente.de(serie, m)?.nombre)
    }

    /** Lo empezado gana al primero sin empezar: eso es "seguir". */
    @Test fun `lo que tienes a medias gana`() {
        val m = mapOf("u/1" to leido(), "u/4" to aMedias(8))
        assertEquals("#4.cbz", Siguiente.de(serie, m)?.nombre)
    }

    /** Con dos a medias, el que tocaste mas recientemente. */
    @Test fun `entre dos a medias, el mas reciente`() {
        val m = mapOf("u/2" to aMedias(5, cuando = 100),
                      "u/4" to aMedias(9, cuando = 900))
        assertEquals("#4.cbz", Siguiente.de(serie, m)?.nombre)
    }

    /** Abrir y salir sin pasar de la portada no es tenerlo empezado. */
    @Test fun `la pagina cero no cuenta como empezado`() {
        val m = mapOf("u/1" to leido(), "u/4" to aMedias(0))
        assertEquals("#2.cbz", Siguiente.de(serie, m)?.nombre)
    }

    @Test fun `con todo leido no hay siguiente`() {
        val m = serie.associate { it.uri to leido() }
        assertNull(Siguiente.de(serie, m))
    }

    @Test fun `una carpeta vacia no revienta`() {
        assertNull(Siguiente.de(emptyList(), emptyMap()))
    }
}
