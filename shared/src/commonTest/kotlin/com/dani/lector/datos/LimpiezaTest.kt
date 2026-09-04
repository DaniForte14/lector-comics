package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Las reglas que deciden que fichero se renombra y cual se borra.
 *
 * **Son las pruebas mas caras de no tener de todo el proyecto**: al otro lado de
 * estas tres funciones hay un `renameDocument` y un `deleteDocument` sobre la
 * biblioteca de Dani. Y fallan calladas — el fichero se renombra igual, solo que
 * no era ese.
 *
 * Los dos casos que ya estuvieron a punto de costar caro estan aqui abajo con
 * nombre y apellidos: "Corps (21)" y "Batman (2016)".
 */
class LimpiezaTest {

    // ── la extension doble ──

    @Test fun `quita la extension que sobra`() {
        assertEquals("Batman 01.cbz", Limpieza.sinDobleExtension("Batman 01.cbz.zip"))
    }

    @Test fun `un nombre normal no tiene nada que quitar`() {
        assertNull(Limpieza.sinDobleExtension("Batman 01.cbz"))
        assertNull(Limpieza.sinDobleExtension("Batman 01.cbr"))
        assertNull(Limpieza.sinDobleExtension("Batman 01.zip"))
    }

    @Test fun `la extension doble se reconoce en mayusculas`() {
        assertEquals("Batman 01.CBZ", Limpieza.sinDobleExtension("Batman 01.CBZ.ZIP"))
    }

    // ── que es una copia ──

    @Test fun `una copia dice como se llama su original`() {
        assertEquals("Batman 01.cbz", Limpieza.originalDe("Batman 01 (1).cbz"))
        assertEquals("Batman 01.cbz", Limpieza.originalDe("Batman 01(2).cbz"))
    }

    @Test fun `un nombre sin parentesis al final no es copia de nada`() {
        assertNull(Limpieza.originalDe("Batman 01.cbz"))
        assertNull(Limpieza.originalDe("Batman (1) del final.cbz"))
        assertNull(Limpieza.originalDe("Batman (uno).cbz"))
    }

    /**
     * ESTE ES EL CASO QUE CAZO DANI EN UNA CAPTURA. `originalDe` devuelve un
     * nombre, no un veredicto: "Corps (21).cbz" se parece a una copia, y lo que
     * decide que NO lo es, es que "Corps.cbz" no este en la carpeta.
     *
     * Si algun dia alguien hace que esta funcion decida sola, se cargara la
     * numeracion de la serie entera sin dar un solo error.
     */
    @Test fun `el numero de grapa solo se salva porque no hay original al lado`() {
        assertEquals("Green Lantern Corps.cbz",
                     Limpieza.originalDe("Green Lantern Corps (21).cbz"))
    }

    // ── de "(n)" a "#0n" ──

    @Test fun `pasa los numeros entre parentesis a formato de grapa`() {
        val r = Limpieza.aGrapa(listOf("Corps (1).cbz", "Corps (2).cbz"))
        assertEquals(listOf("Corps #01.cbz", "Corps #02.cbz"), r.map { it.nuevo })
        assertTrue(r.none { it.choca })
    }

    // Con numeros hasta el 17 basta "#01"; si la serie llega a 120 hace falta
    // "#001" o el orden se rompe otra vez en el 100.
    @Test fun `el mayor de la carpeta decide cuantas cifras se rellenan`() {
        val r = Limpieza.aGrapa(listOf("Corps (1).cbz", "Corps (120).cbz"))
        assertEquals(listOf("Corps #001.cbz", "Corps #120.cbz"), r.map { it.nuevo })
    }

    // El otro caso de la captura: eso es el año de la coleccion.
    @Test fun `un año no se toca`() {
        assertEquals(emptyList(), Limpieza.aGrapa(listOf("Batman (2016).cbz")))
        assertEquals(emptyList(), Limpieza.aGrapa(listOf("Superman (1938).cbz")))
    }

    // 1929 y 2101 caen fuera del rango: ahi vuelven a ser numeros de grapa.
    @Test fun `los bordes del rango de años`() {
        assertEquals(listOf("Corps #1929.cbz"),
                     Limpieza.aGrapa(listOf("Corps (1929).cbz")).map { it.nuevo })
        assertEquals(listOf("Corps #2101.cbz"),
                     Limpieza.aGrapa(listOf("Corps (2101).cbz")).map { it.nuevo })
    }

    @Test fun `lo que ya esta bien no sale en la lista`() {
        assertEquals(emptyList(), Limpieza.aGrapa(listOf("Corps #01.cbz", "Batman.cbz")))
    }

    // No se pisa nada: se marca y quien llama avisa.
    @Test fun `si el nombre nuevo ya existe se marca en vez de renombrar`() {
        val r = Limpieza.aGrapa(listOf("Corps (1).cbz", "Corps #01.cbz"))
        assertEquals(1, r.size)
        assertEquals("Corps (1).cbz", r[0].viejo)
        assertEquals("Corps #01.cbz", r[0].nuevo)
        assertTrue(r[0].choca)
    }

    @Test fun `un nombre sin extension se renombra igual`() {
        assertEquals(listOf("Corps #07"), Limpieza.aGrapa(listOf("Corps (7)")).map { it.nuevo })
    }

    // Cada carpeta se mira por separado, asi que una carpeta sin candidatos no
    // arrastra el relleno de otra.
    @Test fun `una carpeta sin numeros entre parentesis no cambia nada`() {
        assertEquals(emptyList(), Limpieza.aGrapa(listOf("Batman 01.cbz", "Batman 02.cbz")))
        assertEquals(emptyList(), Limpieza.aGrapa(emptyList()))
    }
}
