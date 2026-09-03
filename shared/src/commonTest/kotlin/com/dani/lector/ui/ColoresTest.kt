package com.dani.lector.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Las conversiones de color sustituyen a `android.graphics.Color`, que no existe
 * fuera de Android. Se prueban porque **si se tuercen no dan ningun error**: la
 * pantalla se tiñe de un color raro y nadie sabe por que.
 */
class ColoresTest {

    private fun cerca(a: Float, b: Float, m: Float = 0.01f) = abs(a - b) < m

    private fun mismoColor(a: Color, b: Color) =
        cerca(a.red, b.red) && cerca(a.green, b.green) && cerca(a.blue, b.blue)

    @Test fun `ida y vuelta de los colores puros`() {
        for (c in listOf(Color.Red, Color.Green, Color.Blue,
                         Color.Yellow, Color.Cyan, Color.Magenta, Color.White)) {
            val (h, s, v) = Colores.aHsv(c)
            assertTrue(mismoColor(c, Colores.desdeHsv(h, s, v)), "ida y vuelta de $c")
        }
    }

    // El rojo esta en el 0 y es donde el tono da la vuelta: si el calculo se
    // sale por negativo, sale magenta en vez de rojo.
    @Test fun `el rojo tiene tono cero`() {
        val (h, s, v) = Colores.aHsv(Color.Red)
        assertTrue(cerca(h, 0f), "tono del rojo: $h")
        assertTrue(cerca(s, 1f) && cerca(v, 1f))
    }

    @Test fun `el verde esta en 120 y el azul en 240`() {
        assertTrue(cerca(Colores.aHsv(Color.Green).first, 120f))
        assertTrue(cerca(Colores.aHsv(Color.Blue).first, 240f))
    }

    // Un gris no tiene tono. Es el caso que `oscurecer` mira para no
    // inventarle color a una portada en blanco y negro.
    @Test fun `los grises no tienen saturacion`() {
        for (g in listOf(Color.Black, Color.White, Color(0.5f, 0.5f, 0.5f))) {
            assertTrue(cerca(Colores.aHsv(g).second, 0f), "saturacion de $g")
        }
    }

    // ─────────────────────────── OSCURECER ───────────────────────────

    @Test fun `oscurecer baja el brillo y mantiene el tono`() {
        val base = Color(0.2f, 0.8f, 0.3f)          // un verde
        val (hBase, _, _) = Colores.aHsv(base)
        val (h, _, v) = Colores.aHsv(Colores.oscurecer(base, 0.20f))
        assertTrue(cerca(h, hBase, 2f), "el tono cambio: $hBase -> $h")
        assertTrue(cerca(v, 0.20f), "el brillo deberia ser 0.20 y es $v")
    }

    /**
     * LO QUE MOTIVA TODA ESTA FUNCION: dos colores distintos oscurecidos tienen
     * que seguir distinguiendose. Mezclar contra negro los volvia el mismo gris.
     */
    @Test fun `un verde y un rojo oscurecidos siguen siendo distintos`() {
        val verde = Colores.oscurecer(Color(0.2f, 0.8f, 0.3f), 0.20f)
        val rojo = Colores.oscurecer(Color(0.8f, 0.2f, 0.2f), 0.20f)
        assertTrue(!mismoColor(verde, rojo), "quedaron iguales: $verde y $rojo")
    }

    @Test fun `a un gris no se le inventa color`() {
        val gris = Colores.oscurecer(Color(0.5f, 0.5f, 0.5f), 0.20f)
        assertTrue(cerca(Colores.aHsv(gris).second, 0f), "le puso saturacion: $gris")
    }

    @Test fun `un brillo fuera de rango se recorta`() {
        assertTrue(cerca(Colores.aHsv(Colores.oscurecer(Color.Red, 5f)).third, 1f))
        assertTrue(cerca(Colores.aHsv(Colores.oscurecer(Color.Red, -1f)).third, 0f))
    }
}
