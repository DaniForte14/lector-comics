package com.dani.lector.datos

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class ProgresoTest {

    /**
     * El caso que lo motiva: un comic de 23 paginas ya leido, o sea con la
     * pagina 22 guardada, y un marcapaginas en la 4. Mirarlo no puede
     * devolverlo a "En curso" ni quitarle la marca de leido.
     */
    @Test fun `mirar un marcapaginas de un comic leido no cuenta`() {
        assertFalse(Progreso.cuenta(pagina = 4, techo = 22))
    }

    /**
     * Y el caso que nadie habia visto: ibas por la 40 y el marcapaginas es la
     * 2. Antes esto te movia a la 2 y perdias por donde ibas.
     */
    @Test fun `mirar hacia atras no mueve por donde ibas`() {
        assertFalse(Progreso.cuenta(pagina = 2, techo = 40))
        assertFalse(Progreso.cuenta(pagina = 39, techo = 40))
    }

    /** En cuanto pasas de donde ibas, eso ya es leer. */
    @Test fun `pasar de donde ibas si cuenta`() {
        assertTrue(Progreso.cuenta(pagina = 41, techo = 40))
    }

    /** La misma pagina no cuenta: seguirias donde estabas. */
    @Test fun `quedarse en la misma no cuenta`() {
        assertFalse(Progreso.cuenta(pagina = 40, techo = 40))
    }

    /**
     * Abriendo el comic normalmente se guarda siempre, tambien hacia atras:
     * pasar una pagina para atras leyendo es de lo mas normal.
     */
    @Test fun `sin marcapaginas se guarda siempre`() {
        assertTrue(Progreso.cuenta(pagina = 0, techo = -1))
        assertTrue(Progreso.cuenta(pagina = 5, techo = -1))
    }

    /** Un comic que nunca has abierto no tiene nada que perder. */
    @Test fun `un comic sin marca previa se guarda`() {
        // marcas.de(uri) es null -> techo = -1
        assertTrue(Progreso.cuenta(pagina = 4, techo = -1))
    }
}
