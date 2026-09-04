package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Que entra como pagina y en que orden.
 *
 * Merece prueba por lo de siempre en este proyecto: **si estas dos reglas se
 * tuercen no salta ningun error**. El comic se abre igual, solo que por la
 * pagina que no era, o con una pagina de mas que en realidad es un fichero de
 * metadatos de macOS.
 */
class ImagenesTest {

    @Test fun `las extensiones normales entran`() {
        assertTrue(Imagenes.es("pagina01.jpg"))
        assertTrue(Imagenes.es("pagina01.jpeg"))
        assertTrue(Imagenes.es("pagina01.png"))
        assertTrue(Imagenes.es("pagina01.webp"))
        assertTrue(Imagenes.es("pagina01.gif"))
        assertTrue(Imagenes.es("pagina01.bmp"))
    }

    // Hay CBZ enteros con las paginas en mayusculas.
    @Test fun `la extension en mayusculas entra igual`() {
        assertTrue(Imagenes.es("PAGINA01.JPG"))
        assertTrue(Imagenes.es("Pagina01.Png"))
    }

    @Test fun `lo que no es imagen se queda fuera`() {
        assertFalse(Imagenes.es("ComicInfo.xml"))
        assertFalse(Imagenes.es("leeme.txt"))
        assertFalse(Imagenes.es("sinextension"))
        assertFalse(Imagenes.es("comic.pdf"))
    }

    // Un ._algo.jpg de macOS SI tiene extension de imagen: son dos kilobytes de
    // metadatos que saldrian como primera pagina del comic.
    @Test fun `los ficheros ocultos se quedan fuera aunque parezcan imagen`() {
        assertFalse(Imagenes.es("._portada.jpg"))
        assertFalse(Imagenes.es(".DS_Store"))
        assertFalse(Imagenes.es("carpeta/._pagina01.jpg"))
    }

    // Lo oculto es el NOMBRE del fichero, no la ruta: una carpeta que empiece
    // por punto no puede tirar sus paginas.
    @Test fun `una carpeta oculta no descarta la pagina`() {
        assertTrue(Imagenes.es(".extras/pagina01.jpg"))
    }

    // El compresor de macOS mete una copia sombra de cada imagen ahi dentro.
    // Sin esta regla cada pagina saldria DOS veces.
    @Test fun `la carpeta __MACOSX se queda fuera entera`() {
        assertFalse(Imagenes.es("__MACOSX/pagina01.jpg"))
        assertFalse(Imagenes.es("comic/__MACOSX/pagina01.jpg"))
    }

    @Test fun `se ordena por nombre dentro del mismo nivel`() {
        assertEquals(
            listOf("pagina01.jpg", "pagina02.jpg", "pagina10.jpg"),
            Imagenes.ordenadas(listOf("pagina10.jpg", "pagina01.jpg", "pagina02.jpg"))
        )
    }

    // Sin comparar en minusculas, en orden de bytes TODAS las mayusculas van
    // antes que las minusculas y Page10 se colaria delante de page02.
    @Test fun `las mayusculas no se cuelan delante`() {
        assertEquals(
            listOf("Page01.jpg", "page02.jpg", "Page10.jpg"),
            Imagenes.ordenadas(listOf("Page10.jpg", "page02.jpg", "Page01.jpg"))
        )
    }

    // Lo de la raiz primero y las subcarpetas despues. Es la regla que evita
    // que abras el comic por la carpeta de extras.
    @Test fun `lo menos hondo va primero`() {
        assertEquals(
            listOf("pagina01.jpg", "pagina02.jpg", "extras/aaa.jpg", "extras/bbb.jpg"),
            Imagenes.ordenadas(
                listOf("extras/bbb.jpg", "pagina02.jpg", "extras/aaa.jpg", "pagina01.jpg"))
        )
    }

    @Test fun `a igual profundidad manda el nombre completo con su carpeta`() {
        assertEquals(
            listOf("a/2.jpg", "b/1.jpg"),
            Imagenes.ordenadas(listOf("b/1.jpg", "a/2.jpg"))
        )
    }
}
