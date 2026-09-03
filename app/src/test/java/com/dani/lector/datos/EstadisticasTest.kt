package com.dani.lector.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadisticasTest {

    private val AHORA = 1_756_000_000_000L

    private fun comic(carpeta: String, n: Int) =
        Comic(uri = "$carpeta/$n", nombre = "$n.cbz", carpeta = carpeta,
              numero = n, esEspecial = false)

    private fun leido(paginas: Int = 20) = Marca(paginas - 1, paginas, AHORA)
    private fun aMedias(pagina: Int, paginas: Int = 20) = Marca(pagina, paginas, AHORA)

    /**
     * El arbol de Dani es DC Comics / personaje / serie, asi que el primer
     * nivel son editoriales y hay que poder BAJAR. La version que cogia el
     * primer tramo y lo llamaba "personaje" decia "DC Comics: 15 de 208".
     */
    @Test fun `baja un nivel cada vez`() {
        val comics = (1..4).map { comic("DC Comics/Batman/Vol 3", it) } +
                     (1..2).map { comic("DC Comics/Green Lantern/Vol 4", it) } +
                     listOf(comic("Marvel/Daredevil/Vol 6", 1))
        val progreso = mapOf("DC Comics/Batman/Vol 3/1" to leido())

        val arriba = Estadisticas.avance(progreso, comics)
        assertEquals(listOf("DC Comics", "Marvel"), arriba.map { it.nombre })
        assertEquals(6, arriba[0].total)
        assertEquals(1, arriba[0].leidos)
        assertFalse(arriba[0].hoja)

        val dentro = Estadisticas.avance(progreso, comics, "DC Comics")
        assertEquals(listOf("Batman", "Green Lantern"), dentro.map { it.nombre })
        assertEquals("DC Comics/Batman", dentro[0].ruta)
        assertEquals(4, dentro[0].total)

        val serie = Estadisticas.avance(progreso, comics, "DC Comics/Batman")
        assertEquals(listOf("Vol 3"), serie.map { it.nombre })
        // Ya no hay a donde bajar: sus comics estan directamente ahi.
        assertTrue(serie[0].hoja)
    }

    /** Un comic suelto en la carpeta no se mezcla con las series de al lado. */
    @Test fun `los sueltos van aparte`() {
        val comics = listOf(comic("DC Comics", 1), comic("DC Comics/Batman", 1))
        val r = Estadisticas.avance(emptyMap(), comics, "DC Comics")
        val sueltos = r.first { it.nombre == "Sueltos aquí" }
        assertEquals(1, sueltos.total)
        assertTrue(sueltos.hoja)
    }

    /** El avance se cuenta sobre lo que TIENES, que es una cifra terminable. */
    @Test fun `el avance por personaje cuenta tus ficheros`() {
        val comics = (1..4).map { comic("GL/Vol 4", it) } +
                     (1..2).map { comic("Batman/Vol 3", it) }
        val progreso = mapOf(
            "GL/Vol 4/1" to leido(), "GL/Vol 4/2" to leido(),
            "Batman/Vol 3/1" to leido()
        )
        val r = Estadisticas.calcular(progreso, comics, AHORA)
        assertEquals(3, r.terminados)
        assertEquals(6, r.comics)
        // Batman va primero: 1 de 2 es el 50%, GL 2 de 4 tambien... a igualdad
        // manda el que mas tiene.
        assertEquals(listOf("GL", "Batman"), r.avance.map { it.nombre })
        assertEquals(2, r.avance[0].leidos)
        assertEquals(4, r.avance[0].total)
        assertEquals(50, r.avance[1].porcentaje)
    }

    /**
     * El fichero de progreso guarda cosas de comics que ya has borrado, y
     * contarlas daria mas leidos que comics tienes.
     */
    @Test fun `el progreso de comics que ya no tienes no cuenta`() {
        val comics = listOf(comic("GL/Vol 4", 1))
        val progreso = mapOf(
            "GL/Vol 4/1" to leido(),
            "GL/Vol 4/99" to leido()          // borrado del disco
        )
        val r = Estadisticas.calcular(progreso, comics, AHORA)
        assertEquals(1, r.terminados)
        assertEquals(1, r.comics)
    }

    /** Una serie completa es una carpeta donde ya has leido todo. */
    @Test fun `series completas`() {
        val comics = (1..2).map { comic("GL/Vol 4", it) } +
                     (1..2).map { comic("GL/Corps", it) }
        val progreso = mapOf("GL/Vol 4/1" to leido(), "GL/Vol 4/2" to leido(),
                             "GL/Corps/1" to leido())
        val r = Estadisticas.calcular(progreso, comics, AHORA)
        assertEquals(2, r.series)
        assertEquals(1, r.seriesCompletas)
    }

    /** De uno a medias cuentan las paginas por las que has pasado, no las que tiene. */
    @Test fun `las paginas de uno a medias no se cuentan enteras`() {
        val comics = listOf(comic("GL/Vol 4", 1), comic("GL/Vol 4", 2))
        val progreso = mapOf("GL/Vol 4/1" to leido(20), "GL/Vol 4/2" to aMedias(4, 20))
        val r = Estadisticas.calcular(progreso, comics, AHORA)
        assertEquals(1, r.terminados)
        assertEquals(1, r.empezados)
        assertEquals(25, r.paginas)          // 20 del leido + 5 del que va por la 4
    }

    @Test fun `una biblioteca vacia no revienta`() {
        val r = Estadisticas.calcular(emptyMap(), emptyList(), AHORA)
        assertEquals(0, r.comics)
        assertEquals(0, r.series)
        assertEquals(emptyList<Estadisticas.Avance>(), r.avance)
    }
}
