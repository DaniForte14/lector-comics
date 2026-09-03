package com.dani.lector.datos

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Solo el nombre del fichero: lo demas de [Exportar] es MediaStore y
 * FileProvider, que necesitan un movil.
 */
class ExportarTest {

    @Test fun `quita la extension y pone la pagina con ceros`() {
        assertEquals("Absolute Batman #01 - p007.jpg",
            Exportar.nombre("Absolute Batman #01.cbz", 7))
    }

    @Test fun `tres cifras se quedan como estan`() {
        assertEquals("Blackest Night - p366.jpg",
            Exportar.nombre("Blackest Night.cbz", 366))
    }

    /** Barras y dos puntos rompen un nombre de fichero. */
    @Test fun `los caracteres raros se van`() {
        assertEquals("Green Lantern Rebirth - p001.jpg",
            Exportar.nombre("Green Lantern: Rebirth.cbz", 1))
        assertEquals("Batman Detective - p002.jpg",
            Exportar.nombre("Batman/Detective.cbz", 2))
    }

    /** Las tildes y la ñ SI se quedan: son letras, no caracteres raros. */
    @Test fun `las tildes se respetan`() {
        assertEquals("Compañeros - p010.jpg", Exportar.nombre("Compañeros.cbz", 10))
    }

    @Test fun `un nombre imposible no deja el fichero sin nombre`() {
        assertEquals("pagina - p001.jpg", Exportar.nombre("///.cbz", 1))
    }
}
