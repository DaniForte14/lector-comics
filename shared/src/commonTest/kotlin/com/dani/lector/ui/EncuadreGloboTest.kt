package com.dani.lector.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Donde sale el globo ampliado. Se prueba porque si se tuerce no da ningun
 * error: el globo sale diminuto, cortado por un borde o encima de otro sitio, y
 * solo se ve con el movil en la mano.
 *
 * Una pantalla de 1000 x 2000 para que las cuentas se hagan de cabeza: el tope
 * de ancho es 920 y el de alto 1600.
 */
class EncuadreGloboTest {

    private val pantalla = Size(1000f, 2000f)
    private val margen = 0.01f

    @Test fun `un globo pequeño crece hasta el tope de ampliacion y no mas`() {
        val r = encuadreGlobo(Rect(100f, 100f, 200f, 150f), pantalla)
        assertEquals(100f * GLOBO_AMPLIACION_MAX, r.width, margen)
        assertEquals(50f * GLOBO_AMPLIACION_MAX, r.height, margen)
    }

    @Test fun `un globo ancho lo limita el ancho de la pantalla`() {
        val r = encuadreGlobo(Rect(0f, 1000f, 500f, 1100f), pantalla)
        assertEquals(1000f * GLOBO_ANCHO_MAX, r.width, margen)
        // y conserva la forma: si no, el recorte saldria estirado
        assertEquals(r.width / 5f, r.height, margen)
    }

    @Test fun `un globo alto lo limita el alto de la pantalla`() {
        val r = encuadreGlobo(Rect(400f, 500f, 500f, 1200f), pantalla)
        assertEquals(2000f * GLOBO_ALTO_MAX, r.height, margen)
        assertEquals(r.height / 7f, r.width, margen)
    }

    @Test fun `un globo que ya pasa del hueco se queda como esta y no encoge`() {
        val r = encuadreGlobo(Rect(0f, 0f, 950f, 500f), pantalla)
        assertEquals(950f, r.width, margen)
        assertEquals(500f, r.height, margen)
    }

    @Test fun `un globo mas grande que la pantalla sale centrado y sin excepcion`() {
        // El caso en que coerceIn recibiria el minimo por encima del maximo.
        val r = encuadreGlobo(Rect(-100f, -100f, 1100f, 2100f), pantalla)
        assertEquals(500f, r.center.x, margen)
        assertEquals(1000f, r.center.y, margen)
        assertEquals(1200f, r.width, margen)
    }

    @Test fun `un globo en la esquina de arriba se empuja hacia dentro`() {
        val r = encuadreGlobo(Rect(0f, 0f, 100f, 100f), pantalla)
        assertEquals(0f, r.left, margen)
        assertEquals(0f, r.top, margen)
        assertEquals(250f, r.width, margen)
    }

    @Test fun `un globo en la esquina de abajo no se sale por la derecha ni por abajo`() {
        val r = encuadreGlobo(Rect(900f, 1900f, 1000f, 2000f), pantalla)
        assertEquals(1000f, r.right, margen)
        assertEquals(2000f, r.bottom, margen)
    }

    @Test fun `lejos de los bordes se centra sobre el propio globo`() {
        val globo = Rect(450f, 950f, 550f, 1050f)
        val r = encuadreGlobo(globo, pantalla)
        assertEquals(globo.center.x, r.center.x, margen)
        assertEquals(globo.center.y, r.center.y, margen)
        assertTrue(r.width > globo.width, "tiene que ampliarse")
    }

    @Test fun `sin llenar la escala de partida es 1`() {
        assertEquals(1f, escalaBase(false, 2.2f, 1, 1.5f))
    }

    @Test fun `con llenar es la proporcion de pantalla entre la de la pagina`() {
        assertEquals(2.2f / 1.5f, escalaBase(true, 2.2f, 1, 1.5f), margen)
    }

    @Test fun `con llenar la escala se acota entre 1 y 3`() {
        assertEquals(3f, escalaBase(true, 10f, 1, 1f))
        assertEquals(1f, escalaBase(true, 1f, 1, 2f))
    }

    @Test fun `sin proporcion de pagina todavia la escala es 1`() {
        // La proporcion llega cuando la pagina ya se ha decodificado; hasta
        // entonces es 0 y no puede dividir.
        assertEquals(1f, escalaBase(true, 2.2f, 1, 0f))
    }
}
