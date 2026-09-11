package com.dani.lector.datos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La poda del rastro.
 *
 * Es lo unico del fichero que decide algo: el resto es pegar una linea al final
 * y sacar la hora. Y falla en silencio — un rastro podado de mas se lleva por
 * delante justo las migas de antes del fallo, que son las que se van a leer.
 */
class RastroTest {

    @Test fun `un rastro corto no se toca`() {
        val texto = "uno\ndos\ntres\n"
        assertEquals(texto, Rastro.poda(texto, lineas = 300))
    }

    @Test fun `se queda con las ULTIMAS que son las de antes del fallo`() {
        val texto = (1..10).joinToString("\n") { "linea $it" } + "\n"
        assertEquals("linea 8\nlinea 9\nlinea 10\n", Rastro.poda(texto, lineas = 3))
    }

    @Test fun `un rastro vacio no se convierte en un salto de linea suelto`() {
        assertEquals("", Rastro.poda("", lineas = 300))
        assertEquals("", Rastro.poda("\n\n", lineas = 300))
    }

    @Test fun `siempre acaba en salto de linea para que la siguiente miga no se pegue`() {
        // Sin salto final, la miga de despues quedaria pegada a la ultima linea
        // y las dos se leerian como una sola.
        assertTrue(Rastro.poda("uno\ndos", lineas = 300).endsWith("\n"))
        assertEquals("dos\n", Rastro.poda("uno\ndos", lineas = 1))
    }

    @Test fun `justo en el tope no recorta nada`() {
        val texto = "a\nb\nc\n"
        assertEquals(texto, Rastro.poda(texto, lineas = 3))
    }

    @Test fun `apunta y leer pasan por el disco que se le ha dado`() {
        val disco = DiscoEnMemoria()
        Rastro.arranca(disco)
        Rastro.apunta("abre el comic")

        val leido = Rastro.leer()
        assertTrue(leido.endsWith("  abre el comic\n"), "sale la miga: $leido")
        // dd/MM HH:mm:ss.SSS son 18 caracteres antes de los dos espacios.
        assertTrue(Regex("""^\d\d/\d\d \d\d:\d\d:\d\d\.\d\d\d {2}""").containsMatchIn(leido),
            "lleva la hora delante: $leido")

        Rastro.limpiar()
        assertEquals("Sin rastro todavía.", Rastro.leer())
    }
}
